package com.playerdatatracking.common;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.exceptions.operations.MalformedRequestException;
import com.playerdatatracking.requests.GenericRequest;

public class Methods {

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
			player.setPosicion(request.getPosicion());
	
			SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
			if (request.getDate()!=null)
				player.setDate(sdf.parse(request.getDate()));
			else
				player.setDate(new Date());
			player.setBirth(sdf.parse(request.getBirth()));
			return player;
		} else
			throw new MalformedRequestException("Error found while checking input parameters, the following ones are not present or whit a wrong type: " + errors.toString());
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
}
