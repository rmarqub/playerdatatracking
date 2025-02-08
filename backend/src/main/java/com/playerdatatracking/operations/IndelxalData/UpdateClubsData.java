package com.playerdatatracking.operations.IndelxalData;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.ClubInLeague;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.file.NotCreatedJsonFileResponse;
import com.playerdatatracking.exceptions.file.NotFilledJsonFileResponse;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

public class UpdateClubsData {

	
	private PlayerDataClient pdClient;
	private ApiFootballClient restClient;
	private KeysManagement keyMethods = new KeysManagement();
	private Environment env;
	String directoryPath = "src/main/resources/json/apiFotball/leagues/";
    String excludedFile = "leagues.json";
	private GenericResponse<Club> response = new GenericResponse();
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	public void setEnv(Environment env) {
		this.env = env;
	}
	
	public GenericResponse<Club> ejecutar(GenericRequest request) throws Exception {
		
		restClient = new ApiFootballClient();
		keyMethods.setEnv(env);
		keyMethods.setPdClient(pdClient);
		try {
			if(request.getRestUpdate()!=null && request.getRestUpdate().equals("true")) {
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
				}
			}
			if(request.getUpdate()!=null && request.getUpdate().equals("true")) {
				
				List<String> jsonFiles = new ArrayList<>();
		        try {
		            Files.walk(Paths.get(directoryPath))
		                    .filter(Files::isRegularFile)
		                    .filter(path -> path.toString().endsWith(".json"))
		                    .filter(path -> !path.getFileName().toString().equals(excludedFile))
		                    .forEach(path -> jsonFiles.add(path.toString()));
		        } catch (IOException e) {
		        	e.printStackTrace();
		        }
		        try {
	            	pdClient.deleteAllCILs();
			        pdClient.deleteAllClubs();
		        } catch(Exception e) {
		        	throw new PlayerDataDBException("No se ha completado correctamente el borrado por lo que no se pueden actualizar la informacion sobre clubes. Error: " + e.getMessage());
		        }
		        for (String path : jsonFiles) {

		            try {
			            File file = new File(path);
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
				        
				        ObjectMapper objectMapper = new ObjectMapper();
				        JsonNode root = objectMapper.readTree(file);
				        JsonNode parametersNode = root.path("parameters");
				        JsonNode leagueNode = parametersNode.path("league");
				        Long leagueID = Long.parseLong(leagueNode.textValue());
				        JsonNode responseNode = root.path("response");
				        for (JsonNode node : responseNode) {
					        JsonNode teamNode = node.path("team");
					        String id = teamNode.path("id").asText();
					        String nombre = teamNode.path("name").asText();
					        String codeaf = teamNode.path("code").asText();
					        String paisName = teamNode.path("country").asText();
					        
					        Pais pais = pdClient.findCountry(paisName);
					        Club newClub = new Club();
					        newClub.setId(Long.parseLong(id));
					        newClub.setNombre(nombre);
					        newClub.setCodeaf(codeaf);
					        newClub.setIdPais(pais.getId());
					        Club savedClub = new Club();
					        
					        if (pdClient.findClub(newClub.getId())!=null) {
					        	 savedClub = pdClient.saveClub(newClub);
					        }
					        
					        ClubInLeague cil = new ClubInLeague();
					        cil.setClubId(savedClub.getId());
					        cil.setTorneoId(leagueID);
					        
					        if(pdClient.findCIL(newClub.getId(), leagueID)!=null) {
					        	pdClient.clubPlaysInLeague(cil);
					        }
				        }
		    	    } catch (Exception e) {
		    	    	throw e;
		    	    }
		        }
			}
			response.setCODE(Constants.CODE_OK);
			response.setDescription("OK");
			return response;
		} catch(Exception e) {
			throw e;
		}
	}
}
