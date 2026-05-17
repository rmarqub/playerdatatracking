package com.playerdatatracking.operations.IndelxalData.predictions;

import java.util.Collections;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PredictApiClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.ContextualWeightConfig;
import com.playerdatatracking.entities.indexaldata.FixtureContextualAnalysis;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.repositories.indexaldata.ContextualWeightConfigRepository;
import com.playerdatatracking.repositories.indexaldata.FixtureContextualAnalysisRepository;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.ContextualMatchPrediction;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.MatchPrediction;

@Component
public class GetContextualMatchPrediction {

    @Autowired private PredictApiClient                         predictApiClient;
    @Autowired private ContextualWeightConfigRepository         weightConfigRepository;
    @Autowired private FixtureContextualAnalysisRepository      analysisRepository;
    @Autowired private ContextualBlend                          blend;

    public GenericResponse<ContextualMatchPrediction> ejecutar(GenericRequest request, Long userId) throws Exception {
        GenericResponse<ContextualMatchPrediction> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        // 1. Try Python for a fresh base prediction (non-blocking failure)
        MatchPrediction base = null;
        try {
            base = predictApiClient.predict(request.getId());
        } catch (Exception e) {
            System.err.println("[GetContextualMatchPrediction] Python predict failed, intentando con base almacenada: " + e.getMessage());
        }

        // 2. Read analysis from DB (always fresh, regardless of Python status)
        Optional<FixtureContextualAnalysis> optAnalysis = userId != null
                ? analysisRepository.findByFixtureIdAndUserId(request.getId(), userId)
                : Optional.empty();

        // 3. Resolve base probabilities: Python first, stored snapshot as fallback
        ContextualMatchPrediction result = new ContextualMatchPrediction();
        result.setFixtureId(request.getId());

        double bH, bD, bA;

        if (base != null) {
            result.setHomeTeam(base.getHomeTeam());
            result.setAwayTeam(base.getAwayTeam());
            result.setGoals(base.getGoals());
            result.setBtts(base.getBtts());
            result.setCorners(base.getCorners());
            result.setWarnings(base.getWarnings());
            MatchPrediction.Result1x2 r = base.getResult1x2();
            bH = r != null && r.getHomeWin() != null ? r.getHomeWin() : 0.334;
            bD = r != null && r.getDraw()    != null ? r.getDraw()    : 0.333;
            bA = r != null && r.getAwayWin() != null ? r.getAwayWin() : 0.333;
        } else {
            Float storedH = optAnalysis.map(FixtureContextualAnalysis::getBaseHomeWin).orElse(null);
            Float storedD = optAnalysis.map(FixtureContextualAnalysis::getBaseDraw).orElse(null);
            Float storedA = optAnalysis.map(FixtureContextualAnalysis::getBaseAwayWin).orElse(null);
            if (storedH == null || storedD == null || storedA == null)
                throw new RuntimeException("Servicio de predicción no disponible y no hay predicción base almacenada para este partido");
            bH = storedH;
            bD = storedD;
            bA = storedA;
            result.setWarnings(Collections.singletonList("Usando predicción base almacenada (servicio de predicción no disponible)"));
        }

        result.setBaseHomeWin(bH);
        result.setBaseDraw(bD);
        result.setBaseAwayWin(bA);

        // 4. Apply contextual delta
        if (optAnalysis.isEmpty()) {
            result.setAnalysisFound(false);
            result.setNetDelta(0.0);
            result.setAdjHomeWin(bH);
            result.setAdjDraw(bD);
            result.setAdjAwayWin(bA);
            result.setAdjPredicted(classify(bH, bD, bA));
            result.setAdjConfidence(confidence(bH, bD, bA));
        } else {
            FixtureContextualAnalysis a = optAnalysis.get();
            ContextualWeightConfig weights = resolveWeights(userId);

            double[] unavailImpacts = blend.computeUnavailableImpacts(
                    request.getId(),
                    a.getHomeUnavailablePlayers(),
                    a.getAwayUnavailablePlayers(),
                    weights);
            double deltaHA = blend.computeHaDelta(a, weights) + unavailImpacts[0];
            double deltaD  = blend.computeDrawDelta(a, weights) + unavailImpacts[1];
            double[] adj   = blend.applyBlend(bH, bD, bA, deltaHA, deltaD);

            result.setAnalysisFound(true);
            result.setNetDelta(deltaHA);
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ContextualWeightConfig resolveWeights(Long userId) {
        if (userId != null) {
            return weightConfigRepository.findByUserId(userId).orElseGet(blend::defaultWeights);
        }
        return blend.defaultWeights();
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
