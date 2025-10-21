package com.playerdatatracking.operations.manualdata;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.operations.MalformedRequestException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

public class UpdateManualPlayer {
	
	private PlayerDataClient pdClient;
	private GenericResponse response;

	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	
	public GenericResponse<ManualTrackedPlayer> ejecutar (GenericRequest request, Long idPlayer, Long userID) throws PlayerDataDBException, MalformedRequestException {
		response = new GenericResponse();
		ManualTrackedPlayer player = pdClient.getStudiedPlayer(idPlayer);
		if (player.getUserId()!=userID)
			throw new MalformedRequestException("not allowed to update a studied player from other user");
		
		String clubname = "";
		Long indexID = player.getIndexID();
		if (indexID!=null) {
			Player indexedPlayer = pdClient.getPlayer(indexID);
			if (indexedPlayer!=null) {
				Club c = pdClient.findClub(indexedPlayer.getTeam());
				if (c!=null)
					clubname = c.getNombre();
			}
		}
		if (!clubname.equals(""))
			player.setClub(clubname);
		player.setQualities(request.getQualities());
		player.setLikeable(request.getLikeable());
		player.setMostLikeDestination(request.getMostLikeDestination());
		player.setNombre(request.getNombre());
		player.setPosicion(request.getPosicion());
		player.setNota(request.getNota());
		
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
		LocalDate fechaHoy = LocalDate.now();
		player.setAge(Period.between(player.getBirth(), fechaHoy).getYears());
		
		player.setDate(LocalDate.now());
		
		boolean operationDone = pdClient.savePlayer(player);
		if (operationDone) {
			response.setCODE(Constants.CODE_OK);
			response.setDescription("OK");
			response.setEntity(player);
			return response;
		}
		else
			throw new PlayerDataDBException("Error found while saving player in MANUAL_TRACKED_PLAYER table, check method in clients package");
		}
		

}
