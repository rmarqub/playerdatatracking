package com.playerdatatracking.operations.IndelxalData.fixture;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.FixtureLineup;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetFixtureLineupByFixtureId {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<FixtureLineup> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<FixtureLineup> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        List<FixtureLineup> lineup = pdClient.getFixtureLineupByFixtureId(request.getId());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(lineup);
        return response;
    }
}
