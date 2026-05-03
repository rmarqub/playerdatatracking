package com.playerdatatracking.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.operations.IndelxalData.GetLiveFixtures;
import com.playerdatatracking.operations.IndelxalData.GetStudiedLeagues;
import com.playerdatatracking.operations.IndelxalData.SearchFixtures;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@RestController
public class FixtureController {

    @Autowired
    private GetLiveFixtures operationGetLiveFixtures;
    @Autowired
    private GetStudiedLeagues operationGetStudiedLeagues;
    @Autowired
    private SearchFixtures operationSearchFixtures;

    @PostMapping("/liveFixtures")
    public GenericResponse<Fixture> getLiveFixtures() {
        GenericResponse<Fixture> response = new GenericResponse<>();
        try {
            response = operationGetLiveFixtures.ejecutar();
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/studiedLeagues")
    public GenericResponse<Torneo> getStudiedLeagues() {
        GenericResponse<Torneo> response = new GenericResponse<>();
        try {
            response = operationGetStudiedLeagues.ejecutar();
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/searchFixtures")
    public GenericResponse<Fixture> searchFixtures(@RequestBody GenericRequest request) {
        GenericResponse<Fixture> response = new GenericResponse<>();
        try {
            response = operationSearchFixtures.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
}
