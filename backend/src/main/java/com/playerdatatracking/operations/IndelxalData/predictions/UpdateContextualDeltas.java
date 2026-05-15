package com.playerdatatracking.operations.IndelxalData.predictions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // ── Default HA weights (home-away asymmetry) ─────────────────────────────
    private static final double W_FORMA   = 0.12;
    private static final double W_NEEDS   = 0.10;
    private static final double W_DEF     = 0.07;
    private static final double W_OFF     = 0.07;
    private static final double W_FATIGUE = 0.06;
    private static final double W_SET     = 0.06;
    private static final double W_ATM     = 0.03;
    private static final double W_UNAVAIL = 0.10;

    // ── Default Draw weights (draw-affinity axis) — start neutral ────────────
    private static final double WD_FORMA   = 0.0;
    private static final double WD_NEEDS   = 0.0;
    private static final double WD_DEF     = 0.0;
    private static final double WD_OFF     = 0.0;
    private static final double WD_FATIGUE = 0.0;
    private static final double WD_SET     = 0.0;
    private static final double WD_ATM     = 0.0;
    private static final double WD_UNAVAIL = 0.0;

    private static final double DEFAULT_PLAYER_PCT = 65.0;
    private static final Set<String> FINISHED = Set.of("FT", "AET", "PEN", "AWD");

    private static final int    MIN_SAMPLES   = 10;
    private static final int    MAX_ITER      = 600;
    private static final double LEARN_RATE    = 0.005;
    private static final double L2_HA         = 0.10;   // regularización eje HA
    private static final double L2_D          = 0.05;   // regularización eje Draw (menos restrictiva)

    @Autowired private FixtureContextualAnalysisRepository analysisRepository;
    @Autowired private FixtureRepository                   fixtureRepository;
    @Autowired private ContextualWeightConfigRepository    weightConfigRepository;

    // ── Data row for training ─────────────────────────────────────────────────

    private static class TrainRow {
        final double[] haFeats;    // home-away diff features [8]
        final double[] drawFeats;  // draw-affinity features [8]
        final double   logH, logD, logA;
        final int      label;      // 0=home_win, 1=draw, 2=away_win

        TrainRow(double[] haFeats, double[] drawFeats,
                 double logH, double logD, double logA, int label) {
            this.haFeats = haFeats; this.drawFeats = drawFeats;
            this.logH = logH; this.logD = logD; this.logA = logA;
            this.label = label;
        }
    }

    private static class CalibResult {
        final boolean updated;
        final String  message;
        CalibResult(boolean updated, String message) { this.updated = updated; this.message = message; }
    }

    // ── Public entry point ────────────────────────────────────────────────────

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

            CalibResult calib = calibrate(withSnapshot, fixtureMap);
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

    // ── Calibration: 2D gradient descent (HA + Draw axes) ────────────────────

    private CalibResult calibrate(List<FixtureContextualAnalysis> withSnapshot,
                                  Map<Long, Fixture> fixtureMap) {
        List<TrainRow> rows = new ArrayList<>();

        for (FixtureContextualAnalysis a : withSnapshot) {
            Fixture f = fixtureMap.get(a.getFixtureId());
            if (f == null) continue;
            if (f.getGoalsHome() == null || f.getGoalsAway() == null) continue;
            if (a.getBaseHomeWin() == null || a.getBaseDraw() == null || a.getBaseAwayWin() == null) continue;

            // Home-Away differential features (same as before)
            double[] haFeats = new double[]{
                diff(a.getHomeCurrentForm(),       a.getAwayCurrentForm()),
                diff(a.getHomeTeamNeeds(),         a.getAwayTeamNeeds()),
                diff(a.getHomeDefensiveBlock(),    a.getAwayDefensiveBlock()),
                diff(a.getHomeOffensiveRhythm(),   a.getAwayOffensiveRhythm()),
                diff(a.getAwayFatigue(),           a.getHomeFatigue()),
                diff(a.getHomeSetPieces(),         a.getAwaySetPieces()),
                diff(a.getHomeStadiumAtmosphere(), a.getAwayStadiumAtmosphere()),
                unavailHaSignal(a.getHomeUnavailablePlayers(), a.getAwayUnavailablePlayers())
            };

            // Draw-affinity features: equality signal (range −2 to +2)
            // Positive = ambos equipos iguales en este factor; negativo = muy dispares
            double[] drawFeats = new double[]{
                drawBalance(a.getHomeCurrentForm(),       a.getAwayCurrentForm()),
                drawBalance(a.getHomeTeamNeeds(),         a.getAwayTeamNeeds()),
                drawBalance(a.getHomeDefensiveBlock(),    a.getAwayDefensiveBlock()),
                drawBalance(a.getHomeOffensiveRhythm(),   a.getAwayOffensiveRhythm()),
                drawBalance(a.getHomeFatigue(),           a.getAwayFatigue()),
                drawBalance(a.getHomeSetPieces(),         a.getAwaySetPieces()),
                drawBalance(a.getHomeStadiumAtmosphere(), a.getAwayStadiumAtmosphere()),
                unavailDrawSignal(a.getHomeUnavailablePlayers(), a.getAwayUnavailablePlayers())
            };

            double bH = clamp(a.getBaseHomeWin());
            double bD = clamp(a.getBaseDraw());
            double bA = clamp(a.getBaseAwayWin());

            int gh = f.getGoalsHome(), ga = f.getGoalsAway();
            int label = gh > ga ? 0 : ga > gh ? 2 : 1;

            rows.add(new TrainRow(haFeats, drawFeats,
                    Math.log(bH), Math.log(bD), Math.log(bA), label));
        }

        int n = rows.size();
        if (n < MIN_SAMPLES) {
            return new CalibResult(false,
                "Calibración omitida: se necesitan al menos " + MIN_SAMPLES
                    + " partidos completados con snapshot (hay " + n + ").");
        }

        // ── Initialise weights ───────────────────────────────────────────────
        double[] wHA = {W_FORMA, W_NEEDS, W_DEF, W_OFF, W_FATIGUE, W_SET, W_ATM, W_UNAVAIL};
        double[] wD  = {WD_FORMA, WD_NEEDS, WD_DEF, WD_OFF, WD_FATIGUE, WD_SET, WD_ATM, WD_UNAVAIL};

        // Warm-start: inherit previous draw weights if they exist
        weightConfigRepository.findTopByOrderByIdDesc().ifPresent(prev -> {
            if (prev.getWFormaD() != null) {
                wD[0] = prev.getWFormaD();   wD[1] = prev.getWNeedsD();
                wD[2] = prev.getWDefD();     wD[3] = prev.getWOffD();
                wD[4] = prev.getWFatigueD(); wD[5] = prev.getWSetPiecesD();
                wD[6] = prev.getWAtmD();     wD[7] = prev.getWUnavailD();
            }
        });

        // ── Gradient descent ─────────────────────────────────────────────────
        for (int iter = 0; iter < MAX_ITER; iter++) {
            double[] gradHA = new double[8];
            double[] gradD  = new double[8];

            for (TrainRow row : rows) {
                double deltaHA = dot(wHA, row.haFeats);
                double deltaD  = dot(wD,  row.drawFeats);

                // logit(home) += deltaHA, logit(draw) += deltaD, logit(away) -= deltaHA
                double[] logits = {row.logH + deltaHA, row.logD + deltaD, row.logA - deltaHA};
                double[] probs  = softmax(logits);

                double iH = row.label == 0 ? 1.0 : 0.0;
                double iD = row.label == 1 ? 1.0 : 0.0;
                double iA = row.label == 2 ? 1.0 : 0.0;

                // ∂L/∂delta_HA = (p_H − y_H) − (p_A − y_A)
                double dHA = (probs[0] - iH) - (probs[2] - iA);
                // ∂L/∂delta_D  = (p_D − y_D)
                double dD  = (probs[1] - iD);

                for (int i = 0; i < 8; i++) {
                    gradHA[i] += dHA * row.haFeats[i];
                    gradD[i]  += dD  * row.drawFeats[i];
                }
            }

            for (int i = 0; i < 8; i++) {
                wHA[i] -= LEARN_RATE * (gradHA[i] / n + L2_HA * wHA[i]);
                wD[i]  -= LEARN_RATE * (gradD[i]  / n + L2_D  * wD[i]);
            }
        }

        // ── Persist ──────────────────────────────────────────────────────────
        ContextualWeightConfig cfg = new ContextualWeightConfig();

        cfg.setWForma(    round4(wHA[0])); cfg.setWNeeds(    round4(wHA[1]));
        // w_def y w_off deben ser >= 0: más bloque/ritmo local → ventaja local
        cfg.setWDef(      round4(Math.max(0.0, wHA[2]))); cfg.setWOff(round4(Math.max(0.0, wHA[3])));
        cfg.setWFatigue(  round4(wHA[4])); cfg.setWSetPieces(round4(wHA[5]));
        cfg.setWAtm(      round4(Math.max(0.0, wHA[6]))); cfg.setWUnavail(  round4(wHA[7]));

        // todos los _d deben ser >= 0: igualdad en el factor → más empate (drawBalance +2 cuando iguales)
        cfg.setWFormaD(    round4(Math.max(0.0, wD[0]))); cfg.setWNeedsD(    round4(Math.max(0.0, wD[1])));
        cfg.setWDefD(      round4(Math.max(0.0, wD[2]))); cfg.setWOffD(      round4(Math.max(0.0, wD[3])));
        cfg.setWFatigueD(  round4(Math.max(0.0, wD[4]))); cfg.setWSetPiecesD(round4(Math.max(0.0, wD[5])));
        cfg.setWAtmD(      round4(Math.max(0.0, wD[6]))); cfg.setWUnavailD(  round4(Math.max(0.0, wD[7])));

        cfg.setCalibrationDate(LocalDate.now());
        cfg.setNSamples(n);
        cfg.setNotes("Calibración 2D (HA+Draw) - " + LocalDate.now() + " - " + n + " muestras");
        weightConfigRepository.save(cfg);

        String[] haNames = {"w_forma","w_needs","w_def","w_off","w_fatigue","w_set","w_atm","w_unavail"};
        String[] dNames  = {"w_forma_d","w_needs_d","w_def_d","w_off_d","w_fatigue_d","w_set_d","w_atm_d","w_unavail_d"};
        StringBuilder sb = new StringBuilder("Calibración 2D completada (" + n + " muestras) — HA:");
        for (int i = 0; i < 8; i++) sb.append(" ").append(haNames[i]).append("=").append(round4(wHA[i]));
        sb.append(" | Draw:");
        for (int i = 0; i < 8; i++) sb.append(" ").append(dNames[i]).append("=").append(round4(wD[i]));
        return new CalibResult(true, sb.toString());
    }

    // ── Feature helpers ───────────────────────────────────────────────────────

    /** Home-away difference (positive = home advantage on this factor). */
    private double diff(Integer home, Integer away) {
        return nvl(home) - nvl(away);
    }

    /**
     * Draw-affinity feature: equality signal.
     * Returns 2 − |home − away|, range [−2, +2].
     * +2 = equipos idénticos en este factor; −2 = máxima diferencia posible (1 vs 5).
     * Con pesos >= 0, igualdad sube el empate; disparidad lo baja.
     */
    private double drawBalance(Integer home, Integer away) {
        return 2.0 - Math.abs(nvl(home) - nvl(away));
    }

    private double nvl(Integer v) { return v != null ? v : 3.0; }

    /** HA unavailable signal: positive when more away players absent. */
    private double unavailHaSignal(String homeCsv, String awayCsv) {
        int homeCount = countIds(homeCsv);
        int awayCount = countIds(awayCsv);
        if (homeCount == 0 && awayCount == 0) return 0.0;
        double unit = (DEFAULT_PLAYER_PCT - 50.0) / 100.0;
        return (awayCount - homeCount) * unit;
    }

    /** Draw unavailable signal: total absences (more = more uncertainty). */
    private double unavailDrawSignal(String homeCsv, String awayCsv) {
        int total = countIds(homeCsv) + countIds(awayCsv);
        if (total == 0) return 0.0;
        double unit = (DEFAULT_PLAYER_PCT - 50.0) / 100.0;
        return total * unit;
    }

    private int countIds(String csv) {
        if (csv == null || csv.isBlank()) return 0;
        int count = 0;
        for (String s : csv.split(",")) {
            try { Long.parseLong(s.trim()); count++; } catch (NumberFormatException ignored) {}
        }
        return count;
    }

    // ── Math helpers ──────────────────────────────────────────────────────────

    private double dot(double[] w, double[] x) {
        double s = 0;
        for (int i = 0; i < w.length; i++) s += w[i] * x[i];
        return s;
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

    private double clamp(Float p) {
        if (p == null) return 0.334;
        return Math.max(0.001, Math.min(0.999, p));
    }

    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
