package com.playerdatatracking.operations.IndelxalData.predictions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PredictApiClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.MatchPrediction;

@Component
public class GetMatchPrediction {

    @Autowired
    private PredictApiClient predictApiClient;

    public GenericResponse<MatchPrediction> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<MatchPrediction> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        MatchPrediction prediction = predictApiClient.predict(request.getId());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(prediction);
        return response;
    }
}
