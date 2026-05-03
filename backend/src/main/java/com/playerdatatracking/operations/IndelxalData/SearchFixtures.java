package com.playerdatatracking.operations.IndelxalData;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class SearchFixtures {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<Fixture> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<Fixture> response = new GenericResponse<>();
        List<Fixture> fixtures;

        if (request.getNombre() != null && !request.getNombre().trim().isEmpty()) {
            fixtures = pdClient.searchFixturesByTeam(request.getNombre().trim());
        } else if (request.getId() != null) {
            fixtures = pdClient.searchFixturesByLeague(request.getId().intValue());
        } else {
            throw new PlayerInputException("Se requiere nombre de equipo o id de liga para buscar fixtures");
        }

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(fixtures);
        return response;
    }
}
