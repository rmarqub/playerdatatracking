package com.playerdatatracking.operations.IndelxalData;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.FixtureTeamStats;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetFixtureTeamStatsByFixtureId {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<FixtureTeamStats> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<FixtureTeamStats> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        List<FixtureTeamStats> stats = pdClient.getFixtureTeamStatsByFixtureId(request.getId());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(stats);
        return response;
    }
}
