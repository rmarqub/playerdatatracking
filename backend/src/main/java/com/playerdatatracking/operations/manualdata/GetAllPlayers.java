package com.playerdatatracking.operations.manualdata;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetAllPlayers {

	@Autowired
	private PlayerDataClient pdClient;
	
	private GenericResponse<ManualTrackedPlayer> response = new GenericResponse();
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	public GenericResponse<ManualTrackedPlayer> ejecutar(Long userId) throws PlayerDataDBException {
		response = new GenericResponse<ManualTrackedPlayer>();
		List<ManualTrackedPlayer> playersList = pdClient.getPlayersByUserId(userId);
		response.setEntityList(playersList);
		response.setCODE(Constants.CODE_OK);
		if (playersList==null)
			response.setDescription("No Player Found");
		else
			response.setDescription("OK");
		return response;
		
		
	}
	
	
}
