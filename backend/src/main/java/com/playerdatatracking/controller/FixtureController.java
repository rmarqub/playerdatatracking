package com.playerdatatracking.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.entities.indexaldata.FixtureEvent;
import com.playerdatatracking.entities.indexaldata.FixtureTeamStats;
import com.playerdatatracking.entities.indexaldata.FixturePlayerStats;
import com.playerdatatracking.entities.indexaldata.FixtureLineup;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.operations.IndelxalData.GetFixtureById;
import com.playerdatatracking.operations.IndelxalData.GetFixtureDetailFromApi;
import com.playerdatatracking.operations.IndelxalData.GetFixtureEventsByFixtureId;
import com.playerdatatracking.operations.IndelxalData.GetFixtureTeamStatsByFixtureId;
import com.playerdatatracking.operations.IndelxalData.GetFixturePlayerStatsByFixtureId;
import com.playerdatatracking.operations.IndelxalData.GetFixtureLineupByFixtureId;
import com.playerdatatracking.operations.IndelxalData.GetLiveFixtures;
import com.playerdatatracking.operations.IndelxalData.GetLiveFixturesFromApi;
import com.playerdatatracking.operations.IndelxalData.GetStudiedLeagues;
import com.playerdatatracking.operations.IndelxalData.IngestFixtureEvents;
import com.playerdatatracking.operations.IndelxalData.IngestFixtureTeamStats;
import com.playerdatatracking.operations.IndelxalData.IngestFixturePlayerStats;
import com.playerdatatracking.operations.IndelxalData.IngestFixtureLineup;
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
    private IngestFixtureTeamStats operationIngestFixtureTeamStats;
    @Autowired
    private IngestFixturePlayerStats operationIngestFixturePlayerStats;
    @Autowired
    private IngestFixtureLineup operationIngestFixtureLineup;
    @Autowired
    private GetFixtureById operationGetFixtureById;
    @Autowired
    private GetFixtureEventsByFixtureId operationGetFixtureEventsByFixtureId;
    @Autowired
    private GetFixtureTeamStatsByFixtureId operationGetFixtureTeamStatsByFixtureId;
    @Autowired
    private GetFixturePlayerStatsByFixtureId operationGetFixturePlayerStatsByFixtureId;
    @Autowired
    private GetFixtureLineupByFixtureId operationGetFixtureLineupByFixtureId;

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

    @PostMapping("/ingestFixtureTeamStats")
    public GenericResponse<FixtureTeamStats> ingestFixtureTeamStats(@RequestBody GenericRequest request) {
        GenericResponse<FixtureTeamStats> response = new GenericResponse<>();
        try {
            response = operationIngestFixtureTeamStats.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/ingestFixturePlayerStats")
    public GenericResponse<FixturePlayerStats> ingestFixturePlayerStats(@RequestBody GenericRequest request) {
        GenericResponse<FixturePlayerStats> response = new GenericResponse<>();
        try {
            response = operationIngestFixturePlayerStats.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/ingestFixtureLineup")
    public GenericResponse<FixtureLineup> ingestFixtureLineup(@RequestBody GenericRequest request) {
        GenericResponse<FixtureLineup> response = new GenericResponse<>();
        try {
            response = operationIngestFixtureLineup.ejecutar(request);
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

    @PostMapping("/fixtureTeamStats")
    public GenericResponse<FixtureTeamStats> getFixtureTeamStats(@RequestBody GenericRequest request) {
        GenericResponse<FixtureTeamStats> response = new GenericResponse<>();
        try {
            response = operationGetFixtureTeamStatsByFixtureId.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/fixturePlayerStats")
    public GenericResponse<FixturePlayerStats> getFixturePlayerStats(@RequestBody GenericRequest request) {
        GenericResponse<FixturePlayerStats> response = new GenericResponse<>();
        try {
            response = operationGetFixturePlayerStatsByFixtureId.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/fixtureLineup")
    public GenericResponse<FixtureLineup> getFixtureLineup(@RequestBody GenericRequest request) {
        GenericResponse<FixtureLineup> response = new GenericResponse<>();
        try {
            response = operationGetFixtureLineupByFixtureId.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
}
