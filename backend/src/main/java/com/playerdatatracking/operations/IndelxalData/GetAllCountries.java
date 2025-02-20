package com.playerdatatracking.operations.IndelxalData;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.file.NotCreatedJsonFileResponse;
import com.playerdatatracking.exceptions.file.NotFilledJsonFileResponse;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

public class GetAllCountries {

	private ApiFootballClient restClient;
	private GenericResponse response;
	String filePath = "src/main/resources/json/apiFotball/countries/countries.json";
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
				restClient.getCountriesInfo(apiKey.getValor());
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
	        	updateCountries();
	        		
	        response.setCODE(Constants.CODE_OK);
	        response.setDescription("OK");
	        return response;
	       
	    } catch (Exception e) {
	    	throw e;
		}
	}
	
	public void updateCountries() throws PlayerDataDBException {
	    ObjectMapper objectMapper = new ObjectMapper();
	    try {
	    	pdClient.deleteAllCountries();
	        File jsonFile = new File(filePath);
	        JsonNode root = objectMapper.readTree(jsonFile);
	        JsonNode responseNode = root.path("response");

	        if (responseNode.isArray()) {
	            for (JsonNode node : responseNode) {
	                String name = node.path("name").asText();
	                String code = node.path("code").asText();

	                Pais pais = new Pais();
	                pais.setName(name);
	                pais.setCode(code);
	                pdClient.saveCountry(pais);
	            }
	        }

	    } catch (IOException e) {
	        throw new PlayerDataDBException("Error while reading or processing the JSON file", e);
	    }
	}
	
}
