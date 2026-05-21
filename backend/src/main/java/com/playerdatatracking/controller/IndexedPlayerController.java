package com.playerdatatracking.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.entities.indexaldata.ConvertedPlayer;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.entities.indexaldata.PlayerPhotoData;
import com.playerdatatracking.entities.indexaldata.DTO.PlayerPercentileDTO;
import com.playerdatatracking.entities.indexaldata.PlayerPercentile;
import com.playerdatatracking.operations.IndelxalData.player.GeneratePlayerPercentiles;
import com.playerdatatracking.operations.IndelxalData.player.GeneratePlayerPercentilesFrontend;
import com.playerdatatracking.operations.IndelxalData.player.GetBasicStats;
import com.playerdatatracking.operations.IndelxalData.player.GetIndexedPlayer;
import com.playerdatatracking.operations.IndelxalData.player.GetPlayerAbsenceDays;
import com.playerdatatracking.operations.IndelxalData.player.GetPlayerMarketValue;
import com.playerdatatracking.operations.IndelxalData.player.GetPlayerPercentiles;
import com.playerdatatracking.operations.IndelxalData.player.UpdatePlayer;
import com.playerdatatracking.responses.PlayerMarketValue;
import com.playerdatatracking.responses.PlayerAbsenceDays;
import com.playerdatatracking.operations.services.SearchIndexatedPlayers;
import com.playerdatatracking.requests.PlayerMatchRow;
import com.playerdatatracking.requests.SearchPlayersRequest;
import com.playerdatatracking.responses.GenericResponse;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
public class IndexedPlayerController {

    @Autowired
    private PlayerDataClient pdClient;
    @Autowired
    private SearchIndexatedPlayers operationSearchIndexatedPlayers;
    @Autowired
    private GetIndexedPlayer operationGetIxPlayer;
    @Autowired
    private UpdatePlayer operationUpdatePlayer;
    @Autowired
    private GetBasicStats operationGetBasicStats;
    @Autowired
    private GetPlayerAbsenceDays operationGetPlayerAbsenceDays;
    @Autowired
    private GeneratePlayerPercentiles operationGeneratePlayerPercentiles;
    @Autowired
    private GeneratePlayerPercentilesFrontend operationGeneratePlayerPercentilesFrontend;
    @Autowired
    private GetPlayerPercentiles operationGetPlayerPercentiles;
    @Autowired
    private GetPlayerMarketValue operationGetPlayerMarketValue;

    @GetMapping("/search")
    public GenericResponse<ConvertedPlayer> searchPlayers(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String team) {
        GenericResponse<ConvertedPlayer> response = new GenericResponse<>();
        try {
            SearchPlayersRequest request = new SearchPlayersRequest(player, team);
            response = operationSearchIndexatedPlayers.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/searchPlayer/{id}")
    public GenericResponse<ConvertedPlayer> getConvertedPlayer(@PathVariable("id") Long id) {
        GenericResponse<ConvertedPlayer> response = new GenericResponse<>();
        try {
            response = operationGetIxPlayer.ejecutar(id);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PatchMapping(value = "/updatePlayer", consumes = "multipart/form-data")
    public GenericResponse<Player> updatePlayer(@RequestBody GenericRequest request) {
        GenericResponse<Player> response = new GenericResponse<>();
        try {
            response = operationUpdatePlayer.ejecutar(request.getIndexId(), request.getPhoto());
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @GetMapping(value = "/players/{id}/photo", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"})
    public ResponseEntity<byte[]> getPhoto(@PathVariable Long id) {
        PlayerPhotoData photoData = pdClient.getPhotoData(id);
        if (photoData == null || photoData.getPhoto() == null || photoData.getPhoto().length == 0) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .cacheControl(CacheControl.noStore())
                    .header("Pragma", "no-cache")
                    .build();
        }

        String ct = Optional.ofNullable(photoData.getPhotoContentType()).orElse("image/jpeg");
        LocalDateTime updatedAt = Optional.ofNullable(photoData.getPhotoUpdatedAt()).orElse(LocalDateTime.now());
        long lastMod = updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ct))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .lastModified(lastMod)
                .body(photoData.getPhoto());
    }

    @GetMapping("/players/{indexId}/basic-stats")
    public GenericResponse<PlayerMatchRow> getBasicStats(@PathVariable("indexId") Long indexId) {
        GenericResponse<PlayerMatchRow> response = new GenericResponse<>();
        try {
            response = operationGetBasicStats.ejecutar(indexId);
            response.setCODE(Constants.CODE_OK);
            response.setDescription("OK");
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/playerAbsenceDays")
    public GenericResponse<PlayerAbsenceDays> getPlayerAbsenceDays(@RequestBody GenericRequest request) {
        GenericResponse<PlayerAbsenceDays> response = new GenericResponse<>();
        try {
            response = operationGetPlayerAbsenceDays.ejecutar(request.getIndexId());
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/generatePlayerPercentiles")
    public GenericResponse<String> generatePlayerPercentiles(@RequestBody GenericRequest request) {
        GenericResponse<String> response = new GenericResponse<>();
        try {
            response = operationGeneratePlayerPercentiles.ejecutar(request.getSeason());
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/generatePlayerPercentilesFrontend")
    public GenericResponse<String> generatePlayerPercentilesFrontend(@RequestBody GenericRequest request) {
        GenericResponse<String> response = new GenericResponse<>();
        try {
            response = operationGeneratePlayerPercentilesFrontend.ejecutar(request.getSeason());
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/getPlayerPercentiles")
    public GenericResponse<PlayerPercentileDTO> getPlayerPercentiles(@RequestBody GenericRequest request) {
        GenericResponse<PlayerPercentileDTO> response = new GenericResponse<>();
        try {
            response = operationGetPlayerPercentiles.ejecutar(request.getIndexId(), request.getSeason());
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/playerMarketValue")
    public GenericResponse<PlayerMarketValue> getPlayerMarketValue(@RequestBody GenericRequest request) {
        GenericResponse<PlayerMarketValue> response = new GenericResponse<>();
        try {
            response = operationGetPlayerMarketValue.ejecutar(request);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/playerIdByIndexId")
    public GenericResponse<Long> getPlayerIdByIndexId(@RequestBody GenericRequest request) {
        GenericResponse<Long> response = new GenericResponse<>();
        try {
            Long playerId = pdClient.getPlayerIdByIndexId(request.getIndexId());
            if (playerId != null) {
                response.setCODE(Constants.CODE_OK);
                response.setEntity(playerId);
            } else {
                response.setCODE(Constants.CODE_ERR_NO_PLAYER_FOUND);
                response.setDescription("Jugador con indexId " + request.getIndexId() + " no encontrado");
            }
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
}
