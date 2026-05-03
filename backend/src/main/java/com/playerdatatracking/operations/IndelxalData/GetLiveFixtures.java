package com.playerdatatracking.operations.IndelxalData;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetLiveFixtures {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<Fixture> ejecutar() throws Exception {
        GenericResponse<Fixture> response = new GenericResponse<>();
        List<Fixture> fixtures = pdClient.getLiveFixtures();
        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(fixtures);
        return response;
    }
}
