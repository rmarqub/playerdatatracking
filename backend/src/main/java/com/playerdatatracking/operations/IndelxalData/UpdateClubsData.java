package com.playerdatatracking.operations.IndelxalData;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.responses.GenericResponse;

public class UpdateClubsData {

	
	private PlayerDataClient pdClient;
	
	private GenericResponse<Club> response = new GenericResponse();
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	public GenericResponse<Club> ejecutar() throws Exception {
		return response;
	}
}
