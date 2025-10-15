package com.playerdatatracking.operations.manualdata;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.operations.MalformedRequestException;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.repositories.indexaldata.MANUAL_TRACKED_PLAYERRepository;
import com.playerdatatracking.responses.GenericResponse;

public class AddPlayer{

	
	
	private PlayerDataClient pdClient;
	
	private GenericResponse response;
	


	
	public GenericResponse ejecutar (ManualTrackedPlayer player) throws MalformedRequestException, PlayerDataDBException, PlayerInputException {
		
		response = new GenericResponse();
		ManualTrackedPlayer dummy = pdClient.getPlayerbyName(player.getNombre());
		if (dummy!=null) {
			throw new PlayerInputException("Player already registered in MANUAL_TRACKED_PLAYER");
		}
		if ((Object)player.getAge()==null) {
		    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
		    LocalDate fechaHoy = LocalDate.now();
		    player.setAge(Period.between(player.getBirth(), fechaHoy).getYears());
		}
		boolean operationDone = pdClient.savePlayer(player);
		if (operationDone) {
			response.setCODE(Constants.CODE_OK);
			response.setDescription("OK");
			return response;
		}
		else
			throw new PlayerDataDBException("Error found while saving player in MANUAL_TRACKED_PLAYER table, check method in clients package");
	
	}


    public GenericResponse createForUser(ManualTrackedPlayer player, Long userId) throws PlayerDataDBException {
    	
    	response = new GenericResponse();

        if (player.getAge() == null && player.getBirth() != null) {
            LocalDate today = LocalDate.now();
            player.setAge(Period.between(player.getBirth(), today).getYears());
        }

        player.setUserId(userId);
        boolean operationDone = pdClient.savePlayer(player);
        
        if (operationDone) {
			response.setCODE(Constants.CODE_OK);
			response.setDescription("OK");
			return response;
		}else
			throw new PlayerDataDBException("Error found while saving player in MANUAL_TRACKED_PLAYER table, check method in clients package");
    }

	public PlayerDataClient getPdClient() {
		return pdClient;
	}

	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}

	
	
	
	
	
}
