package com.playerdatatracking.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.operations.IndelxalData.indexal.GetAllCountries;
import com.playerdatatracking.operations.IndelxalData.indexal.GetAllLeagues;
import com.playerdatatracking.operations.IndelxalData.indexal.GetAllTorneos;
import com.playerdatatracking.operations.IndelxalData.indexal.GetLeagueTiers;
import com.playerdatatracking.operations.IndelxalData.indexal.UpdateLeagueTiers;
import com.playerdatatracking.operations.IndelxalData.indexal.IngestRawData;
import com.playerdatatracking.operations.IndelxalData.indexal.TransferCheckOfPlayers;
import com.playerdatatracking.operations.IndelxalData.indexal.TransformRawToStats;
import com.playerdatatracking.operations.IndelxalData.indexal.UpdateClubsData;
import com.playerdatatracking.operations.IndelxalData.indexal.UpdatePlayersBySquads;
import com.playerdatatracking.operations.IndelxalData.indexal.UpdateStudiedLeagues;
import com.playerdatatracking.operations.IndelxalData.player.UpdatePlayersData;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.LeagueTierInfo;
import com.playerdatatracking.responses.TorneoInfo;

@RestController
public class DataSyncController {

    @Autowired
    private GetAllLeagues operationGetAllLeagues;
    @Autowired
    private GetAllCountries operationGetCountries;
    @Autowired
    private GetAllTorneos operationGetAllTorneos;
    @Autowired
    private UpdateStudiedLeagues operationUpdateStudiedLeagues;
    @Autowired
    private GetLeagueTiers operationGetLeagueTiers;
    @Autowired
    private UpdateLeagueTiers operationUpdateLeagueTiers;
    @Autowired
    private UpdateClubsData operationUpdateClubsData;
    @Autowired
    private UpdatePlayersData operationUpdatePlayersData;
    @Autowired
    private IngestRawData operationIngestRawData;
    @Autowired
    private TransformRawToStats operationTransformRawToStats;
    @Autowired
    private TransferCheckOfPlayers operationTCP;
    @Autowired
    private UpdatePlayersBySquads operationUPS;

    @PostMapping("/leagues")
    public GenericResponse getAllLeagues(@RequestBody GenericRequest request) {
        GenericResponse response = new GenericResponse();
        try {
            response = operationGetAllLeagues.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/countries")
    public GenericResponse getAllCountries(@RequestBody GenericRequest request) {
        GenericResponse response = new GenericResponse();
        try {
            response = operationGetCountries.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/updateLeagues")
    public GenericResponse updateLeagues() {
        GenericResponse response = new GenericResponse();
        try {
            operationGetAllLeagues.updateLeagues();
            response.setCODE(Constants.CODE_OK);
            response.setDescription("OK");
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/updateCountries")
    public GenericResponse updateCountries() {
        GenericResponse response = new GenericResponse();
        try {
            operationGetCountries.updateCountries();
            response.setCODE(Constants.CODE_OK);
            response.setDescription("OK");
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/updateClubsInfo")
    public GenericResponse<Club> updateClubsData(@RequestBody GenericRequest request) {
        GenericResponse<Club> response = new GenericResponse<>();
        try {
            response = operationUpdateClubsData.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/updatePlayers")
    public GenericResponse<Player> updatePlayersData(@RequestBody GenericRequest request) {
        GenericResponse<Player> response = new GenericResponse<>();
        try {
            response = operationUpdatePlayersData.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/ingestRawData")
    public GenericResponse ingestRawData(@RequestBody GenericRequest request) {
        GenericResponse response = new GenericResponse<>();
        try {
            operationIngestRawData.ejecutar(request);
            response.setCODE(Constants.CODE_OK);
            response.setDescription("OK");
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/transformRawToStats")
    public GenericResponse transformRawToStats(@RequestBody GenericRequest request) {
        GenericResponse response = new GenericResponse<>();
        try {
            operationTransformRawToStats.ejecutar(request);
            response.setCODE(Constants.CODE_OK);
            response.setDescription("OK");
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/updateTransferedPlayers")
    public GenericResponse updateTransferedPlayers(@RequestBody GenericRequest request) {
        GenericResponse response = new GenericResponse();
        try {
            response = operationTCP.ejecutar();
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/updatePlayerBySquads")
    public GenericResponse updatePlayerBySquads() {
        GenericResponse response = new GenericResponse();
        try {
            response = operationUPS.ejecutar();
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/allLeagues")
    public GenericResponse<TorneoInfo> getAllLeagues() {
        GenericResponse<TorneoInfo> response = new GenericResponse<>();
        try {
            response = operationGetAllTorneos.ejecutar();
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/updateStudiedLeagues")
    public GenericResponse<String> updateStudiedLeagues(@RequestBody GenericRequest request) {
        GenericResponse<String> response = new GenericResponse<>();
        try {
            response = operationUpdateStudiedLeagues.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/leagueTiers")
    public GenericResponse<LeagueTierInfo> getLeagueTiers() {
        GenericResponse<LeagueTierInfo> response = new GenericResponse<>();
        try {
            response = operationGetLeagueTiers.ejecutar();
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/updateLeagueTiers")
    public GenericResponse<String> updateLeagueTiers(@RequestBody GenericRequest request) {
        GenericResponse<String> response = new GenericResponse<>();
        try {
            response = operationUpdateLeagueTiers.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
}
