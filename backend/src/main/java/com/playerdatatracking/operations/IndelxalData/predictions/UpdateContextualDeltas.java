package com.playerdatatracking.operations.IndelxalData.predictions;

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
import com.playerdatatracking.responses.GenericResponse;

@Component
public class UpdateContextualDeltas {

    private static final double W_FORMA   = 0.12;
    private static final double W_NEEDS   = 0.10;
    private static final double W_DEF     = 0.07;
    private static final double W_OFF     = 0.07;
    private static final double W_FATIGUE = 0.06;
    private static final double W_SET     = 0.06;
    private static final double W_ATM     = 0.03;
    private static final double W_UNAVAIL = 0.10;

    private static final double DEFAULT_PLAYER_PCT = 65.0;

    private static final Set<String> FINISHED = Set.of("FT", "AET", "PEN", "AWD");

    private static final int    MIN_SAMPLES  = 10;
    private static final int    MAX_ITER     = 500;
    private static final double LEARN_RATE   = 0.005;
    private static final double L2_LAMBDA    = 0.1;

    @Autowired private FixtureContextualAnalysisRepository analysisRepository;
    @Autowired private FixtureRepository                   fixtureRepository;
    @Autowired private ContextualWeightConfigRepository    weightConfigRepository;

    private static class CalibResult {
        final boolean updated;
        final String  message;
        CalibResult(boolean updated, String message) { this.updated = updated; this.message = message; }
    }

    private static class TrainRow {
        final double[] deltas;
        final double   logH, logD, logA;
        final int      label;
        TrainRow(double[] deltas, double logH, double logD, double logA, int label) {
            this.deltas = deltas; this.logH = logH; this.logD = logD; this.logA = logA; this.label = label;
        }
    }

    public GenericResponse<String> ejecutar() {
        GenericResponse<String> response = new GenericResponse<>();

        try {
            List<FixtureContextualAnalysis> withSnapshot = analysisRepository.findAllWithBaseSnapshot();

            Map<Long, Fixture> fixtureMap = fixtureRepository
                    .findAllById(withSnapshot.stream()
                            .map(FixtureContextualAnalysis::getFixtureId)
                            .collect(Collectors.toList()))
                    .stream()
                    .filter(f -> FINISHED.contains(f.getStatusShort()))
                    .collect(Collectors.toMap(Fixture::getId, f -> f));

            CalibResult calib = calibrateInJava(withSnapshot, fixtureMap);

            response.setCODE(Constants.CODE_OK);
            response.setDescription(calib.message);
            response.setEntity(calib.message);
        } catch (Exception e) {
            response.setCODE(Constants.CODE_ERROR);
            response.setDescription("Error actualizando deltas contextuales: " + e.getMessage());
            response.setEntity("Error: " + e.getMessage());
        }

        return response;
    }

    private CalibResult calibrateInJava(List<FixtureContextualAnalysis> withSnapshot,
                                        Map<Long, Fixture> fixtureMap) {
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

        double[] w = new double[]{W_FORMA, W_NEEDS, W_DEF, W_OFF, W_FATIGUE, W_SET, W_ATM, W_UNAVAIL};

        for (int iter = 0; iter < MAX_ITER; iter++) {
            double[] grad = new double[8];

            for (TrainRow row : rows) {
                double delta = 0;
                for (int i = 0; i < 8; i++) delta += w[i] * row.deltas[i];

                double[] logits = new double[]{row.logH + delta, row.logD, row.logA - delta};
                double[] probs  = softmax(logits);

                double iH = row.label == 0 ? 1.0 : 0.0;
                double iA = row.label == 2 ? 1.0 : 0.0;
                double dLdDelta = (probs[0] - iH) - (probs[2] - iA);

                for (int i = 0; i < 8; i++) {
                    grad[i] += dLdDelta * row.deltas[i];
                }
            }

            for (int i = 0; i < 8; i++) {
                grad[i] = grad[i] / n + L2_LAMBDA * w[i];
                w[i] -= LEARN_RATE * grad[i];
            }
        }

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

    private double diff(Integer home, Integer away) {
        return (home != null ? home : 3) - (away != null ? away : 3);
    }

    private double clamp(Float p) {
        if (p == null) return 0.334;
        return Math.max(0.001, Math.min(0.999, p));
    }

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

    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
