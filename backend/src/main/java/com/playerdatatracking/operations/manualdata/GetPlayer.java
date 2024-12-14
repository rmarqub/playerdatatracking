package com.playerdatatracking.operations.manualdata;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.responses.GenericResponse;

public class GetPlayer {

	
	private PlayerDataClient pdClient;
	
	private GenericResponse<ManualTrackedPlayer> response = new GenericResponse();
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	public GenericResponse<ManualTrackedPlayer> ejecutar(Long id) throws PlayerDataDBException, PlayerInputException{
		ManualTrackedPlayer player = pdClient.getPlayer(id);
		if (player==null)
			throw new PlayerInputException("no player has been found with that name");
		response.setCODE(Constants.CODE_OK);
		response.setDescription("OK");
		response.setEntity(player);
		return response;
	}
}
