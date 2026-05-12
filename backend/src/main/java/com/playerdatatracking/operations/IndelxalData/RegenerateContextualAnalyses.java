package com.playerdatatracking.operations.IndelxalData;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PredictApiClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.FixtureContextualAnalysis;
import com.playerdatatracking.repositories.indexaldata.FixtureContextualAnalysisRepository;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.MatchPrediction;

@Component
public class RegenerateContextualAnalyses {

    @Autowired
    private FixtureContextualAnalysisRepository analysisRepository;

    @Autowired
    private PredictApiClient predictApiClient;

    public GenericResponse<String> ejecutar() {
        List<FixtureContextualAnalysis> analyses = analysisRepository.findAllWithBaseSnapshotAndFixtureNotStarted();

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
        response.setDescription("Regeneradas " + updated + " predicciones base"
                + (failed > 0 ? " (" + failed + " errores)" : ""));
        return response;
    }
}
