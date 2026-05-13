package com.playerdatatracking.operations.IndelxalData.indexal;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetStudiedLeagues {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<Torneo> ejecutar() throws Exception {
        GenericResponse<Torneo> response = new GenericResponse<>();
        List<Torneo> leagues = pdClient.getStudiedLeagues();
        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(leagues);
        return response;
    }
}
