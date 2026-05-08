package com.playerdatatracking.operations.IndelxalData;

import java.math.BigDecimal;
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
import com.playerdatatracking.clients.PredictApiClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.FixturePlayerStats;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class IngestFixturePlayerStats {

    @Autowired
    private PlayerDataClient pdClient;

    @Autowired
    private PredictApiClient predictApiClient;

    @Autowired
    private KeysManagement keyMethods;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Methods methods = new Methods();

    public GenericResponse<FixturePlayerStats> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<FixturePlayerStats> response = new GenericResponse<>();
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

        List<Long> idsToProcess = purge ? fixtureIds : pdClient.getFixtureIdsWithoutPlayerStats();

        Set<Long> ensuredClubs = new HashSet<>();
        int totalStats = 0;
        int processed = 0;
        int total = idsToProcess.size();

        System.out.println("Fixtures a procesar (player stats): " + total + " (de " + fixtureIds.size() + " totales).");

        for (Long fixtureId : idsToProcess) {
            processed++;

            if (purge)
                pdClient.deletePlayerStatsByFixtureId(fixtureId);

            String jsonBody = fetchWithRetry(client, apiKey, fixtureId, processed, total);
            keyMethods.useKey(apiKey);

            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode responseArray = root.path("response");

            if (!responseArray.isArray() || responseArray.size() == 0) {
                System.out.println("[" + processed + "/" + total + "] Fixture " + fixtureId + ": sin player stats.");
                pdClient.markFixtureStatsStored(fixtureId);
                methods.sleep(180);
                continue;
            }

            List<FixturePlayerStats> statsList = new ArrayList<>();
            for (JsonNode teamNode : responseArray) {
                Long teamId = longOrNull(teamNode.path("team"), "id");
                String teamName = textOrNull(teamNode.path("team"), "name");
                if (teamId != null)
                    ensureClubExists(teamId, teamName, ensuredClubs);

                JsonNode playersArray = teamNode.path("players");
                if (!playersArray.isArray()) continue;

                for (JsonNode playerNode : playersArray) {
                    Long playerId = longOrNull(playerNode.path("player"), "id");
                    String playerName = textOrNull(playerNode.path("player"), "name");
                    if (playerId == null) continue;

                    JsonNode statsArray = playerNode.path("statistics");
                    if (!statsArray.isArray() || statsArray.size() == 0) continue;

                    FixturePlayerStats stats = mapToPlayerStats(statsArray.get(0), playerId, playerName, teamId);
                    if (stats != null) statsList.add(stats);
                }
            }

            pdClient.saveAllFixturePlayerStats(fixtureId, statsList);
            pdClient.markFixtureStatsStored(fixtureId);
            totalStats += statsList.size();
            System.out.println("[" + processed + "/" + total + "] Fixture " + fixtureId + ": "
                    + statsList.size() + " player stats guardadas. Acumulado: " + totalStats);
            methods.sleep(200);
        }

        System.out.println("Ingesta completada. Total player stats guardadas: " + totalStats);
        predictApiClient.refreshPercentiles();
        response.setCODE(Constants.CODE_OK);
        response.setDescription("Ingestadas " + totalStats + " player stats de " + processed + " fixtures procesados");
        return response;
    }

    private String fetchWithRetry(ApiFootballClient client, Keys apiKey, Long fixtureId, int processed, int total) throws Exception {
        String jsonBody = client.getFixturePlayerStatsRaw(apiKey.getValor(), fixtureId);
        boolean rateLimited = methods.checkGoodCallGeneric(jsonBody);
        if (rateLimited) {
            System.out.println("[" + processed + "/" + total + "] Reintentando fixture " + fixtureId + " tras rate limit...");
            methods.sleep(180);
            jsonBody = client.getFixturePlayerStatsRaw(apiKey.getValor(), fixtureId);
        }
        return jsonBody;
    }

    private FixturePlayerStats mapToPlayerStats(JsonNode stat, Long playerId, String playerName, Long teamId) {
        FixturePlayerStats s = new FixturePlayerStats();
        s.setPlayerId(playerId);
        s.setPlayerName(playerName);
        s.setTeamId(teamId);

        JsonNode games = stat.path("games");
        s.setMinutesPlayed(intOrNull(games, "minutes"));
        s.setPosition(textOrNull(games, "position"));
        s.setRating(parseBigDecimal(textOrNull(games, "rating")));
        s.setCaptain(boolOrFalse(games, "captain"));
        s.setSubstitute(boolOrFalse(games, "substitute"));

        s.setOffsides(intOrNull(stat, "offsides"));

        JsonNode shots = stat.path("shots");
        s.setShotsTotal(intOrNull(shots, "total"));
        s.setShotsOn(intOrNull(shots, "on"));

        JsonNode goals = stat.path("goals");
        s.setGoalsScored(intOrNull(goals, "total"));
        s.setGoalsConceded(intOrNull(goals, "conceded"));
        s.setAssists(intOrNull(goals, "assists"));
        s.setSaves(intOrNull(goals, "saves"));

        JsonNode passes = stat.path("passes");
        s.setPassesTotal(intOrNull(passes, "total"));
        s.setPassesKey(intOrNull(passes, "key"));
        s.setPassesAccuracy(parseBigDecimal(textOrNull(passes, "accuracy")));

        JsonNode tackles = stat.path("tackles");
        s.setTacklesTotal(intOrNull(tackles, "total"));
        s.setTacklesBlocks(intOrNull(tackles, "blocks"));
        s.setInterceptions(intOrNull(tackles, "interceptions"));

        JsonNode duels = stat.path("duels");
        s.setDuelsTotal(intOrNull(duels, "total"));
        s.setDuelsWon(intOrNull(duels, "won"));

        JsonNode dribbles = stat.path("dribbles");
        s.setDribblesAtt(intOrNull(dribbles, "attempts"));
        s.setDribblesSuc(intOrNull(dribbles, "success"));
        s.setDribblesPast(intOrNull(dribbles, "past"));

        JsonNode fouls = stat.path("fouls");
        s.setFoulsDrawn(intOrNull(fouls, "drawn"));
        s.setFoulsCommitted(intOrNull(fouls, "committed"));

        JsonNode cards = stat.path("cards");
        s.setYellowCards(intOrNull(cards, "yellow"));
        s.setRedCards(intOrNull(cards, "red"));
        s.setYellowRedCards(intOrNull(cards, "yellowred"));

        JsonNode penalty = stat.path("penalty");
        s.setPenaltyWon(intOrNull(penalty, "won"));
        s.setPenaltyCommitted(intOrNull(penalty, "commited"));
        s.setPenaltyScored(intOrNull(penalty, "scored"));
        s.setPenaltyMissed(intOrNull(penalty, "missed"));
        s.setPenaltySaved(intOrNull(penalty, "saved"));

        return s;
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            return null;
        }
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

    private Boolean boolOrFalse(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isMissingNode() || n.isNull()) ? false : n.asBoolean();
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
