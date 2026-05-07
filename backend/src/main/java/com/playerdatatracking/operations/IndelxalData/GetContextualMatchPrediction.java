package com.playerdatatracking.operations.IndelxalData;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PredictApiClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.FixtureContextualAnalysis;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.repositories.indexaldata.FixtureContextualAnalysisRepository;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.ContextualMatchPrediction;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.MatchPrediction;

@Component
public class GetContextualMatchPrediction {

    // ---- Phase 7a hardcoded weights (logit-space per 1-point deviation) ------
    private static final double W_FORMA   = 0.12;
    private static final double W_NEEDS   = 0.10;
    private static final double W_DEF     = 0.07;
    private static final double W_OFF     = 0.07;
    private static final double W_FATIGUE = 0.06; // applied inverted: high fatigue hurts
    private static final double W_SET     = 0.06;
    private static final double W_ATM     = 0.03;

    @Autowired
    private PredictApiClient predictApiClient;

    @Autowired
    private FixtureContextualAnalysisRepository analysisRepository;

    public GenericResponse<ContextualMatchPrediction> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<ContextualMatchPrediction> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        MatchPrediction base = predictApiClient.predict(request.getId());

        ContextualMatchPrediction result = new ContextualMatchPrediction();
        result.setFixtureId(base.getFixtureId());
        result.setHomeTeam(base.getHomeTeam());
        result.setAwayTeam(base.getAwayTeam());
        result.setOverUnder25(base.getOverUnder25());
        result.setBtts(base.getBtts());
        result.setWarnings(base.getWarnings());

        MatchPrediction.Result1x2 r = base.getResult1x2();
        double bH = r != null && r.getHomeWin() != null ? r.getHomeWin() : 0.334;
        double bD = r != null && r.getDraw()    != null ? r.getDraw()    : 0.333;
        double bA = r != null && r.getAwayWin() != null ? r.getAwayWin() : 0.333;

        result.setBaseHomeWin(bH);
        result.setBaseDraw(bD);
        result.setBaseAwayWin(bA);

        Optional<FixtureContextualAnalysis> optAnalysis =
                analysisRepository.findByFixtureId(request.getId());

        if (optAnalysis.isEmpty()) {
            // No analysis: return base probs as adjusted
            result.setAnalysisFound(false);
            result.setNetDelta(0.0);
            result.setAdjHomeWin(bH);
            result.setAdjDraw(bD);
            result.setAdjAwayWin(bA);
            result.setAdjPredicted(classify(bH, bD, bA));
            result.setAdjConfidence(confidence(bH, bD, bA));
        } else {
            FixtureContextualAnalysis a = optAnalysis.get();
            double delta = computeDelta(a);

            double[] adj = applyBlend(bH, bD, bA, delta);
            result.setAnalysisFound(true);
            result.setNetDelta(delta);
            result.setAdjHomeWin(adj[0]);
            result.setAdjDraw(adj[1]);
            result.setAdjAwayWin(adj[2]);
            result.setAdjPredicted(classify(adj[0], adj[1], adj[2]));
            result.setAdjConfidence(confidence(adj[0], adj[1], adj[2]));
        }

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(result);
        return response;
    }

    // ---- Blend logic ---------------------------------------------------------

    private double computeDelta(FixtureContextualAnalysis a) {
        return W_FORMA   * diff(a.getHomeCurrentForm(),       a.getAwayCurrentForm())
             + W_NEEDS   * diff(a.getHomeTeamNeeds(),         a.getAwayTeamNeeds())
             + W_DEF     * diff(a.getHomeDefensiveBlock(),    a.getAwayDefensiveBlock())
             + W_OFF     * diff(a.getHomeOffensiveRhythm(),   a.getAwayOffensiveRhythm())
             + W_FATIGUE * diff(a.getAwayFatigue(),           a.getHomeFatigue())   // inverted
             + W_SET     * diff(a.getHomeSetPieces(),         a.getAwaySetPieces())
             + W_ATM     * diff(a.getHomeStadiumAtmosphere(), a.getAwayStadiumAtmosphere());
    }

    private double diff(Integer home, Integer away) {
        return (home != null ? home : 3) - (away != null ? away : 3);
    }

    private double[] applyBlend(double bH, double bD, double bA, double delta) {
        bH = Math.max(0.001, Math.min(0.999, bH));
        bD = Math.max(0.001, Math.min(0.999, bD));
        bA = Math.max(0.001, Math.min(0.999, bA));

        double logH = Math.log(bH) + delta;
        double logD = Math.log(bD);          // draw not shifted by team asymmetry
        double logA = Math.log(bA) - delta;

        double maxLog = Math.max(logH, Math.max(logD, logA));
        double sumExp = Math.exp(logH - maxLog) + Math.exp(logD - maxLog) + Math.exp(logA - maxLog);

        return new double[]{
            Math.exp(logH - maxLog) / sumExp,
            Math.exp(logD - maxLog) / sumExp,
            Math.exp(logA - maxLog) / sumExp
        };
    }

    private String classify(double h, double d, double a) {
        if (h >= d && h >= a) return "home_win";
        if (a >= h && a >= d) return "away_win";
        return "draw";
    }

    private double confidence(double h, double d, double a) {
        double[] s = {h, d, a};
        java.util.Arrays.sort(s);
        return s[2] - s[1];
    }
}
