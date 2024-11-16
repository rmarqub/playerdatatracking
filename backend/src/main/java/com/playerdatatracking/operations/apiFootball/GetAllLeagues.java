package com.playerdatatracking.operations.apiFootball;


import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.MANUAL_TRACKED_PLAYER;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.entities.indexaldata.TipoTorneo;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.file.NotCreatedJsonFileResponse;
import com.playerdatatracking.exceptions.file.NotFilledJsonFileResponse;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

public class GetAllLeagues {

	
	private ApiFootballClient restClient;
	private GenericResponse response;
	String filePath = "src/main/resources/json/apiFotball/leagues/leagues.json";
	private PlayerDataClient pdClient;
	private KeysManagement keyMethods = new KeysManagement();
	private Environment env;

	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}

	public void setEnv(Environment env) {
		this.env = env;
	}
	
	public GenericResponse ejecutar (GenericRequest request) throws Exception {
		try {
			response = new GenericResponse();
			restClient = new ApiFootballClient();
			keyMethods.setEnv(env);
			keyMethods.setPdClient(pdClient);
			Keys apiKey = keyMethods.nextKey();
			if (apiKey==null)
				throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
			if (keyMethods.checkReadiness(apiKey)) {
				Keys unctyptedKey = keyMethods.uncryptKey(apiKey);
				restClient.getLeaguesInfo(unctyptedKey.getValor());
				keyMethods.useKey(apiKey);
			}
			else {
				throw new ApiKeyManagementException("error al intentar usar una key no disponible");
			}
	        File file = new File(filePath);

	        if (!file.exists())
	            throw new NotCreatedJsonFileResponse("error al crear un json de respuesta, el archivo no ha sido creado o no se ha guardado correctamente");
	        
	        if (file.length() == 0) 
	            throw new NotFilledJsonFileResponse("el archivo de respuesta creado esta vacio");

	        FileReader fileReader = new FileReader(file);
	            int ch;
	            if ((ch = fileReader.read()) == -1) {
	            	fileReader.close();
	            	throw new NotFilledJsonFileResponse("el archivo de respuesta creado esta vacio");
	            }
	            fileReader.close();
	            
	        if (request.getUpdate().equalsIgnoreCase("true"))
	        	updateLeagues();
	        		
	        response.setCODE(Constants.CODE_OK);
	        response.setDescription("OK");
	        return response;
	       
	    } catch (Exception e) {
	    	throw e;
		}
	}
	
	public void updateLeagues() throws PlayerDataDBException, PlayerInputException {
	    ObjectMapper objectMapper = new ObjectMapper();
	    try {
	    	pdClient.deleteAllTorneos();
	        File jsonFile = new File(filePath);
	        JsonNode root = objectMapper.readTree(jsonFile);
	        JsonNode responseNode = root.path("response");

	        if (responseNode.isArray()) {
	            for (JsonNode node : responseNode) {
	            	JsonNode league = node.path("league");
	                String id = league.path("id").asText();
	                String name = league.path("name").asText();
	                String type = league.path("type").asText();
	                
	                JsonNode country = node.path("country");
	                String countryName = country.path("name").asText();
	                
	                Torneo torneo = new Torneo();
	                torneo.setId(Long.parseLong(id));
	                torneo.setName(name);
	                torneo.setLastupdate(null);
	                torneo.setStudied(false);
	                torneo.setFbrefid(null);
	                torneo.setFbrefdata(false);
	                int idTipo = Methods.getTournamentType(name, countryName, type);
	                torneo.setTipoTorneo(idTipo);
	                try {
	                	Pais pais = pdClient.findCountry(countryName);
	                	torneo.setPais(pais.getId());
	                	pdClient.saveTorneo(torneo);
	                } catch (NullPointerException e) {
	                	throw new PlayerInputException("no country has been found when looking for: " + countryName + " in DB");
	                } catch (Exception e) {
	                	throw e;
	                }
	            }
	        }

	    } catch (IOException e) {
	        throw new PlayerDataDBException("Error while reading or processing the JSON file", e);
	    }
	}
}
