package com.playerdatatracking.operations.IndelxalData;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.TransferRecord;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.IndexTeamPair;
import com.playerdatatracking.responses.GenericResponse;

public class TransferCheckOfPlayers {
	
	PlayerDataClient pdClient;
	private static final HttpClient HTTP = HttpClient.newHttpClient();
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private ApiFootballClient apiClient;
	private KeysManagement keyMethods = new KeysManagement();
	private Environment env;
	private GenericResponse response;

	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
		apiClient = new ApiFootballClient();
	}
	
	public void setEnv(Environment env) {
		this.env = env;
	}

	public GenericResponse ejecutar() throws PlayerDataDBException {
		response = new GenericResponse();
		try {
			List<IndexTeamPair> l = pdClient.getDuppedPlayersWithDiffTeam();
			processList(l);
			response.setCODE(0);
			response.setDescription("OK");
			return response;
		} catch(Exception e) {
			throw e;
		}
	}
	public void processList(List<IndexTeamPair> duppedPlayers) {
		keyMethods = new KeysManagement();
		keyMethods.setEnv(env);
		keyMethods.setPdClient(pdClient);
		Map<Long, List<Long>> checklist = groupTeamsByIndexId(duppedPlayers);
		for (Entry<Long, List<Long>> e : checklist.entrySet()) {
			Long indexId = e.getKey();
			Set<Long> teamIds = e.getValue().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
			if (teamIds.size() < 2) {//nunca deberia entrar por aqui pero no está de mas comprobarlo para no romper la logica
	            continue;
	        }
			List<TransferRecord> transfers = fetchTransfers(indexId);

	        transfers.sort(Comparator.comparing(TransferRecord::getDate).reversed());
	        
	        Optional<TransferRecord> match = transfers.stream()
	                .filter(tr -> tr.getInId() != null && tr.getOutId() != null)
	                .filter(tr -> teamIds.contains(tr.getInId()) && teamIds.contains(tr.getOutId()))
	                .findFirst();

	        if (match.isPresent()) {
	            TransferRecord tr = match.get();
	            Long toDeleteTeamId = tr.getOutId();
	            try {
	                pdClient.deleteIndexedPlayer(indexId, toDeleteTeamId);
	                System.out.printf("Eliminado duplicado: indexId=%d, teamId(out)=%d (mantengo in=%d, fecha=%s)%n",
	                        indexId, toDeleteTeamId, tr.getInId(), tr.getDate());
	            } catch (Exception ex) {
	                System.err.printf("Fallo al eliminar indexId=%d teamId=%d: %s%n",
	                        indexId, toDeleteTeamId, ex.getMessage());
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
	    	}
	    	else
				throw new ApiKeyManagementException("error al intentar usar una key no disponible");
	
	        JsonNode root = MAPPER.readTree(resp.body());
	        JsonNode responseArr = root.path("response");
	        if (!responseArr.isArray() || responseArr.size() == 0) return Collections.emptyList();
	
	        // Hay casos con múltiples bloques en "response" (distintas fuentes); iteramos todos por robustez
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
	                    // Si alguna fecha viniera en otro formato, la ignoramos
	                    continue;
	                }
	
	                JsonNode inNode  = t.path("teams").path("in");
	                JsonNode outNode = t.path("teams").path("out");
	                Long inId  = inNode.path("id").isNumber()  ? inNode.path("id").asLong()  : null;
	                Long outId = outNode.path("id").isNumber() ? outNode.path("id").asLong() : null;
	
	                // Importante: Solo nos quedamos con registros donde in.id no sea null (tu requisito)
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
}


