package com.playerdatatracking.operations.IndelxalData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetFixtureById {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<Fixture> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<Fixture> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        Fixture fixture = pdClient.getFixtureById(request.getId());
        if (fixture == null)
            throw new PlayerInputException("No se encontró el partido con id=" + request.getId());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(fixture);
        return response;
    }
}
