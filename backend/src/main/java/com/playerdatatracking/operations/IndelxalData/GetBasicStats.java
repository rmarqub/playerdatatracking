package com.playerdatatracking.operations.IndelxalData;

import java.util.List;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.requests.PlayerMatchRow;
import com.playerdatatracking.responses.GenericResponse;

public class GetBasicStats {
	
	private PlayerDataClient pdClient;
	private GenericResponse<PlayerMatchRow> response;
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	public GenericResponse<PlayerMatchRow> ejecutar (Long indexId){
		response = new GenericResponse<PlayerMatchRow>();
		try {
			List<PlayerMatchRow> l = pdClient.getStaticsByIndexId(indexId);
			response.setEntityList(l);
		} catch (Exception e) {
			throw e;
		}
		return response;
	}

}
