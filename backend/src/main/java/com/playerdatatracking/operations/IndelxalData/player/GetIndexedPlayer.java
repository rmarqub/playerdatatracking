package com.playerdatatracking.operations.IndelxalData.player;

import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.ConvertedPlayer;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.operations.NoPlayerFoundException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetIndexedPlayer {

	@Autowired
	private PlayerDataClient pdClient;
	@Autowired
	private Environment env;
	private GenericResponse<ConvertedPlayer> response = new GenericResponse();
	private Methods methods;
	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	public void setEnv(Environment env) {
		this.env = env;
	}
	
	public GenericResponse<ConvertedPlayer> ejecutar(Long id) throws Exception{
		try {
			Player p = pdClient.searchPlayer(id);
			if (p==null)
				throw new NoPlayerFoundException("no player has been found with id: "+ id.toString());
			ConvertedPlayer cp = new ConvertedPlayer(p,pdClient);
			response.setEntity(cp);
			response.setCODE(Constants.CODE_OK);
			response.setDescription("OK");
		} catch (Exception e) {
			throw e;
		}
		return response;
	}
	
    public byte[] getPlayerPhoto(Long playerId) throws PlayerDataDBException {
        Player player = pdClient.getPlayer(playerId);
        return player.getPhoto();
    }

    public String getPlayerPhotoContentType(Long playerId) throws PlayerDataDBException {
    	Player player = pdClient.getPlayer(playerId);
        return player.getPhotoContentType();
    }
}
