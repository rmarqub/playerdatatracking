package com.playerdatatracking.common;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.entities.indexaldata.ConfigParams;
import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.file.NotCreatedJsonFileResponse;
import com.playerdatatracking.exceptions.file.NotFilledJsonFileResponse;
import com.playerdatatracking.exceptions.operations.MalformedRequestException;
import com.playerdatatracking.requests.GenericRequest;

public class Methods {
	

	private ApiFootballClient restClient;

	public static ArrayList<String> playerWrongValues(GenericRequest player) {
		ArrayList<String> errors = new ArrayList<String>();
		if (player.getNombre()==null || player.getNombre().equals(""))
			errors.add("Nombre");
		if ((Float)player.getNota()==null)
			errors.add("Nota");
		if (player.getClub()==null || player.getClub().equals(""))
			errors.add("Club");
		if (player.getBirth()==null)
			errors.add("Birth");
		return errors;
	}
	public static boolean isMarketActive(PlayerDataClient pdClient) throws PlayerDataDBException {
		ConfigParams c = pdClient.getParam(Constants.MARKET_ACTIVE);
		if (c.getValue().equalsIgnoreCase("true"))
			return true;
		else
			return false;
	}
	public static boolean useDupped(PlayerDataClient pdClient) throws PlayerDataDBException {
		ConfigParams c = pdClient.getParam(Constants.USE_DUPPED);
		if (c.getValue().equalsIgnoreCase("true"))
			return true;
		else
			return false;
	}
	public static String checkCountryClub(PlayerDataClient pdClient, String country, String club) throws PlayerDataDBException {
		Pais p = pdClient.findCountry(country);
		if (p!=null)
			return country;
		if (club.equals("Galatasaray U18"))
			return "Turkey";
		if (country.equals("Türkiye"))
			return "Turkey";
		if (country.equals("Lituania"))
			return "Lithuania";
		if (country.equals("intl") && club.equals("Crvena Zvezda U18"))
			return "Serbia";
		if (country.equals("North Macedonia"))
			return "Macedonia";
		if (country==null || country.equals("intl"))
			return "World";
		return "World";
	}
	
	
	public static int exceptionCodeManagement(Exception e) {
		if (e==null)
			return -1;
		String name = e.getClass().getSimpleName();
		
		switch (name) {
			case "MalformedRequestException":
				return Constants.CODE_ERR_MALFORMED_PARAMS;
			case "PlayerDataDBException":
				return Constants.CODE_ERR_PDDB;
			case "PlayerInputException":
				return Constants.CODE_ERR_INPUT_EXCEPTION;
			case "IOException":
				return Constants.CODE_ERR_XSL_READING_EXCEPTION;
			case "ParseException":
				return Constants.CODE_ERR_PARSE_EXCEPTION;
			case "NotFilledJsonFileResponse":
				return Constants.CODE_ERR_NOT_FILLED_JSON_RESPONSE;
			case "NotCreatedJsonFileResponse":
				return Constants.CODE_ERR_NOT_CREATED_JSON_RESPONSE;
			case "SecretKeyBadGeneratedException":
				return Constants.CODE_ERR_GENERATED_KEY;
			case "ApiKeyManagementException":
				return Constants.CODE_ERR_KEY_MNGMT;
			case "NoPlayerFoundException":
				return Constants.CODE_ERR_NO_PLAYER_FOUND;
			default:
				return -1;
				
		}
	}



	public static ManualTrackedPlayer bindRequestAsPlayer(GenericRequest request) throws ParseException, MalformedRequestException {
		ArrayList<String> errors = playerWrongValues(request);
		if (errors.size()==0) {
			ManualTrackedPlayer player = new ManualTrackedPlayer();
			player.setNombre(request.getNombre());
			player.setNota(request.getNota());
			if (request.getAge()==null || request.getAge().equals(""))
				player.setAge(null);
			else
				player.setAge(Integer.parseInt(request.getAge()));
			player.setClub(request.getClub());
			player.setLikeable(request.getLikeable());
			player.setMostLikeDestination(request.getMostLikeDestination());
			player.setQualities(request.getQualities());
			player.setIndexID(request.getIndexId());
			player.setPosicion(request.getPosicion());
		    LocalDate birthDate = parseLocalDateFlexible(request.getBirth());
		    player.setBirth(birthDate);
		    LocalDate date = (request.getDate() != null && !request.getDate().isBlank())
		            ? parseLocalDateFlexible(request.getDate())
		            : LocalDate.now();
		    player.setDate(date);
			return player;
		} else
			throw new MalformedRequestException("Error found while checking input parameters, the following ones are not present or whit a wrong type: " + errors.toString());
	}
	
	
	public static LocalDate parseLocalDateFlexible(String input) {
	    if (input == null || input.isBlank()) {
	        throw new IllegalArgumentException("Date string is null/blank");
	    }
	    DateTimeFormatter[] fmts = new DateTimeFormatter[] {
	            DateTimeFormatter.ISO_LOCAL_DATE,
	            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
	            DateTimeFormatter.ofPattern("yyyy/MM/dd")
	    };
	    for (DateTimeFormatter f : fmts) {
	        try {
	            return LocalDate.parse(input, f);
	        } catch (DateTimeParseException ignore) {}
	    }
	    throw new IllegalArgumentException("Unsupported date format: " + input);
	}
	
	
	public static int getTournamentType(String name, String country, String type) {
		switch (country) {
		case Constants.WORLD:
			switch(name) {
			case Constants.EURO:
			case Constants.CONFEDERATIONS:
			case Constants.MUNDIAL:
			case Constants.ASIAN_GAMES:
			case Constants.ASIAN_CUP:
			case Constants.WWORLD_CUP:
			case Constants.OLYMPICS:
			case Constants.CECAFA:
			case Constants.SAFF:
			case Constants.MUNDIAL_QF_PO:
			case Constants.AFC:
			case Constants.AFRICAN_NC:
			case Constants.AFF:
			case Constants.GOLD_CUP:
			case Constants.EAFF:
			case Constants.AFRICAN_CUP:
			case Constants.WOLYMPICS:
			case Constants.GULF_CUP:
			case Constants.OFC:
			case Constants.EURO_U21:
			case Constants.MUNDIAL_U20:
			case Constants.COPA_AMERICA:
			case Constants.EU_NATIONS_LEAGUE:
			case Constants.CONCACAF_QF:
			case Constants.CF_MUNDIAL_QF:
			case Constants.EU_MUNDIAL_QF:
			case Constants.OC_MUNDIAL_QF:
			case Constants.SAA_MUNDIAL_QF:
			case Constants.AF_MUNDIAL_QF:
			case Constants.AS_MUNDIAL_QF:
			case Constants.EURO_U19:
			case Constants.CONCACAF_U20:
			case Constants.ASIAN_CUP_QF:
			case Constants.PACIFIC_GAMES:
			case Constants.SUDAMERICANO_U20:
			case Constants.MUNDIAL_U17:
			case Constants.ASIAN_CUP_U23:
			case Constants.AF_NATIONS_LEAGUE:
			case Constants.AF_NATIONS_CUP_U20:
			case Constants.AF_OLYMPICS_QF:
			case Constants.GOLD_CUP_QF:
			case Constants.COSAFA:
			case Constants.ARAB_CUP:
			case Constants.U20_ELITE:
			case Constants.AF_CUP_NATIONS_QF:
			case Constants.SAA_YOUTH_GAMES:
			case Constants.AFC_U23_QF:
			case Constants.AF_U23_CUP_NATIONS_QF:
			case Constants.EU_U17_QF:
			case Constants.EU_U19_QF:
			case Constants.AFF_U23:
			case Constants.SEAS_GAMES:
			case Constants.FINALISSIMA:
			case Constants.MEDITERRANEAN_GAMES:
			case Constants.EU_U17:
			case Constants.AFF_U19:
			case Constants.ARAB_CUP_U20:
			case Constants.CAC_GAMES:
			case Constants.AFC_U17:
			case Constants.CAF_U23_CUP:
			case Constants.CONCACAF_CAA:
			case Constants.AF_GAMES:
			case Constants.EU_U21_QF:
			case Constants.EURO_QF:
			case Constants.CONCACAF_U17:
			case Constants.AFC_U20:
			case Constants.CONMEBOL_U17:
			case Constants.CAF_U17:
			case Constants.CAFA:
			case Constants.CONMEBOL_OLYMPICS_QF:
			case Constants.CONCACAF_U20_QF:
			case Constants.WAFF_U23:
			case Constants.OLYMPICS_QF:
			case Constants.OFC_U19:
				return Constants.INTERNATIONAL_ID;
			default:
				return Constants.COPA_INT_ID;
			}
		default:
			switch(type) {
			case Constants.LEAGUE:
				return Constants.LEAGUE_ID;
			default:
				return Constants.CUP_ID;
			}
				
		}
	}
	
	public static void sleep(long tiempoTotalMs) {
		int partes = 5;
		long intervalo = tiempoTotalMs / partes;
		
		try {
		    for (int j = 1; j <= partes; j++) {
		        Thread.sleep(intervalo);
		System.out.println("Han pasado " + (j * (tiempoTotalMs / 1000 / partes)) + " segundos...");
		    }
		} catch (InterruptedException e) {
		    e.printStackTrace();
		}
    }
	
	public void checkGoodClubsCall(String jsonResponsePath, HashMap<String, String> queryParams, String apikey, String leagueName) throws Exception {
        try {
            String content = new String(Files.readAllBytes(Paths.get(jsonResponsePath)));
            this.restClient = new ApiFootballClient();
            
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(content);
            JsonNode errorsNode = jsonNode.path("errors").path("rateLimit");
            if (!errorsNode.isMissingNode() && Constants.RATE_LIMIT_ERROR_MESSAGE.equals(errorsNode.asText())) {
                System.out.println("Se ha detectado un error de rate limit. Iniciando espera de 1 minuto...");
                Methods.sleep(70000);
                System.out.println("Reiniciando operacion...");
                restClient.getClubs(queryParams, apikey, leagueName);
            }

        } catch (Exception e) {
            throw e;
        }
        
	}
	
	public void checkGoodPlayersCall(String jsonResponsePath, HashMap<String, String> queryParams, String apikey, String clubName, String page) throws Exception {
        try {
            String content = new String(Files.readAllBytes(Paths.get(jsonResponsePath)));
            this.restClient = new ApiFootballClient();
            
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(content);
            JsonNode errorsNode = jsonNode.path("errors").path("rateLimit");
            String errorsText = errorsNode.asText();
            if (!errorsNode.isMissingNode() && errorsNode.asText().toLowerCase().contains("too many requests")) {
                System.out.println("Se ha detectado un error de rate limit. Iniciando espera de 1 minuto...");
                Methods.sleep(70000);
                System.out.println("Reiniciando operacion...");
                restClient.getPlayersPaged(queryParams, apikey, clubName, page);
            }

        } catch (Exception e) {
            throw e;
        }
        
	}
	public void checkGoodTransferCall(String content, String apiKey, Long indexID) throws Exception {
		
        try {
            this.restClient = new ApiFootballClient();
            
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(content);
            JsonNode errorsNode = jsonNode.path("errors").path("rateLimit");
            String errorsText = errorsNode.asText();
            if (!errorsNode.isMissingNode() && errorsNode.asText().toLowerCase().contains("too many requests")) {
                System.out.println("Se ha detectado un error de rate limit. Iniciando espera de 1 minuto...");
                Methods.sleep(70000);
                System.out.println("Reiniciando operacion...");
                restClient.getTransfer(apiKey, indexID);
            }

        } catch (Exception e) {
            throw e;
        }
		
	}
	
	public boolean checkGoodCall(String content, String apiKey, Long teamID) throws Exception{
		try {
			ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(content);
            JsonNode errorsNode = jsonNode.path("errors").path("rateLimit");
            String errorsText = errorsNode.asText();
            if (!errorsNode.isMissingNode() && errorsNode.asText().toLowerCase().contains("too many requests")) {
                System.out.println("Se ha detectado un error de rate limit. Iniciando espera de 1 minuto...");
                Methods.sleep(60000);
                System.out.println("Reiniciando operacion...");
                return true;
            }
            return false;
        } catch (Exception e) {
            throw e;
        }
	}
	
	public int getTotalOfPagesResponse(String jsonResponsePath) throws NotCreatedJsonFileResponse, NotFilledJsonFileResponse, IOException {
		int response = 0;
		try {
			File file = new File(jsonResponsePath);
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
	        JsonNode pagingNode = root.path("paging");
	        JsonNode totalPagesNode = pagingNode.path("total");
	        response = totalPagesNode.intValue();
	        return response;
		} catch (Exception e) {
			throw e;
		}
		
	}
}
