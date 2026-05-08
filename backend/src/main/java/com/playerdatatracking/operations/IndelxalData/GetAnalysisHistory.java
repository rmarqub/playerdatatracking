package com.playerdatatracking.operations.IndelxalData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.ContextualWeightConfig;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.entities.indexaldata.FixtureContextualAnalysis;
import com.playerdatatracking.repositories.indexaldata.ContextualWeightConfigRepository;
import com.playerdatatracking.repositories.indexaldata.FixtureContextualAnalysisRepository;
import com.playerdatatracking.repositories.indexaldata.FixtureRepository;
import com.playerdatatracking.responses.AnalysisHistoryData;
import com.playerdatatracking.responses.AnalysisMatchResult;
import com.playerdatatracking.responses.ContextualWeightsSnapshot;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetAnalysisHistory {

    // Phase 7a hardcoded fallback weights
    private static final double W_FORMA   = 0.12;
    private static final double W_NEEDS   = 0.10;
    private static final double W_DEF     = 0.07;
    private static final double W_OFF     = 0.07;
    private static final double W_FATIGUE = 0.06;
    private static final double W_SET     = 0.06;
    private static final double W_ATM     = 0.03;
    private static final double W_UNAVAIL = 0.10;

    // Percentile assumed for players not in player_season_percentiles
    private static final double DEFAULT_PLAYER_PCT = 65.0;

    // Statuses that mean the match is fully over and has a result
    private static final Set<String> FINISHED = Set.of("FT", "AET", "PEN", "AWD");

    private static final int    MIN_SAMPLES  = 10;
    private static final int    MAX_ITER     = 500;
    private static final double LEARN_RATE   = 0.005;
    private static final double L2_LAMBDA    = 0.1;

    @Autowired private FixtureContextualAnalysisRepository analysisRepository;
    @Autowired private FixtureRepository                   fixtureRepository;
    @Autowired private ContextualWeightConfigRepository    weightConfigRepository;

    // ── Inner types ────────────────────────────────────────────────────────────

    private static class CalibResult {
        final boolean updated;
        final String  message;
        CalibResult(boolean updated, String message) { this.updated = updated; this.message = message; }
    }

    private static class TrainRow {
        final double[] deltas; // length 8: 7 contextual + 1 unavailable signal
        final double   logH, logD, logA;
        final int      label;  // 0=home_win 1=draw 2=away_win
        TrainRow(double[] deltas, double logH, double logD, double logA, int label) {
            this.deltas = deltas; this.logH = logH; this.logD = logD; this.logA = logA; this.label = label;
        }
    }

    // ── Main entry point ───────────────────────────────────────────────────────

    public GenericResponse<AnalysisHistoryData> ejecutar() {
        GenericResponse<AnalysisHistoryData> response = new GenericResponse<>();

        // 1 ── Count total analyses in DB
        long totalAnalysed = analysisRepository.count();

        // 2 ── Fetch analyses with base snapshot and join with FT fixtures
        List<FixtureContextualAnalysis> withSnapshot = analysisRepository.findAllWithBaseSnapshot();

        Map<Long, Fixture> fixtureMap = fixtureRepository
                .findAllById(withSnapshot.stream()
                        .map(FixtureContextualAnalysis::getFixtureId)
                        .collect(Collectors.toList()))
                .stream()
                .filter(f -> FINISHED.contains(f.getStatusShort()))
                .collect(Collectors.toMap(Fixture::getId, f -> f));

        // 3 ── Run Java-native calibration
        CalibResult calib = calibrateInJava(withSnapshot, fixtureMap);

        // 4 ── Load weights AFTER calibration
        Optional<ContextualWeightConfig> afterOpt = weightConfigRepository.findTopByOrderByIdDesc();
        double[] weights = resolveWeights(afterOpt);
        ContextualWeightsSnapshot snap = toSnapshot(afterOpt, weights);

        // 5 ── Compute per-match metrics
        List<AnalysisMatchResult> results = new ArrayList<>();
        int correct = 0, incorrect = 0;
        double sumBrier = 0, sumLogLoss = 0;

        for (FixtureContextualAnalysis a : withSnapshot) {
            Fixture f = fixtureMap.get(a.getFixtureId());
            if (f == null) continue;
            if (f.getGoalsHome() == null || f.getGoalsAway() == null) continue;

            String actual = actualResult(f.getGoalsHome(), f.getGoalsAway());

            double bH = clamp(a.getBaseHomeWin());
            double bD = clamp(a.getBaseDraw());
            double bA = clamp(a.getBaseAwayWin());

            double delta    = computeDelta(a, weights);
            double[] adj    = applyBlend(bH, bD, bA, delta);
            String adjPred  = classify(adj[0], adj[1], adj[2]);
            String basePred = classify(bH, bD, bA);

            boolean isCorrect     = adjPred.equals(actual);
            boolean isBaseCorrect = basePred.equals(actual);
            double pActualBase = "home_win".equals(actual) ? bH : "draw".equals(actual) ? bD : bA;
            double pActualAdj  = "home_win".equals(actual) ? adj[0] : "draw".equals(actual) ? adj[1] : adj[2];
            boolean deltaHelpful = pActualAdj > pActualBase;
            if (isCorrect) correct++; else incorrect++;

            double brier   = brierScore(adj[0], adj[1], adj[2], actual);
            double logLoss = logLoss(adj[0], adj[1], adj[2], actual);
            sumBrier   += brier;
            sumLogLoss += logLoss;

            AnalysisMatchResult r = new AnalysisMatchResult();
            r.setFixtureId(f.getId());
            r.setHomeTeamName(f.getHomeTeamName());
            r.setAwayTeamName(f.getAwayTeamName());
            r.setMatchDate(f.getMatchDate() != null ? f.getMatchDate().toString() : null);
            r.setGoalsHome(f.getGoalsHome());
            r.setGoalsAway(f.getGoalsAway());
            r.setActualResult(actual);
            r.setBaseHomeWin(round(bH));
            r.setBaseDraw(round(bD));
            r.setBaseAwayWin(round(bA));
            r.setAdjHomeWin(round(adj[0]));
            r.setAdjDraw(round(adj[1]));
            r.setAdjAwayWin(round(adj[2]));
            r.setAdjPredicted(adjPred);
            r.setNetDelta(round(delta));
            r.setCorrect(isCorrect);
            r.setBaseCorrect(isBaseCorrect);
            r.setDeltaHelpful(deltaHelpful);
            r.setBrierScore(round(brier));
            r.setLogLoss(round(logLoss));
            results.add(r);
        }

        int processed = results.size();
        double avgBrier   = processed > 0 ? sumBrier   / processed : 0.0;
        double avgLogLoss = processed > 0 ? sumLogLoss / processed : 0.0;
        double accuracy   = processed > 0 ? (correct * 100.0) / processed : 0.0;

        // Sort by matchDate desc
        results.sort((x, y) -> {
            String a2 = x.getMatchDate();
            String b2 = y.getMatchDate();
            if (a2 == null && b2 == null) return 0;
            if (a2 == null) return 1;
            if (b2 == null) return -1;
            return b2.compareTo(a2);
        });

        // 6 ── Assemble response
        AnalysisHistoryData data = new AnalysisHistoryData();
        data.setTotalAnalysed((int) totalAnalysed);
        data.setProcessedAnalyses(processed);
        data.setCorrectPredictions(correct);
        data.setIncorrectPredictions(incorrect);
        data.setAccuracyRate(round(accuracy));
        data.setAvgBrierScore(round(avgBrier));
        data.setAvgLogLoss(round(avgLogLoss));
        data.setCalibrationRun(calib.updated || calib.message.startsWith("Calibración"));
        data.setWeightsUpdated(calib.updated);
        data.setCalibrationMessage(calib.message);
        data.setCurrentWeights(snap);
        data.setMatchResults(results);

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(data);
        return response;
    }

    // ── Java-native calibration ────────────────────────────────────────────────

    private CalibResult calibrateInJava(List<FixtureContextualAnalysis> withSnapshot,
                                        Map<Long, Fixture> fixtureMap) {
        // Build training rows from finished matches with base snapshot
        List<TrainRow> rows = new ArrayList<>();
        for (FixtureContextualAnalysis a : withSnapshot) {
            Fixture f = fixtureMap.get(a.getFixtureId());
            if (f == null) continue;
            if (f.getGoalsHome() == null || f.getGoalsAway() == null) continue;
            if (a.getBaseHomeWin() == null || a.getBaseDraw() == null || a.getBaseAwayWin() == null) continue;

            double[] deltas = new double[]{
                diff(a.getHomeCurrentForm(),       a.getAwayCurrentForm()),
                diff(a.getHomeTeamNeeds(),         a.getAwayTeamNeeds()),
                diff(a.getHomeDefensiveBlock(),    a.getAwayDefensiveBlock()),
                diff(a.getHomeOffensiveRhythm(),   a.getAwayOffensiveRhythm()),
                diff(a.getAwayFatigue(),           a.getHomeFatigue()),
                diff(a.getHomeSetPieces(),         a.getAwaySetPieces()),
                diff(a.getHomeStadiumAtmosphere(), a.getAwayStadiumAtmosphere()),
                unavailableSignal(a.getHomeUnavailablePlayers(), a.getAwayUnavailablePlayers())
            };

            double bH = clamp(a.getBaseHomeWin());
            double bD = clamp(a.getBaseDraw());
            double bA = clamp(a.getBaseAwayWin());

            int gh = f.getGoalsHome(), ga = f.getGoalsAway();
            int label = gh > ga ? 0 : ga > gh ? 2 : 1;

            rows.add(new TrainRow(deltas, Math.log(bH), Math.log(bD), Math.log(bA), label));
        }

        int n = rows.size();
        if (n < MIN_SAMPLES) {
            return new CalibResult(false,
                "Calibración omitida: se necesitan al menos " + MIN_SAMPLES + " partidos completados con snapshot (hay " + n + ").");
        }

        // Gradient descent — weights start at current fallback values
        double[] w = new double[]{W_FORMA, W_NEEDS, W_DEF, W_OFF, W_FATIGUE, W_SET, W_ATM, W_UNAVAIL};

        for (int iter = 0; iter < MAX_ITER; iter++) {
            double[] grad = new double[8];

            for (TrainRow row : rows) {
                // net delta = w · deltas
                double delta = 0;
                for (int i = 0; i < 8; i++) delta += w[i] * row.deltas[i];

                double[] logits = new double[]{row.logH + delta, row.logD, row.logA - delta};
                double[] probs  = softmax(logits);

                // dL/d_delta = (p_H - I_H) - (p_A - I_A)
                double iH = row.label == 0 ? 1.0 : 0.0;
                double iA = row.label == 2 ? 1.0 : 0.0;
                double dLdDelta = (probs[0] - iH) - (probs[2] - iA);

                for (int i = 0; i < 8; i++) {
                    grad[i] += dLdDelta * row.deltas[i];
                }
            }

            for (int i = 0; i < 8; i++) {
                grad[i] = grad[i] / n + L2_LAMBDA * w[i];  // L2 regularization
                w[i] -= LEARN_RATE * grad[i];
            }
        }

        // Save to DB
        String[] names = {"w_forma","w_needs","w_def","w_off","w_fatigue","w_set_pieces","w_atm","w_unavail"};
        ContextualWeightConfig cfg = new ContextualWeightConfig();
        cfg.setWForma(    round4(w[0]));
        cfg.setWNeeds(    round4(w[1]));
        cfg.setWDef(      round4(w[2]));
        cfg.setWOff(      round4(w[3]));
        cfg.setWFatigue(  round4(w[4]));
        cfg.setWSetPieces(round4(w[5]));
        cfg.setWAtm(      round4(w[6]));
        cfg.setWUnavail(  round4(w[7]));
        cfg.setCalibrationDate(LocalDate.now());
        cfg.setNSamples(n);
        cfg.setNotes("Calibración Java nativa - " + LocalDate.now() + " - " + n + " muestras");
        weightConfigRepository.save(cfg);

        StringBuilder sb = new StringBuilder("Calibración completada (" + n + " muestras):");
        for (int i = 0; i < 8; i++) sb.append(" ").append(names[i]).append("=").append(round4(w[i]));
        return new CalibResult(true, sb.toString());
    }

    private double[] softmax(double[] logits) {
        double maxL = logits[0];
        for (double v : logits) if (v > maxL) maxL = v;
        double sum = 0;
        double[] out = new double[logits.length];
        for (int i = 0; i < logits.length; i++) { out[i] = Math.exp(logits[i] - maxL); sum += out[i]; }
        for (int i = 0; i < out.length; i++) out[i] /= sum;
        return out;
    }

    // ── Weights ────────────────────────────────────────────────────────────────

    private double[] resolveWeights(Optional<ContextualWeightConfig> opt) {
        return opt.map(w -> new double[]{
            nvl(w.getWForma(),    W_FORMA),
            nvl(w.getWNeeds(),    W_NEEDS),
            nvl(w.getWDef(),      W_DEF),
            nvl(w.getWOff(),      W_OFF),
            nvl(w.getWFatigue(),  W_FATIGUE),
            nvl(w.getWSetPieces(),W_SET),
            nvl(w.getWAtm(),      W_ATM),
            nvl(w.getWUnavail(),  W_UNAVAIL)
        }).orElse(new double[]{W_FORMA, W_NEEDS, W_DEF, W_OFF, W_FATIGUE, W_SET, W_ATM, W_UNAVAIL});
    }

    private ContextualWeightsSnapshot toSnapshot(Optional<ContextualWeightConfig> opt, double[] w) {
        ContextualWeightsSnapshot s = new ContextualWeightsSnapshot();
        s.setWForma(w[0]);    s.setWNeeds(w[1]);    s.setWDef(w[2]);
        s.setWOff(w[3]);      s.setWFatigue(w[4]);  s.setWSetPieces(w[5]);
        s.setWAtm(w[6]);      s.setWUnavail(w[7]);
        s.setFromDb(opt.isPresent());
        opt.ifPresent(cfg -> {
            s.setCalibrationDate(cfg.getCalibrationDate() != null ? cfg.getCalibrationDate().toString() : null);
            s.setNSamples(cfg.getNSamples());
            s.setNotes(cfg.getNotes());
        });
        return s;
    }

    // ── Blend logic (mirrors GetContextualMatchPrediction) ─────────────────────

    private double computeDelta(FixtureContextualAnalysis a, double[] w) {
        return w[0] * diff(a.getHomeCurrentForm(),       a.getAwayCurrentForm())
             + w[1] * diff(a.getHomeTeamNeeds(),         a.getAwayTeamNeeds())
             + w[2] * diff(a.getHomeDefensiveBlock(),    a.getAwayDefensiveBlock())
             + w[3] * diff(a.getHomeOffensiveRhythm(),   a.getAwayOffensiveRhythm())
             + w[4] * diff(a.getAwayFatigue(),           a.getHomeFatigue())
             + w[5] * diff(a.getHomeSetPieces(),         a.getAwaySetPieces())
             + w[6] * diff(a.getHomeStadiumAtmosphere(), a.getAwayStadiumAtmosphere())
             + w[7] * unavailableSignal(a.getHomeUnavailablePlayers(), a.getAwayUnavailablePlayers());
    }

    private double diff(Integer home, Integer away) {
        return (home != null ? home : 3) - (away != null ? away : 3);
    }

    private double clamp(Float p) {
        if (p == null) return 0.334;
        return Math.max(0.001, Math.min(0.999, p));
    }

    private double[] applyBlend(double bH, double bD, double bA, double delta) {
        double logH = Math.log(bH) + delta;
        double logD = Math.log(bD);
        double logA = Math.log(bA) - delta;
        double maxLog = Math.max(logH, Math.max(logD, logA));
        double sum = Math.exp(logH - maxLog) + Math.exp(logD - maxLog) + Math.exp(logA - maxLog);
        return new double[]{
            Math.exp(logH - maxLog) / sum,
            Math.exp(logD - maxLog) / sum,
            Math.exp(logA - maxLog) / sum
        };
    }

    private String classify(double h, double d, double a) {
        if (h >= d && h >= a) return "home_win";
        if (a >= h && a >= d) return "away_win";
        return "draw";
    }

    private String actualResult(int gh, int ga) {
        if (gh > ga) return "home_win";
        if (ga > gh) return "away_win";
        return "draw";
    }

    // ── Metrics ────────────────────────────────────────────────────────────────

    private double brierScore(double pH, double pD, double pA, String actual) {
        int iH = "home_win".equals(actual) ? 1 : 0;
        int iD = "draw".equals(actual)     ? 1 : 0;
        int iA = "away_win".equals(actual) ? 1 : 0;
        return (Math.pow(pH - iH, 2) + Math.pow(pD - iD, 2) + Math.pow(pA - iA, 2)) / 3.0;
    }

    private double logLoss(double pH, double pD, double pA, String actual) {
        double eps = 1e-9;
        if ("home_win".equals(actual)) return -Math.log(Math.max(pH, eps));
        if ("draw".equals(actual))     return -Math.log(Math.max(pD, eps));
        return                                -Math.log(Math.max(pA, eps));
    }

    // ── Unavailable signal (mirrors GetContextualMatchPrediction) ──────────────

    // Signal = Σ(pct_away - 50)/100  −  Σ(pct_home - 50)/100
    // Uses DEFAULT_PLAYER_PCT (65) for all players; no DB lookup needed at calibration time.
    private double unavailableSignal(String homeCsv, String awayCsv) {
        int homeCount = parseCsvIds(homeCsv).size();
        int awayCount = parseCsvIds(awayCsv).size();
        if (homeCount == 0 && awayCount == 0) return 0.0;
        double unitImpact = (DEFAULT_PLAYER_PCT - 50.0) / 100.0;
        return (awayCount - homeCount) * unitImpact;
    }

    private List<Long> parseCsvIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String s : csv.split(",")) {
            try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private double nvl(Double v, double fallback) { return v != null ? v : fallback; }
    private double round(double v)  { return Math.round(v * 10000.0) / 10000.0; }
    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
