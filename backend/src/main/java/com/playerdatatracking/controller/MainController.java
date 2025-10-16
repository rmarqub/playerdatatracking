package com.playerdatatracking.controller;

import java.awt.PageAttributes.MediaType;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.common.crypto.AESCrypto;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.ConvertedPlayer;
import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.operations.IndelxalData.GetAllCountries;
import com.playerdatatracking.operations.IndelxalData.GetAllLeagues;
import com.playerdatatracking.operations.IndelxalData.GetIndexedPlayer;
import com.playerdatatracking.operations.IndelxalData.UpdateClubsData;
import com.playerdatatracking.operations.IndelxalData.UpdatePlayer;
import com.playerdatatracking.operations.IndelxalData.UpdatePlayersData;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.operations.manualdata.AddPlayer;
import com.playerdatatracking.operations.manualdata.DeletePlayer;
import com.playerdatatracking.operations.manualdata.GetAllPlayers;
import com.playerdatatracking.operations.manualdata.GetPlayer;
import com.playerdatatracking.operations.services.SearchIndexatedPlayers;
import com.playerdatatracking.repositories.indexaldata.MANUAL_TRACKED_PLAYERRepository;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.requests.SearchPlayersRequest;
import com.playerdatatracking.responses.GenericResponse;


import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;



@RestController
public class MainController {
	
//	---------ENVIRONMENT---------
	@Autowired
	private Environment env;
	@Autowired
	private ResourceLoader resourceLoader;
	
	
//	---------CLIENTS---------
	@Autowired
	private PlayerDataClient pdClient;
	
	
//	---------RFEPOSITORIES---------
	@Autowired
	private MANUAL_TRACKED_PLAYERRepository repository;
	
	
//  ---------OPERATIONS---------
	private AddPlayer operationAddPlayer = new AddPlayer();
	private GetAllPlayers operationGetAllPlayers = new GetAllPlayers();
	private GetAllLeagues operationGetAllLeagues = new GetAllLeagues();
	private AESCrypto operationCrypto = new AESCrypto();
	private KeysManagement operationKeys = new KeysManagement();
	private DeletePlayer operationDeletePlayer = new DeletePlayer();
	private GetPlayer oeprationGetPlayer = new GetPlayer();
	private GetAllCountries operationGetCountries = new GetAllCountries();
	private UpdateClubsData operationUpdateClubsData = new UpdateClubsData();
	private UpdatePlayersData operationUpdatePlayersData = new UpdatePlayersData();
	private SearchIndexatedPlayers operationSearchIndexatedPlayers = new SearchIndexatedPlayers();
	private GetIndexedPlayer operationGetIxPlayer = new GetIndexedPlayer();
	private UpdatePlayer operationUpdatePlayer = new UpdatePlayer();
	
	
// 	---------RESPONSES---------	
	GenericResponse response = new GenericResponse();
	
	
	@GetMapping("/secretkey")
	public GenericResponse getSecretKey() throws Exception {
    	response = new GenericResponse();
		try {
			response = operationCrypto.getNewSecretKey();
		} catch (Exception e) {
        	response.setCODE(Methods.exceptionCodeManagement(e));
        	response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
		}
		return response;
	}
	@PostMapping("/apiKey")
	public GenericResponse storeApiKey(@RequestBody GenericRequest request){
    	response = new GenericResponse();
		operationKeys.setPdClient(pdClient);
		operationKeys.setEnv(this.env);
		try {
			response = operationKeys.storeKey(request.getNewKey(), request.getMail(), request.getPlan(), request.getIdService());
		} catch (Exception e) {
        	response.setCODE(Methods.exceptionCodeManagement(e));
        	response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
		
		return response;
	}
	@GetMapping("/apiKey")
	public GenericResponse getApiKey(@RequestBody GenericRequest request) {
    	response = new GenericResponse();
		operationKeys.setPdClient(pdClient);
		operationKeys.setEnv(this.env);
		try {
			response = operationKeys.getKeyByKey(request.getApiKey());
		} catch (Exception e) {
        	response.setCODE(Methods.exceptionCodeManagement(e));
        	response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
		
		return response;
	
	}
	
	
	@GetMapping("/myapiKeys")
	public GenericResponse getApiKeysbyMail(@RequestBody GenericRequest request) {
    	response = new GenericResponse();
		operationKeys.setPdClient(pdClient);
		operationKeys.setEnv(this.env);
		try {
			response = operationKeys.getKeyByMail(request.getMail());
		} catch (Exception e) {
        	response.setCODE(Methods.exceptionCodeManagement(e));
        	response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
		return response;
	}
    
    @PostMapping("/leagues")
    public GenericResponse getAllLeagues(@RequestBody GenericRequest request) {
    	response = new GenericResponse();
    	operationGetAllLeagues.setEnv(env);
    	operationGetAllLeagues.setPdClient(pdClient);
    	try {
    		response = operationGetAllLeagues.ejecutar(request);
    	} catch (Exception e) {
    		response.setCODE(Methods.exceptionCodeManagement(e));
    		response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
    	}
    	return response;
    }
    @PostMapping("/countries")
    public GenericResponse getAllCountries (@RequestBody GenericRequest request) {
    	response = new GenericResponse();
    	operationGetCountries.setEnv(env);
    	operationGetCountries.setPdClient(pdClient);
    	try {
    		response = operationGetCountries.ejecutar(request);
    	} catch (Exception e) {
    		response.setCODE(Methods.exceptionCodeManagement(e));
    		response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
    	}
    	return response;
    	
    }
    
    @PostMapping("/updateCountries")
    public GenericResponse updateCountries() {
    	response = new GenericResponse();
    	operationGetCountries.setEnv(env);
    	operationGetCountries.setPdClient(pdClient);
    	try {
    		operationGetCountries.updateCountries();
    		response.setCODE(Constants.CODE_OK);
    		response.setDescription("OK");
    	} catch (Exception e) {
    		response.setCODE(Methods.exceptionCodeManagement(e));
    		response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
    	}
    	return response;
    }
    
    
    @PostMapping("/updateLeagues")
    public GenericResponse updateLeagues() {
    	response = new GenericResponse();
    	operationGetAllLeagues.setEnv(env);
    	operationGetAllLeagues.setPdClient(pdClient);
    	try {
    		operationGetAllLeagues.updateLeagues();
    		response.setCODE(Constants.CODE_OK);
    		response.setDescription("OK");
    	} catch (Exception e) {
    		response.setCODE(Methods.exceptionCodeManagement(e));
    		response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
    	}
    	return response;
    }
    
//    @PostMapping("/player")
//    public GenericResponse addPlayer(@RequestBody GenericRequest request) throws PlayerDataDBException, PlayerInputException, ParseException {
//    	response = new GenericResponse();
//    	operationAddPlayer.setPdClient(pdClient);
//        try {
//        	ManualTrackedPlayer player = Methods.bindRequestAsPlayer(request);
//        	response = operationAddPlayer.ejecutar(player);
//        } catch (Exception e) {
//        	response.setCODE(Methods.exceptionCodeManagement(e));
//        	response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
//        }
//        return response;
//    }
    @PostMapping("/player")
    public GenericResponse addPlayer(@RequestBody GenericRequest p, HttpServletRequest request) {
    	response = new GenericResponse();
    	operationAddPlayer.setPdClient(pdClient);
        Long userId = currentUserId(request);
        try {
        	ManualTrackedPlayer player = Methods.bindRequestAsPlayer(p);
        	response = operationAddPlayer.createForUser(player, userId);
        } catch (Exception e) {
    		response.setCODE(Methods.exceptionCodeManagement(e));
    		response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
    
    
    @DeleteMapping("/player")
    public GenericResponse deletePlayer(@RequestBody GenericRequest request) {
    	response = new GenericResponse();
    	operationDeletePlayer.setPdClient(pdClient);
    	try {
    		response = operationDeletePlayer.ejecutar(request.getNombre());
    	} catch (Exception e) {
        	response.setCODE(Methods.exceptionCodeManagement(e));
        	response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
    
    @PatchMapping(value = "/updatePlayer", consumes = "multipart/form-data")
    public GenericResponse<Player> updatePlayer(@RequestBody GenericRequest request){
    	response = new GenericResponse<Player>();
    	operationUpdatePlayer.setPdClient(pdClient);
    	try {
    		response = operationUpdatePlayer.ejecutar(request.getIndexId(), request.getPhoto());
    	} catch (Exception e) {
        	response.setCODE(Methods.exceptionCodeManagement(e));
        	response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    	
    }
    
    @GetMapping("/player/{id}")
    public GenericResponse<ManualTrackedPlayer> getPlayer(@PathVariable("id") Long id) {
    	response = new GenericResponse();
        oeprationGetPlayer.setPdClient(pdClient);
        try {
            response = oeprationGetPlayer.ejecutar(id);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    
    @PostMapping("/updateClubsInfo")
    public GenericResponse<Club> updateClubsData(@RequestBody GenericRequest request){
    	response = new GenericResponse();
    	operationUpdateClubsData.setEnv(env);
    	operationUpdateClubsData.setPdClient(pdClient);
    	try {
    		response = operationUpdateClubsData.ejecutar(request);
    	} catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
    
    @PostMapping("/updatePlayers")
    public GenericResponse<Player> updatePlayersData(@RequestBody GenericRequest request){
    	response = new GenericResponse();
    	operationUpdatePlayersData.setEnv(env);
    	operationUpdatePlayersData.setPdClient(pdClient);
    	try {
    		response = operationUpdatePlayersData.ejecutar(request);
    	} catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
    
    @GetMapping("/search")
    public GenericResponse<ConvertedPlayer> searchPlayers( @RequestParam(required = false) String player, @RequestParam(required = false) String team) {
        operationSearchIndexatedPlayers.setEnv(env);
        operationSearchIndexatedPlayers.setPdClient(pdClient);
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
    	response = new GenericResponse();
    	operationGetIxPlayer.setPdClient(pdClient);
    	operationGetIxPlayer.setEnv(env);
        try {
            response = operationGetIxPlayer.ejecutar(id);
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
    
    private Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Object user = session.getAttribute("USER");
        if (!(user instanceof Map<?,?> map) || map.get("id") == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ((Number) map.get("id")).longValue();
    }

    @GetMapping("/players")
    public GenericResponse<ManualTrackedPlayer> listMine(HttpServletRequest request) throws PlayerDataDBException {
    	response = new GenericResponse<ManualTrackedPlayer>();
    	operationGetAllPlayers.setPdClient(pdClient);
        Long userId = currentUserId(request);
        try {
        	response = operationGetAllPlayers.ejecutar(userId);
        }catch (Exception e) {
        	response.setCODE(Methods.exceptionCodeManagement(e));
        	response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
        
    
    }
    
    
}