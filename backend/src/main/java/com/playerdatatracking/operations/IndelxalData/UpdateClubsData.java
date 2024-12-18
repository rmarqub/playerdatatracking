package com.playerdatatracking.operations.IndelxalData;

import org.springframework.core.env.Environment;

import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.responses.GenericResponse;

public class UpdateClubsData {

	
	private PlayerDataClient pdClient;
	private ApiFootballClient restClient;
	private KeysManagement keyMethods = new KeysManagement();
	private Environment env;
	private GenericResponse<Club> response = new GenericResponse();
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	public void setEnv(Environment env) {
		this.env = env;
	}
	
	public GenericResponse<Club> ejecutar() throws Exception {
		
		restClient = new ApiFootballClient();
		keyMethods.setEnv(env);
		keyMethods.setPdClient(pdClient);
		Keys apiKey = keyMethods.nextKey();
		if (apiKey==null)
			throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
		if (keyMethods.checkReadiness(apiKey)) {
			Keys unctyptedKey = keyMethods.uncryptKey(apiKey);
			restClient.getCountriesInfo(unctyptedKey.getValor());
			keyMethods.useKey(apiKey);
		}
		else {
			throw new ApiKeyManagementException("error al intentar usar una key no disponible");
		}
		return response;
	}
}
