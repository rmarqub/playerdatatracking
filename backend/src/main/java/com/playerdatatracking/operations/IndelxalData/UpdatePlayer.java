package com.playerdatatracking.operations.IndelxalData;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class UpdatePlayer {

	private Methods methods;
	@Autowired
	private PlayerDataClient pdClient;
	private GenericResponse<Player> response = new GenericResponse();
	private Environment env;
	
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	public void setEnv(Environment env) {
		this.env = env;
	}

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp"
    );
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    public GenericResponse<Player> ejecutar(Long playerId, MultipartFile photo) throws PlayerDataDBException {
    	response = new GenericResponse();
        Player player = pdClient.getPlayer(playerId);

        if (photo == null || photo.isEmpty()) {
	        player.setPhoto(null);
	        player.setPhotoContentType(null);
	        player.setPhotoUpdatedAt(null);
	        pdClient.saveIndexedPlayer(player);
	        response.setCODE(Constants.CODE_OK);
			response.setDescription("OK");
			return response;
        }

        if (!ALLOWED_CONTENT_TYPES.contains(photo.getContentType())) {
            throw new IllegalArgumentException("Tipo de archivo no permitido: " + photo.getContentType());
        }
        if (photo.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("La imagen supera el límite de 5MB");
        }

        try {
            player.setPhoto(photo.getBytes());
            player.setPhotoContentType(photo.getContentType());
            player.setPhotoUpdatedAt(LocalDateTime.now());
            pdClient.saveIndexedPlayer(player);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo procesar la imagen", e);
        }
        
        response.setCODE(Constants.CODE_OK);
		response.setDescription("OK");
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
