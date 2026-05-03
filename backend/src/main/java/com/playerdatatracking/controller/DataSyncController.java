package com.playerdatatracking.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.operations.IndelxalData.GetAllCountries;
import com.playerdatatracking.operations.IndelxalData.GetAllLeagues;
import com.playerdatatracking.operations.IndelxalData.IngestRawData;
import com.playerdatatracking.operations.IndelxalData.TransferCheckOfPlayers;
import com.playerdatatracking.operations.IndelxalData.UpdateClubsData;
import com.playerdatatracking.operations.IndelxalData.UpdatePlayersData;
import com.playerdatatracking.operations.IndelxalData.UpdatePlayersBySquads;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@RestController
public class DataSyncController {

    @Autowired
    private GetAllLeagues operationGetAllLeagues;
    @Autowired
    private GetAllCountries operationGetCountries;
    @Autowired
    private UpdateClubsData operationUpdateClubsData;
    @Autowired
    private UpdatePlayersData operationUpdatePlayersData;
    @Autowired
    private IngestRawData operationIngestRawData;
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
}
