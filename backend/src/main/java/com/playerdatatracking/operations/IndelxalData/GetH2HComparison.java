package com.playerdatatracking.operations.IndelxalData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.H2HComparisonData;

@Component
public class GetH2HComparison {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<H2HComparisonData> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<H2HComparisonData> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        H2HComparisonData comparison = pdClient.getH2HComparison(request.getId());
        if (comparison == null)
            throw new PlayerInputException("No se encontró el partido con id=" + request.getId());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(comparison);
        return response;
    }
}
