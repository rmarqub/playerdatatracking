package com.playerdatatracking.operations.IndelxalData.indexal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.DuppedPlayers;
import com.playerdatatracking.entities.indexaldata.Transfer;
import com.playerdatatracking.entities.indexaldata.TransferRecord;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.IndexTeamPair;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class TransferCheckOfPlayers {

	@Autowired
	private PlayerDataClient pdClient;
	private static final HttpClient HTTP = HttpClient.newHttpClient();
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private ApiFootballClient apiClient = new ApiFootballClient();
	@Autowired
	private KeysManagement keyMethods;
	@Autowired
	private Environment env;
	boolean isMarketActive;
	boolean useDupped;
	private GenericResponse response;
	String actualSeason = "";

	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}

	public void setEnv(Environment env) {
		this.env = env;
	}

	public GenericResponse ejecutar() throws PlayerDataDBException {
		response = new GenericResponse();
		try {
			actualSeason = pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();
			isMarketActive = Methods.isMarketActive(pdClient);
			useDupped = Methods.useDupped(pdClient);
			List<IndexTeamPair> l = pdClient.getDuppedPlayersWithDiffTeam();
			processList(l);
			
			response.setCODE(0);
			response.setDescription("OK");
			return response;
		} catch(Exception e) {
			throw e;
		}
	}
	public void processList(List<IndexTeamPair> duppedPlayers) throws PlayerDataDBException {
		Map<Long, List<Long>> checklist = groupTeamsByIndexId(duppedPlayers);
		for (Entry<Long, List<Long>> e : checklist.entrySet()) {
			Long indexId = e.getKey();
			Set<Long> teamIds = e.getValue().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
			
			if (teamIds.size() < 2) {
	            continue;
	        }
			if(!isMarketActive && useDupped) {
				
				List<DuppedPlayers> duppedList = pdClient.getDuppedPlayerById(indexId);
				DuppedPlayers dupp = new DuppedPlayers();
				if (duppedList !=null && duppedList.size()>1) {
					//jugador duplicado en duppedPlayer (no deberia haber ningun caso)
					for (DuppedPlayers d : duppedList) {
						if (d.getSeason().equals(actualSeason)) {
							dupp= d;
							break;
						}
					}
				//jugador registrado en duppedPlayer
				}else if (duppedList !=null && duppedList.size()==1)
					dupp= duppedList.get(0);
				
				//jugador duplicado encontrado
				if(dupp.getId()!=null) {
					if (teamIds.contains(dupp.getTeam())) {
						teamIds.remove(dupp.getTeam());
						for (Long club : teamIds) {
							pdClient.deleteIndexedPlayer(indexId, club);
							System.out.printf("Eliminado duplicado: indexId=%d, teamId(out)=%d (mantengo in=%d)%n", indexId, club, dupp.getTeam());
						}
						continue;
					}
				}
			}

			
			//jugador no registrado en duppedPlayer ni hay transfer registrada (nuevo mercado de transferencias con registros ya accesibles)
			List<TransferRecord> transfers = fetchTransfers(indexId);

	        transfers.sort(Comparator.comparing(TransferRecord::getDate).reversed());
	        
	        Optional<TransferRecord> match = transfers.stream()
	                .filter(tr -> tr.getInId() != null && tr.getOutId() != null)
	                .filter(tr -> teamIds.contains(tr.getInId()) && teamIds.contains(tr.getOutId()))
	                .findFirst();

	        if (match.isPresent()) {
	            TransferRecord tr = match.get();
	            Long toDeleteTeamId = tr.getOutId();
	            Long newTeam = tr.getInId();
	            saveTransfer(indexId, tr.getInId(), tr.getOutId(), actualSeason);
	            saveDupped(indexId, tr.getInId(), actualSeason);
	            try {
	                pdClient.deleteIndexedPlayer(indexId, toDeleteTeamId);
	                System.out.printf("Eliminado duplicado: indexId=%d, teamId(out)=%d (mantengo in=%d, fecha=%s)%n",indexId, toDeleteTeamId, tr.getInId(), tr.getDate());
	            } catch (Exception ex) {
	                System.err.printf("Fallo al eliminar indexId=%d teamId=%d: %s%n",indexId, toDeleteTeamId, ex.getMessage());
	            }
	        } else {
	            System.out.printf("Sin transfer coincidente para indexId=%d con teams=%s%n", indexId, teamIds);
	        }
		}
    }
	
	public Map<Long, List<Long>> groupTeamsByIndexId(List<IndexTeamPair> pairs) {
	    if (pairs == null || pairs.isEmpty()) {
	        return Collections.emptyMap();
	    }

	    return pairs.stream()
	            .collect(Collectors.groupingBy(IndexTeamPair::getIndexId, Collectors.mapping(IndexTeamPair::getTeam, Collectors.toList())));
	}

	private List<TransferRecord> fetchTransfers(Long indexId) {
		Methods m = new Methods();
	    if (indexId == null) return Collections.emptyList();
	    try {
	    	
	    	Keys apiKey = keyMethods.nextKey();
	    	if (apiKey==null)
				throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
	    	HttpResponse<String> resp = null;
	    	if (keyMethods.checkReadiness(apiKey)) {
	    		resp = apiClient.getTransfer(apiKey.getValor(), indexId);
	    		m.checkGoodTransferCall(resp.body(), apiKey.getValor(), indexId);
	    		keyMethods.useKey(apiKey);
	    		m.sleep(180);
	    	}
	    	else
				throw new ApiKeyManagementException("error al intentar usar una key no disponible");
	
	        JsonNode root = MAPPER.readTree(resp.body());
	        JsonNode responseArr = root.path("response");
	        if (!responseArr.isArray() || responseArr.size() == 0) return Collections.emptyList();
	        
	        
	        List<TransferRecord> out = new ArrayList<>();
	        for (JsonNode responseNode : responseArr) {
	            JsonNode transfers = responseNode.path("transfers");
	            if (!transfers.isArray()) continue;
	
	            for (JsonNode t : transfers) {
	                String dateStr = t.path("date").asText(null);
	                if (dateStr == null || dateStr.isBlank()) continue;
	
	                LocalDate date;
	                try {
	                    date = LocalDate.parse(dateStr, DTF);
	                } catch (Exception ex) {
	                    continue;
	                }
	
	                JsonNode inNode  = t.path("teams").path("in");
	                JsonNode outNode = t.path("teams").path("out");
	                Long inId  = inNode.path("id").isNumber()  ? inNode.path("id").asLong()  : null;
	                Long outId = outNode.path("id").isNumber() ? outNode.path("id").asLong() : null;
	                if (inId != null) {
	                    out.add(new TransferRecord(date, inId, outId));
	                }
	            }
	        }
	        return out;
	    } catch (Exception e) {
	        System.err.printf("Excepción llamando transfers para indexId=%d: %s%n", indexId, e.getMessage());
	        return Collections.emptyList();
	    }
	}
	
	private void saveTransfer(Long player, Long in, Long out, String actualSeason) throws PlayerDataDBException {
		Transfer t = new Transfer();
		t.setPlayer(player);
		t.setIn(in);
		t.setOut(out);
		t.setSeason(actualSeason);
		pdClient.saveTransfer(t);
	}
	private void saveDupped(Long id, Long club, String actualSeason) throws PlayerDataDBException{
		DuppedPlayers dupp = new DuppedPlayers();
		dupp.setPlayer(id);
		dupp.setTeam(club);
		dupp.setSeason(actualSeason);
		pdClient.saveDuppedPlayer(dupp);
	}
	
}


