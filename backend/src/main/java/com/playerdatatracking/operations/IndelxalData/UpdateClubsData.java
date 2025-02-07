package com.playerdatatracking.operations.IndelxalData;

import java.util.HashMap;
import java.util.List;

import org.springframework.core.env.Environment;

import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
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
		try {
			if(pdClient.deleteAllClubs()) {
				String actualSeason = pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();
				List<Torneo> studiedLeagues = pdClient.getStudiedLeagues();
				if (studiedLeagues==null)
					throw new PlayerDataDBException("Error al buscar ligas para actualizar los datos de clubes");
				if (studiedLeagues.size()==0) {
					response.setCODE(Constants.CODE_OK);
					response.setDescription("OK");
					return response;
				} else {
					Keys apiKey = keyMethods.nextKey();
					apiKey = keyMethods.uncryptKey(apiKey);
					if (apiKey==null)
						throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
					for (Torneo league : studiedLeagues) {
						HashMap<String, String> queryParams = new HashMap<>();
						queryParams.put("season", actualSeason);
						queryParams.put("league", league.getId().toString());
						if (keyMethods.checkReadiness(apiKey)) {
							restClient.getClubs(queryParams, apiKey.getValor(), league.getName());
							keyMethods.useKey(apiKey);
						}
						else {
							keyMethods.storeUsedKey(apiKey);
							apiKey = keyMethods.nextKey();
							if (keyMethods.checkReadiness(apiKey)) {
								restClient.getClubs(queryParams, apiKey.getValor(), league.getName());
								keyMethods.useKey(apiKey);
							}
							else
								throw new ApiKeyManagementException("error al intentar usar una key no disponible");
						}
					}
					keyMethods.storeUsedKey(apiKey);
					response.setCODE(Constants.CODE_OK);
					response.setDescription("OK");
					return response;
				}
			}
			else {
				throw new PlayerDataDBException("Error al intentar borrar la informacion de los clubes previo a la actualizacion");
			}
		} catch(Exception e) {
			throw e;
		}
	}
}
