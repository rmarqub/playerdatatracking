package com.playerdatatracking.operations.IndelxalData.indexal;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
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

@Component
public class UpdateClubsData {

	@Autowired
	private PlayerDataClient pdClient;
	private ApiFootballClient restClient;
	@Autowired
	private KeysManagement keyMethods;
	@Autowired
	private Environment env;
	String directoryPath = "src/main/resources/json/apiFotball/leagues/";
    String excludedFile = "leagues.json";
	private GenericResponse<Club> response = new GenericResponse();
	private Methods methods;

	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}

	public void setEnv(Environment env) {
		this.env = env;
	}


	public GenericResponse<Club> ejecutar(GenericRequest request) throws Exception {

		restClient = new ApiFootballClient();
		methods = new Methods();
		try {
			if(request.getRestUpdate()!=null && request.getRestUpdate().equals("true")) {
				String actualSeason = pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();
				List<Torneo> studiedLeagues = pdClient.getStudiedLeagues();
				if (studiedLeagues==null)
					throw new PlayerDataDBException("Error al buscar ligas para actualizar los datos de clubes");
				if (studiedLeagues.size()!=0) {
					Keys apiKey = keyMethods.nextKey();
					if (apiKey==null)
						throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
					for (Torneo league : studiedLeagues) {
						HashMap<String, String> queryParams = new HashMap<>();
						queryParams.put("season", actualSeason);
						queryParams.put("league", league.getId().toString());
						String responsePath = "";
						if (keyMethods.checkReadiness(apiKey)) {
							responsePath = restClient.getClubs(queryParams, apiKey.getValor(), league.getName());
							methods.checkGoodClubsCall(responsePath, queryParams, apiKey.getValor(), league.getName());
							keyMethods.useKey(apiKey);
							methods.sleep(180);
						}
						else {
							throw new ApiKeyManagementException("error al intentar usar una key no disponible");
						}
					}
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
		        	throw e;
		        }

		        // Único borrado seguro: CIL no tiene FKs apuntando a ella.
		        // Los clubs y torneos NO se borran porque fixture, player y fixture_*_stats
		        // los referencian sin CASCADE — borrarlos rompería la integridad histórica.
		        pdClient.deleteAllCILs();

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

				        // Determinar si este torneo es de selecciones para marcar los clubs nuevos
				        Torneo torneo = pdClient.getTorneoById(leagueID);
				        boolean esLigaDeSelecciones = torneo != null
				                && Integer.valueOf(Constants.SELECCIONES).equals(torneo.getTipoTorneo());

				        List<Club> clubsToSave = new ArrayList<>();
				        List<ClubInLeague> cilsToSave = new ArrayList<>();

				        JsonNode responseNode = root.path("response");
				        for (JsonNode node : responseNode) {
					        JsonNode teamNode = node.path("team");
					        Long clubId = Long.parseLong(teamNode.path("id").asText());
					        String nombre = teamNode.path("name").asText();
					        String codeaf = teamNode.path("code").asText();
					        String paisName = teamNode.path("country").asText();
					        paisName = Methods.checkCountryClub(pdClient, paisName, nombre);

					        System.out.println(paisName);

					        Pais pais = pdClient.findCountry(paisName);
					        Club existingClub = pdClient.findClub(clubId);
					        Club club;

					        if (existingClub == null) {
					        	// Club nuevo: asignar esSeleccion según el tipo del torneo en que aparece
					        	club = new Club();
					        	club.setId(clubId);
					        	club.setEsSeleccion(esLigaDeSelecciones);
					        } else {
					        	// Club existente: actualizar datos pero preservar esSeleccion ya registrado.
					        	// Un club existente que no sea selección nunca aparecerá en un torneo de
					        	// selecciones, así que no es necesario recalcular este flag.
					        	club = existingClub;
					        }

					        club.setNombre(nombre);
					        club.setCodeaf(codeaf);
					        club.setIdPais(pais.getId());
					        clubsToSave.add(club);

					        ClubInLeague cil = new ClubInLeague();
					        cil.setClubId(clubId);
					        cil.setTorneoId(leagueID);
					        cilsToSave.add(cil);
				        }

				        // Guardar en batch: primero clubs (para que existan antes de insertar CILs)
				        if (!clubsToSave.isEmpty()) {
				        	pdClient.saveAllClubs(clubsToSave);
				        }
				        if (!cilsToSave.isEmpty()) {
				        	pdClient.saveAllCILs(cilsToSave);
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
