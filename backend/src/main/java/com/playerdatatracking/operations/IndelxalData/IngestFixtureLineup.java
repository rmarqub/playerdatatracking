package com.playerdatatracking.operations.IndelxalData;

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
import com.playerdatatracking.entities.indexaldata.FixtureLineup;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class IngestFixtureLineup {

    @Autowired
    private PlayerDataClient pdClient;

    @Autowired
    private KeysManagement keyMethods;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Methods methods = new Methods();

    public GenericResponse<FixtureLineup> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<FixtureLineup> response = new GenericResponse<>();
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

        List<Long> idsToProcess = purge ? fixtureIds : pdClient.getFixtureIdsWithoutLineups();

        Set<Long> ensuredClubs = new HashSet<>();
        int totalLineups = 0;
        int processed = 0;
        int total = idsToProcess.size();

        System.out.println("Fixtures a procesar (lineups): " + total + " (de " + fixtureIds.size() + " totales).");

        for (Long fixtureId : idsToProcess) {
            processed++;

            if (purge)
                pdClient.deleteLineupsByFixtureId(fixtureId);

            String jsonBody = fetchWithRetry(client, apiKey, fixtureId, processed, total);
            keyMethods.useKey(apiKey);

            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode responseArray = root.path("response");

            if (!responseArray.isArray() || responseArray.size() == 0) {
                System.out.println("[" + processed + "/" + total + "] Fixture " + fixtureId + ": sin lineup.");
                pdClient.markFixtureLineupStored(fixtureId);
                methods.sleep(200);
                continue;
            }

            List<FixtureLineup> lineupList = new ArrayList<>();
            for (JsonNode teamNode : responseArray) {
                Long teamId = longOrNull(teamNode.path("team"), "id");
                String teamName = textOrNull(teamNode.path("team"), "name");
                String formation = textOrNull(teamNode, "formation");
                Long coachId = longOrNull(teamNode.path("coach"), "id");
                String coachName = textOrNull(teamNode.path("coach"), "name");

                if (teamId != null)
                    ensureClubExists(teamId, teamName, ensuredClubs);

                collectPlayers(teamNode.path("startXI"), false, teamId, formation, coachId, coachName, lineupList);
                collectPlayers(teamNode.path("substitutes"), true, teamId, formation, coachId, coachName, lineupList);
            }

            pdClient.saveAllFixtureLineups(fixtureId, lineupList);
            pdClient.markFixtureLineupStored(fixtureId);
            totalLineups += lineupList.size();
            System.out.println("[" + processed + "/" + total + "] Fixture " + fixtureId + ": "
                    + lineupList.size() + " entradas de lineup guardadas. Acumulado: " + totalLineups);
            methods.sleep(500);
        }

        System.out.println("Ingesta completada. Total entradas de lineup guardadas: " + totalLineups);
        response.setCODE(Constants.CODE_OK);
        response.setDescription("Ingestadas " + totalLineups + " entradas de lineup de " + processed + " fixtures procesados");
        return response;
    }

    private void collectPlayers(JsonNode playersArray, boolean substitute, Long teamId,
                                String formation, Long coachId, String coachName,
                                List<FixtureLineup> lineupList) {
        if (!playersArray.isArray()) return;
        for (JsonNode entry : playersArray) {
            JsonNode playerNode = entry.path("player");
            Long playerId = longOrNull(playerNode, "id");
            if (playerId == null) continue;

            FixtureLineup l = new FixtureLineup();
            l.setTeamId(teamId);
            l.setFormation(formation);
            l.setCoachId(coachId);
            l.setCoachName(coachName);
            l.setPlayerId(playerId);
            l.setPlayerName(textOrNull(playerNode, "name"));
            l.setPlayerNumber(intOrNull(playerNode, "number"));
            l.setPosition(textOrNull(playerNode, "pos"));
            l.setGrid(textOrNull(playerNode, "grid"));
            l.setSubstitute(substitute);
            lineupList.add(l);
        }
    }

    private String fetchWithRetry(ApiFootballClient client, Keys apiKey, Long fixtureId, int processed, int total) throws Exception {
        String jsonBody = client.getFixtureLineupsRaw(apiKey.getValor(), fixtureId);
        boolean rateLimited = methods.checkGoodCallGeneric(jsonBody);
        if (rateLimited) {
            System.out.println("[" + processed + "/" + total + "] Reintentando fixture " + fixtureId + " tras rate limit...");
            jsonBody = client.getFixtureLineupsRaw(apiKey.getValor(), fixtureId);
        }
        return jsonBody;
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

    private void ensureClubExists(Long teamId, String teamName, Set<Long> ensuredClubs) throws PlayerDataDBException {
        if (ensuredClubs.contains(teamId)) return;
        if (pdClient.findClub(teamId) == null) {
            Club club = new Club();
            club.setId(teamId);
            club.setNombre(teamName);
            club.setIdPais(0);
            pdClient.saveClub(club);
        }
        ensuredClubs.add(teamId);
    }
}
