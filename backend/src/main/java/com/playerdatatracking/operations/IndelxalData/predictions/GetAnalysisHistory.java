package com.playerdatatracking.operations.IndelxalData.predictions;

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

    private static final Set<String> FINISHED = Set.of("FT", "AET", "PEN", "AWD");

    @Autowired private FixtureContextualAnalysisRepository analysisRepository;
    @Autowired private FixtureRepository                   fixtureRepository;
    @Autowired private ContextualWeightConfigRepository    weightConfigRepository;
    @Autowired private ContextualBlend                     blend;

    // ── Main entry point ───────────────────────────────────────────────────────

    public GenericResponse<AnalysisHistoryData> ejecutar(Long userId) {
        GenericResponse<AnalysisHistoryData> response = new GenericResponse<>();

        // 1 ── Count total analyses for this user
        long totalAnalysed = userId != null ? analysisRepository.countByUserId(userId) : 0;

        // 2 ── Fetch analyses with base snapshot, keep only finished fixtures
        List<FixtureContextualAnalysis> withSnapshot = userId != null
                ? analysisRepository.findAllWithBaseSnapshotByUserId(userId)
                : java.util.Collections.emptyList();

        Map<Long, Fixture> fixtureMap = fixtureRepository
                .findAllById(withSnapshot.stream()
                        .map(FixtureContextualAnalysis::getFixtureId)
                        .collect(Collectors.toList()))
                .stream()
                .filter(f -> FINISHED.contains(f.getStatusShort()))
                .collect(Collectors.toMap(Fixture::getId, f -> f));

        // 3 ── Load current weights for this user
        Optional<ContextualWeightConfig> currentOpt = userId != null
                ? weightConfigRepository.findByUserId(userId)
                : java.util.Optional.empty();
        ContextualWeightConfig weightsConfig = currentOpt.orElseGet(blend::defaultWeights);
        ContextualWeightsSnapshot snap = toSnapshot(currentOpt, weightsConfig);

        // 4 ── Compute per-match metrics
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

            // Use stored adj snapshot (set at prediction time) to preserve historical accuracy.
            // Fall back to live computation with current weights for legacy analyses without stored adj.
            double[] adj;
            if (a.getAdjHomeWin() != null && a.getAdjDraw() != null && a.getAdjAwayWin() != null) {
                adj = new double[]{
                    Math.max(0.001, Math.min(0.999, a.getAdjHomeWin())),
                    Math.max(0.001, Math.min(0.999, a.getAdjDraw())),
                    Math.max(0.001, Math.min(0.999, a.getAdjAwayWin()))
                };
            } else {
                double[] computed = blend.computeAdj(a.getFixtureId(), a, weightsConfig);
                adj = computed != null ? computed : new double[]{bH, bD, bA};
            }

            // netDelta: HA impact with current weights (display metric, always fresh)
            double delta = blend.computeFullDeltas(a.getFixtureId(), a, weightsConfig)[0];

            String adjPred  = classify(adj[0], adj[1], adj[2]);
            String basePred = classify(bH, bD, bA);

            boolean isCorrect     = adjPred.equals(actual);
            boolean isBaseCorrect = basePred.equals(actual);
            double pActualBase = "home_win".equals(actual) ? bH : "draw".equals(actual) ? bD : bA;
            double pActualAdj  = "home_win".equals(actual) ? adj[0] : "draw".equals(actual) ? adj[1] : adj[2];
            boolean deltaChangedToCorrect = !basePred.equals(actual) && adjPred.equals(actual);
            boolean deltaHelpful = pActualAdj > pActualBase || deltaChangedToCorrect;
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
            String a2 = x.getMatchDate(), b2 = y.getMatchDate();
            if (a2 == null && b2 == null) return 0;
            if (a2 == null) return 1;
            if (b2 == null) return -1;
            return b2.compareTo(a2);
        });

        // 5 ── Assemble response
        AnalysisHistoryData data = new AnalysisHistoryData();
        data.setTotalAnalysed((int) totalAnalysed);
        data.setProcessedAnalyses(processed);
        data.setCorrectPredictions(correct);
        data.setIncorrectPredictions(incorrect);
        data.setAccuracyRate(round(accuracy));
        data.setAvgBrierScore(round(avgBrier));
        data.setAvgLogLoss(round(avgLogLoss));
        data.setCalibrationRun(false);
        data.setWeightsUpdated(false);
        data.setCalibrationMessage("Sin calibración ejecutada");
        data.setCurrentWeights(snap);
        data.setMatchResults(results);

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(data);
        return response;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ContextualWeightsSnapshot toSnapshot(Optional<ContextualWeightConfig> opt, ContextualWeightConfig w) {
        ContextualWeightsSnapshot s = new ContextualWeightsSnapshot();
        s.setWForma(nvl(w.getWForma(), 0.12));       s.setWNeeds(nvl(w.getWNeeds(), 0.10));
        s.setWDef(nvl(w.getWDef(), 0.07));           s.setWOff(nvl(w.getWOff(), 0.07));
        s.setWFatigue(nvl(w.getWFatigue(), 0.06));   s.setWSetPieces(nvl(w.getWSetPieces(), 0.06));
        s.setWAtm(nvl(w.getWAtm(), 0.03));           s.setWUnavail(nvl(w.getWUnavail(), 0.10));
        s.setWFormaD(nvl(w.getWFormaD(), 0.0));      s.setWNeedsD(nvl(w.getWNeedsD(), 0.0));
        s.setWDefD(nvl(w.getWDefD(), 0.0));          s.setWOffD(nvl(w.getWOffD(), 0.0));
        s.setWFatigueD(nvl(w.getWFatigueD(), 0.0));  s.setWSetPiecesD(nvl(w.getWSetPiecesD(), 0.0));
        s.setWAtmD(nvl(w.getWAtmD(), 0.0));          s.setWUnavailD(nvl(w.getWUnavailD(), 0.0));
        s.setFromDb(opt.isPresent());
        opt.ifPresent(cfg -> {
            s.setCalibrationDate(cfg.getCalibrationDate() != null ? cfg.getCalibrationDate().toString() : null);
            s.setNSamples(cfg.getNSamples());
            s.setNotes(cfg.getNotes());
        });
        return s;
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

    private double clamp(Float p) {
        if (p == null) return 0.334;
        return Math.max(0.001, Math.min(0.999, p));
    }

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

    private double nvl(Double v, double fallback) { return v != null ? v : fallback; }
    private double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
