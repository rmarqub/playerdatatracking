package com.playerdatatracking.operations.IndelxalData.indexal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.FixtureEvent;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class IngestFixtureEvents {

    @Autowired
    private PlayerDataClient pdClient;

    @Autowired
    private KeysManagement keyMethods;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Methods methods = new Methods();

    public GenericResponse<FixtureEvent> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<FixtureEvent> response = new GenericResponse<>();
        ApiFootballClient client = new ApiFootballClient();

        boolean purge = Boolean.TRUE.equals(request.getPurgeBeforeRun());

        List<Long> fixtureIds = pdClient.getAllFixtureIds();
        if (fixtureIds == null || fixtureIds.isEmpty())
            throw new IllegalArgumentException("No hay fixtures almacenados en la base de datos");

        Keys apiKey = keyMethods.nextKey();
        if (apiKey == null)
            throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
        if (!keyMethods.checkReadiness(apiKey))
            throw new ApiKeyManagementException("la key disponible no está lista para usarse");

        List<Long> idsToProcess = purge ? fixtureIds : pdClient.getFixtureIdsWithoutEvents();

        Set<Long> ensuredClubs = new HashSet<>();
        int totalEvents = 0;
        int processed = 0;
        int total = idsToProcess.size();

        System.out.println("Fixtures a procesar: " + total + " (de " + fixtureIds.size() + " totales).");

        for (Long fixtureId : idsToProcess) {
            processed++;

            if (purge)
                pdClient.deleteEventsByFixtureId(fixtureId);

            String jsonBody = fetchWithRetry(client, apiKey, fixtureId, processed, total);
            keyMethods.useKey(apiKey);

            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode responseArray = root.path("response");
            JsonNode leaguePath = responseArray.path("league");
            String countryName = leaguePath.asText("country");

            if (!responseArray.isArray() || responseArray.size() == 0) {
                System.out.println("[" + processed + "/" + total + "] Fixture " + fixtureId + ": sin eventos.");
                pdClient.markFixtureEventsStored(fixtureId);
                methods.sleep(180);
                continue;
            }

            List<FixtureEvent> events = new ArrayList<>();
            for (JsonNode item : responseArray) {
                Long teamId = longOrNull(item.path("team"), "id");
                String teamName = textOrNull(item.path("team"), "name");
                if (teamId != null)
                    ensureClubExists(teamId, teamName, ensuredClubs, countryName);

                FixtureEvent e = mapToEvent(item);
                if (e != null) events.add(e);
            }

            pdClient.saveAllFixtureEvents(fixtureId, events);
            pdClient.markFixtureEventsStored(fixtureId);
            totalEvents += events.size();
            System.out.println("[" + processed + "/" + total + "] Fixture " + fixtureId + ": "
                    + events.size() + " eventos guardados. Acumulado: " + totalEvents);
            methods.sleep(180);
        }

        System.out.println("Ingesta completada. Total eventos guardados: " + totalEvents);
        response.setCODE(Constants.CODE_OK);
        response.setDescription("Ingestados " + totalEvents + " eventos de " + processed + " fixtures procesados");
        return response;
    }

    private String fetchWithRetry(ApiFootballClient client, Keys apiKey, Long fixtureId, int processed, int total) throws Exception {
        String jsonBody = client.getFixtureEventsRaw(apiKey.getValor(), fixtureId);
        boolean rateLimited = methods.checkGoodCallGeneric(jsonBody);
        if (rateLimited) {
            System.out.println("[" + processed + "/" + total + "] Reintentando fixture " + fixtureId + " tras rate limit...");
            methods.sleep(180);
            jsonBody = client.getFixtureEventsRaw(apiKey.getValor(), fixtureId);
        }
        return jsonBody;
    }

    private FixtureEvent mapToEvent(JsonNode item) {
        String type = textOrNull(item, "type");
        if (type == null) return null;

        FixtureEvent e = new FixtureEvent();

        JsonNode timeNode = item.path("time");
        e.setTimeElapsed(intOrNull(timeNode, "elapsed"));
        e.setTimeExtra(intOrNull(timeNode, "extra"));

        e.setTeamId(longOrNull(item.path("team"), "id"));

        JsonNode playerNode = item.path("player");
        e.setPlayerId(longOrNull(playerNode, "id"));
        e.setPlayerName(textOrNull(playerNode, "name"));

        JsonNode assistNode = item.path("assist");
        e.setAssistId(longOrNull(assistNode, "id"));
        e.setAssistName(textOrNull(assistNode, "name"));

        e.setEventType(type);
        e.setEventDetail(textOrNull(item, "detail"));
        e.setComments(textOrNull(item, "comments"));

        return e;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isMissingNode() || n.isNull()) ? null : n.asText();
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isMissingNode() || n.isNull()) ? null : n.asInt();
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isMissingNode() || n.isNull()) ? null : n.asLong();
    }

    private void ensureClubExists(Long teamId, String teamName, Set<Long> ensuredClubs, String country) throws PlayerDataDBException {
        if (ensuredClubs.contains(teamId)) return;
        if (pdClient.findClub(teamId) == null) {
            Club club = new Club();
            club.setId(teamId);
            club.setNombre(teamName);
            club.setIdPais(resolveCountryId(country));
            pdClient.saveClub(club);
        }
        ensuredClubs.add(teamId);
    }
    
    private int resolveCountryId(String countryName) {
        if (countryName == null || countryName.isEmpty()) return 0;
        try {
            Pais pais = pdClient.findCountry(countryName);
            return (pais != null) ? pais.getId().intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
