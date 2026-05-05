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
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.FixtureTeamStats;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class IngestFixtureTeamStats {

    @Autowired
    private PlayerDataClient pdClient;

    @Autowired
    private KeysManagement keyMethods;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Methods methods = new Methods();

    public GenericResponse<FixtureTeamStats> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<FixtureTeamStats> response = new GenericResponse<>();
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

        List<Long> idsToProcess = purge ? fixtureIds : pdClient.getFixtureIdsWithoutTeamStats();

        Set<Long> ensuredClubs = new HashSet<>();
        int totalStats = 0;
        int processed = 0;
        int total = idsToProcess.size();

        System.out.println("Fixtures a procesar (team stats): " + total + " (de " + fixtureIds.size() + " totales).");

        for (Long fixtureId : idsToProcess) {
            processed++;

            if (purge)
                pdClient.deleteTeamStatsByFixtureId(fixtureId);

            String jsonBody = fetchWithRetry(client, apiKey, fixtureId, processed, total);
            keyMethods.useKey(apiKey);

            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode responseArray = root.path("response");

            if (!responseArray.isArray() || responseArray.size() == 0) {
                System.out.println("[" + processed + "/" + total + "] Fixture " + fixtureId + ": sin team stats.");
                pdClient.markFixtureMatchStored(fixtureId);
                methods.sleep(200);
                continue;
            }

            List<FixtureTeamStats> statsList = new ArrayList<>();
            for (JsonNode teamNode : responseArray) {
                Long teamId = longOrNull(teamNode.path("team"), "id");
                String teamName = textOrNull(teamNode.path("team"), "name");
                if (teamId != null)
                    ensureClubExists(teamId, teamName, ensuredClubs);

                FixtureTeamStats stats = mapToTeamStats(teamNode, teamId);
                if (stats != null) statsList.add(stats);
            }

            pdClient.saveAllFixtureTeamStats(fixtureId, statsList);
            pdClient.markFixtureMatchStored(fixtureId);
            totalStats += statsList.size();
            System.out.println("[" + processed + "/" + total + "] Fixture " + fixtureId + ": "
                    + statsList.size() + " team stats guardadas. Acumulado: " + totalStats);
            methods.sleep(200);
        }

        System.out.println("Ingesta completada. Total team stats guardadas: " + totalStats);
        response.setCODE(Constants.CODE_OK);
        response.setDescription("Ingestadas " + totalStats + " team stats de " + processed + " fixtures procesados");
        return response;
    }

    private String fetchWithRetry(ApiFootballClient client, Keys apiKey, Long fixtureId, int processed, int total) throws Exception {
        String jsonBody = client.getFixtureTeamStatsRaw(apiKey.getValor(), fixtureId);
        boolean rateLimited = methods.checkGoodCallGeneric(jsonBody);
        if (rateLimited) {
            System.out.println("[" + processed + "/" + total + "] Reintentando fixture " + fixtureId + " tras rate limit...");
            jsonBody = client.getFixtureTeamStatsRaw(apiKey.getValor(), fixtureId);
        }
        return jsonBody;
    }

    private FixtureTeamStats mapToTeamStats(JsonNode teamNode, Long teamId) {
        if (teamId == null) return null;

        FixtureTeamStats s = new FixtureTeamStats();
        s.setTeamId(teamId);

        JsonNode statsArray = teamNode.path("statistics");
        if (!statsArray.isArray()) return s;

        for (JsonNode stat : statsArray) {
            String type = textOrNull(stat, "type");
            JsonNode valueNode = stat.path("value");
            if (type == null || valueNode.isNull() || valueNode.isMissingNode()) continue;

            switch (type) {
                case "Shots on Goal"      -> s.setShotsOnGoal(valueNode.asInt());
                case "Shots off Goal"     -> s.setShotsOffGoal(valueNode.asInt());
                case "Total Shots"        -> s.setShotsTotal(valueNode.asInt());
                case "Blocked Shots"      -> s.setShotsBlocked(valueNode.asInt());
                case "Shots insidebox"    -> s.setShotsInsideBox(valueNode.asInt());
                case "Shots outsidebox"   -> s.setShotsOutsideBox(valueNode.asInt());
                case "Fouls"              -> s.setFouls(valueNode.asInt());
                case "Corner Kicks"       -> s.setCornerKicks(valueNode.asInt());
                case "Offsides"           -> s.setOffsides(valueNode.asInt());
                case "Ball Possession"    -> s.setBallPossession(parsePct(valueNode.asText()));
                case "Yellow Cards"       -> s.setYellowCards(valueNode.asInt());
                case "Red Cards"          -> s.setRedCards(valueNode.asInt());
                case "Goalkeeper Saves"   -> s.setGoalkeeperSaves(valueNode.asInt());
                case "Total passes"       -> s.setTotalPasses(valueNode.asInt());
                case "Passes accurate"    -> s.setPassesAccurate(valueNode.asInt());
                case "Passes %"           -> s.setPassesPct(parsePct(valueNode.asText()));
                case "expected_goals"     -> s.setExpectedGoals(parseBigDecimal(valueNode.asText()));
                case "goals_prevented"    -> s.setGoalsPrevented(parseBigDecimal(valueNode.asText()));
            }
        }
        return s;
    }

    private BigDecimal parsePct(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return new BigDecimal(val.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
