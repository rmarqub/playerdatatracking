package com.playerdatatracking.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.manualdata.AddPlayer;
import com.playerdatatracking.operations.manualdata.DeletePlayer;
import com.playerdatatracking.operations.manualdata.GetAllPlayers;
import com.playerdatatracking.operations.manualdata.GetPlayer;
import com.playerdatatracking.operations.manualdata.UpdateManualPlayer;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ManualPlayerController extends BaseController {

    @Autowired
    private AddPlayer operationAddPlayer;
    @Autowired
    private GetAllPlayers operationGetAllPlayers;
    @Autowired
    private DeletePlayer operationDeletePlayer;
    @Autowired
    private GetPlayer operationGetPlayer;
    @Autowired
    private UpdateManualPlayer operationUpdateManualPlayer;

    @PostMapping("/player")
    public GenericResponse addPlayer(@RequestBody GenericRequest p, HttpServletRequest request) {
        GenericResponse response = new GenericResponse();
        Long userId = currentUserId(request);
        try {
            ManualTrackedPlayer player = Methods.bindRequestAsPlayer(p);
            response = operationAddPlayer.createForUser(player, userId);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @DeleteMapping("/player")
    public GenericResponse deletePlayer(@RequestBody GenericRequest request) {
        GenericResponse response = new GenericResponse();
        try {
            response = operationDeletePlayer.ejecutar(request.getNombre());
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/player/{id}")
    public GenericResponse<ManualTrackedPlayer> getPlayer(@PathVariable("id") Long id) {
        GenericResponse<ManualTrackedPlayer> response = new GenericResponse<>();
        try {
            response = operationGetPlayer.ejecutar(id);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/players")
    public GenericResponse<ManualTrackedPlayer> listMine(HttpServletRequest request) throws PlayerDataDBException {
        GenericResponse<ManualTrackedPlayer> response = new GenericResponse<>();
        Long userId = currentUserId(request);
        try {
            response = operationGetAllPlayers.ejecutar(userId);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PutMapping("/manualTrackedPlayer/{id}")
    public GenericResponse<ManualTrackedPlayer> updateManualPlayer(
            @PathVariable("id") Long id,
            @RequestBody GenericRequest request,
            HttpServletRequest servlet) {
        GenericResponse<ManualTrackedPlayer> response = new GenericResponse<>();
        Long userId = currentUserId(servlet);
        try {
            response = operationUpdateManualPlayer.ejecutar(request, id, userId);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
}
