package com.playerdatatracking.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.entities.indexaldata.FixtureEvent;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.operations.IndelxalData.GetFixtureById;
import com.playerdatatracking.operations.IndelxalData.GetFixtureDetailFromApi;
import com.playerdatatracking.operations.IndelxalData.GetFixtureEventsByFixtureId;
import com.playerdatatracking.operations.IndelxalData.GetLiveFixtures;
import com.playerdatatracking.operations.IndelxalData.GetLiveFixturesFromApi;
import com.playerdatatracking.operations.IndelxalData.GetStudiedLeagues;
import com.playerdatatracking.operations.IndelxalData.IngestFixtureEvents;
import com.playerdatatracking.operations.IndelxalData.IngestFixtures;
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
    @Autowired
    private GetLiveFixturesFromApi operationGetLiveFixturesFromApi;
    @Autowired
    private GetFixtureDetailFromApi operationGetFixtureDetailFromApi;
    @Autowired
    private IngestFixtures operationIngestFixtures;
    @Autowired
    private IngestFixtureEvents operationIngestFixtureEvents;
    @Autowired
    private GetFixtureById operationGetFixtureById;
    @Autowired
    private GetFixtureEventsByFixtureId operationGetFixtureEventsByFixtureId;

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

    @PostMapping("/liveFixturesApi")
    public GenericResponse<Object> getLiveFixturesFromApi() {
        GenericResponse<Object> response = new GenericResponse<>();
        try {
            response = operationGetLiveFixturesFromApi.ejecutar();
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/ingestFixtures")
    public GenericResponse<Fixture> ingestFixtures(@RequestBody GenericRequest request) {
        GenericResponse<Fixture> response = new GenericResponse<>();
        try {
            response = operationIngestFixtures.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/ingestFixtureEvents")
    public GenericResponse<FixtureEvent> ingestFixtureEvents(@RequestBody GenericRequest request) {
        GenericResponse<FixtureEvent> response = new GenericResponse<>();
        try {
            response = operationIngestFixtureEvents.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/fixtureById")
    public GenericResponse<Fixture> getFixtureById(@RequestBody GenericRequest request) {
        GenericResponse<Fixture> response = new GenericResponse<>();
        try {
            response = operationGetFixtureById.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/fixtureEvents")
    public GenericResponse<FixtureEvent> getFixtureEvents(@RequestBody GenericRequest request) {
        GenericResponse<FixtureEvent> response = new GenericResponse<>();
        try {
            response = operationGetFixtureEventsByFixtureId.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/fixtureDetailApi")
    public GenericResponse<Object> getFixtureDetailFromApi(@RequestBody GenericRequest request) {
        GenericResponse<Object> response = new GenericResponse<>();
        try {
            response = operationGetFixtureDetailFromApi.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
}
