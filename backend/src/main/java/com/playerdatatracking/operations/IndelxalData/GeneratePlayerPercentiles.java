package com.playerdatatracking.operations.IndelxalData;

import com.playerdatatracking.clients.PredictApiClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.responses.GenericResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GeneratePlayerPercentiles {

    @Autowired
    private PredictApiClient predictApiClient;

    public GenericResponse<String> ejecutar(String season) {
        GenericResponse<String> response = new GenericResponse<>();
        predictApiClient.computePlayerPercentiles(season);
        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity("Cálculo de percentiles iniciado en segundo plano" +
                (season != null ? " (season=" + season + ")" : ""));
        return response;
    }
}
