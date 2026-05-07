package com.playerdatatracking.operations.IndelxalData;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.H2HFixtureSummary;

@Component
public class GetH2HFixtures {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<H2HFixtureSummary> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<H2HFixtureSummary> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        List<H2HFixtureSummary> fixtures = pdClient.getH2HFixtures(request.getId());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(fixtures);
        return response;
    }
}
