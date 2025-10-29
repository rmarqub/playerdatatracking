package com.playerdatatracking.operations.IndelxalData;


import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.DuppedPlayers;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.IndexTeamPair;
import com.playerdatatracking.responses.GenericResponse;

public class UpdatePlayersBySquads {

	private PlayerDataClient pdClient;
	private KeysManagement keyMethods = new KeysManagement();
	private Environment env;
	private HttpClient http = HttpClient.newHttpClient();
	boolean isMarketActive;
	boolean useDupped;
	String actualSeason = "";
	
	public void setEnv(Environment env) {
		this.env = env;
	}
	
	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	
	public GenericResponse ejecutar() throws PlayerDataDBException, ApiKeyManagementException {
		GenericResponse response = new GenericResponse();
		http = HttpClient.newHttpClient();
		List<IndexTeamPair> pairs = pdClient.getDuppedPlayersWithDiffTeam();
		Map<Long, List<Long>> porJugador = agruparPorJugador(pairs);
		actualSeason = pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();
		isMarketActive = Methods.isMarketActive(pdClient);
		useDupped = Methods.useDupped(pdClient);
		
		Path logPath = prepararLog();
        int procesados = 0;
        int arreglados = 0;
        int sinResolver = 0;
        
        for (Map.Entry<Long, List<Long>> e : porJugador.entrySet()) {
            Long indexId = e.getKey();
            List<Long> teamIds = e.getValue().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

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
            
            
            procesados++;

            Optional<Long> teamActual = encontrarTeamActualPorSquad(indexId, teamIds);
            if (teamActual.isPresent()) {
                Long keepTeam = teamActual.get();
                // Eliminar todos los demás
                for (Long t : teamIds) {
                    if (!t.equals(keepTeam)) {
                        try {
                            pdClient.deleteIndexedPlayer(indexId, t);
                        } catch (Exception ex) {
                            // si algo falla, lo dejamos trazado pero seguimos con los demás
                            escribirLinea(logPath, String.format(
                                    "ERROR_DELETE | indexId=%d | teamId=%d | msg=%s",
                                    indexId, t, ex.getMessage()));
                        }
                    }
                }
                arreglados++;
            } else {
                // No se encontró el jugador en ninguna plantilla consultada
                sinResolver++;
                escribirLinea(logPath, String.format(
                        "NO_SQUAD_MATCH | indexId=%d | teams=%s",
                        indexId, teamIds));
            }
        }
        System.out.println("Jugadores procesados: " + procesados + ", arreglados: " + arreglados + ", sin resolver: " + sinResolver);
        response.setCODE(0);
        response.setDescription("OK");
        return response;
    }
	
    private Map<Long, List<Long>> agruparPorJugador(List<IndexTeamPair> pairs) {
        if (pairs == null || pairs.isEmpty()) 
        	return Collections.emptyMap();
        return pairs.stream().collect(Collectors.groupingBy(IndexTeamPair::getIndexId, Collectors.mapping(IndexTeamPair::getTeam, Collectors.toList())));
    }
    
    
    private boolean squadContieneJugador(Long teamId, Long playerIndexId) throws PlayerDataDBException, ApiKeyManagementException {
    	keyMethods = new KeysManagement();
		keyMethods.setEnv(env);
		keyMethods.setPdClient(pdClient);
		Keys apiKey = keyMethods.nextKey();
		Methods m = new Methods();
		if (apiKey==null)
			throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
        String url = "https://v3.football.api-sports.io/players/squads?team=" + teamId;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .header("x-apisports-key", apiKey.getValor())
                .build();

        try {
        	HttpResponse<String> resp = null;
        	if (keyMethods.checkReadiness(apiKey)) {
        		resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	    		boolean repeat = m.checkGoodCall(resp.body(), apiKey.getValor(), teamId);
	    		keyMethods.useKey(apiKey);
	    		if (repeat) {
	    			resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		    		keyMethods.useKey(apiKey);
	    		}
	    	}
	    	else
				throw new ApiKeyManagementException("error al intentar usar una key no disponible");
            if (resp.statusCode() != 200) {
                return false;
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(resp.body());
            JsonNode response = root.path("response");
            if (!response.isArray() || response.size() == 0) {
                return false;
            }
            // response[0].players[*].id
            for (JsonNode block : response) {
                JsonNode players = block.path("players");
                if (!players.isArray()) continue;
                for (JsonNode p : players) {
                    long id = p.path("id").asLong(-1);
                    if (id == playerIndexId.longValue()) {
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            // En caso de error, devolvemos false (no encontrado) y seguimos
            return false;
        }
        return false;
    }
    
    private Path prepararLog() {
        try {
            Path dir = Paths.get("logs");
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path p = dir.resolve("check_team_squad_" + ts + ".txt");
            Files.writeString(p, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            return p;
        } catch (Exception e) {
            return Paths.get(System.getProperty("java.io.tmpdir"),
                    "check_team_squad_fallback.txt");
        }
    }
    
    private void escribirLinea(Path log, String line) {
        try {
            Files.writeString(log, line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }
    
    private Optional<Long> encontrarTeamActualPorSquad(Long indexId, List<Long> teamIds) throws PlayerDataDBException, ApiKeyManagementException {
        for (Long teamId : teamIds) {
            if (teamId == null) continue;
            if (squadContieneJugador(teamId, indexId)) {
                return Optional.of(teamId);
            }
        }
        return Optional.empty();
    }
	
}
