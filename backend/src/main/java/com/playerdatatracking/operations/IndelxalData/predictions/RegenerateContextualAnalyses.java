package com.playerdatatracking.operations.IndelxalData.predictions;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PredictApiClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.ContextualWeightConfig;
import com.playerdatatracking.entities.indexaldata.FixtureContextualAnalysis;
import com.playerdatatracking.repositories.indexaldata.ContextualWeightConfigRepository;
import com.playerdatatracking.repositories.indexaldata.FixtureContextualAnalysisRepository;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.MatchPrediction;

@Component
public class RegenerateContextualAnalyses {

    @Autowired private FixtureContextualAnalysisRepository analysisRepository;
    @Autowired private ContextualWeightConfigRepository    weightConfigRepository;
    @Autowired private PredictApiClient                    predictApiClient;
    @Autowired private ContextualBlend                     blend;

    /**
     * @param scope "not_started" | "finished" | "all"  (null defaults to "not_started")
     */
    public GenericResponse<String> ejecutar(Long userId, String scope) {
        List<FixtureContextualAnalysis> analyses = selectAnalyses(userId, scope);

        ContextualWeightConfig weights = userId != null
                ? weightConfigRepository.findByUserId(userId).orElseGet(blend::defaultWeights)
                : blend.defaultWeights();

        int updated = 0;
        int failed  = 0;

        for (FixtureContextualAnalysis a : analyses) {
            try {
                MatchPrediction p = predictApiClient.predict(a.getFixtureId());
                MatchPrediction.Result1x2 r = p.getResult1x2();
                if (r != null) {
                    a.setBaseHomeWin(r.getHomeWin() != null ? r.getHomeWin().floatValue() : null);
                    a.setBaseDraw(r.getDraw()        != null ? r.getDraw().floatValue()    : null);
                    a.setBaseAwayWin(r.getAwayWin()  != null ? r.getAwayWin().floatValue() : null);

                    double[] adj = blend.computeAdj(a.getFixtureId(), a, weights);
                    if (adj != null) {
                        a.setAdjHomeWin((float) adj[0]);
                        a.setAdjDraw((float) adj[1]);
                        a.setAdjAwayWin((float) adj[2]);
                    }

                    analysisRepository.save(a);
                    updated++;
                }
            } catch (Exception e) {
                System.err.println("[RegenerateContextualAnalyses] Fixture " + a.getFixtureId() + ": " + e.getMessage());
                failed++;
            }
        }

        GenericResponse<String> response = new GenericResponse<>();
        response.setCODE(Constants.CODE_OK);
        response.setDescription("Regeneradas " + updated + " predicciones (" + scopeLabel(scope) + ")"
                + (failed > 0 ? " (" + failed + " errores)" : ""));
        return response;
    }

    private List<FixtureContextualAnalysis> selectAnalyses(Long userId, String scope) {
        if (userId == null) return Collections.emptyList();
        if ("finished".equals(scope)) {
            return analysisRepository.findAllWithBaseSnapshotAndFixtureFinishedByUserId(userId);
        }
        if ("all".equals(scope)) {
            return analysisRepository.findAllWithBaseSnapshotByUserId(userId);
        }
        return analysisRepository.findAllWithBaseSnapshotAndFixtureNotStartedByUserId(userId);
    }

    private String scopeLabel(String scope) {
        if ("finished".equals(scope)) return "finalizados";
        if ("all".equals(scope))      return "todos";
        return "no finalizados";
    }
}
