package com.playerdatatracking.operations.IndelxalData.player;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.ValuationApiClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.PlayerMarketValue;

@Component
public class GetPlayerMarketValue {

    @Autowired
    private ValuationApiClient valuationApiClient;

    public GenericResponse<PlayerMarketValue> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<PlayerMarketValue> response = new GenericResponse<>();

        if (request.getIndexId() == null)
            throw new PlayerInputException("Se requiere el indexId del jugador");

        PlayerMarketValue value = valuationApiClient.getPlayerValue(request.getIndexId());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(value);
        return response;
    }
}
