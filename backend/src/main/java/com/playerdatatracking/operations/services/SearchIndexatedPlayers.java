package com.playerdatatracking.operations.services;

import java.util.List;

import org.springframework.core.env.Environment;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.SearchPlayersRequest;
import com.playerdatatracking.responses.GenericResponse;

public class SearchIndexatedPlayers {

	private PlayerDataClient pdClient;
	private Environment env;
	private GenericResponse<Player> response;
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}

	public void setEnv(Environment env) {
		this.env = env;
	}
	
	public GenericResponse<Player> ejecutar (SearchPlayersRequest request) throws Exception {
		response = new GenericResponse<Player>();
		try {
			
			
			List<Player> rList = pdClient.searchPlayers(request.getPlayer(), request.getTeam());
			if (rList == null) {
				throw new PlayerInputException("no player or team has been found with that name");
			}
			response.setCODE(Constants.CODE_OK);
			response.setDescription("OK");
			response.setEntityList(rList);
			return response;
		} catch(Exception e) {
			throw e;
		}
		
	}
}
