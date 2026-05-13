package com.playerdatatracking.operations.IndelxalData.indexal;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.ConfigParams;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiFootballRequestException;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class IngestFixtures {

    private static final Logger log = LoggerFactory.getLogger(IngestFixtures.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private PlayerDataClient pdClient;

    @Autowired
    private KeysManagement keyMethods;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenericResponse<Fixture> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<Fixture> response = new GenericResponse<>();
        ApiFootballClient client = new ApiFootballClient();

        boolean purge = "true".equalsIgnoreCase(request.getPurgeBeforeRun());

        Integer season;
        String actualSeason = request.getSeason();
		String requestedSeason = (actualSeason != null && !actualSeason.trim().isEmpty())
				? actualSeason.trim()
				: pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();
        if (requestedSeason != null && !requestedSeason.trim().isEmpty()) {
            season = Integer.parseInt(requestedSeason.trim());
        } else {
            ConfigParams seasonParam = pdClient.getParam("ACTUAL_SEASON");
            if (seasonParam == null)
                throw new IllegalArgumentException("No se encontró el parámetro ACTUAL_SEASON en config_params");
            season = Integer.parseInt(seasonParam.getValue());
        }

        List<Torneo> studiedLeagues = pdClient.getStudiedLeagues();
        if (studiedLeagues == null || studiedLeagues.isEmpty())
            throw new IllegalArgumentException("No hay ligas marcadas como studied");

        Keys apiKey = keyMethods.nextKey();
        if (apiKey == null)
            throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
        if (!keyMethods.checkReadiness(apiKey))
            throw new ApiKeyManagementException("la key disponible no está lista para usarse");

        int totalIngested = 0;
        Set<Long> ensuredClubs = new HashSet<>();

        log.info("=== IngestFixtures START — temporada {} | ligas: {} | purge: {} ===",
                season, studiedLeagues.size(), purge);

        for (Torneo torneo : studiedLeagues) {
            Integer leagueId = torneo.getId().intValue();
            String leagueName = torneo.getName() != null ? torneo.getName() : String.valueOf(leagueId);

            log.info("--- Liga {} ({}) ---", leagueId, leagueName);

            if (purge)
                pdClient.deleteFixturesByLeagueAndSeason(leagueId, season);

            String jsonBody = client.getFixturesByLeagueAndSeasonRaw(apiKey.getValor(), torneo.getId(), season);
            keyMethods.useKey(apiKey);
            Methods.sleep(180);

            JsonNode root = objectMapper.readTree(jsonBody);

            JsonNode errors = root.path("errors");
            if (errors.isObject() && errors.size() > 0)
                throw new ApiFootballRequestException("API devolvió errores para liga " + leagueId + ": " + errors.toString());

            JsonNode responseArray = root.path("response");
            if (!responseArray.isArray()) {
                log.warn("Liga {} — respuesta de API no es array, se omite", leagueId);
                continue;
            }

            List<Fixture> leagueFixtures = new ArrayList<>();
            for (JsonNode item : responseArray) {
                Fixture f = mapToFixture(item);
                if (f == null) continue;

                String leagueCountry = textOrNull(item.path("league"), "country");
                ensureClubExists(f.getHomeTeamId(), f.getHomeTeamName(), leagueCountry, ensuredClubs);
                ensureClubExists(f.getAwayTeamId(), f.getAwayTeamName(), leagueCountry, ensuredClubs);

                leagueFixtures.add(f);
            }

            log.info("Liga {} — {} fixtures recibidos de la API", leagueId, leagueFixtures.size());

            if (purge) {
                log.info("Liga {} — modo PURGE: insertando {} fixtures", leagueId, leagueFixtures.size());
                logFixtures("  INSERT", leagueFixtures);
                pdClient.saveAllFixtures(leagueFixtures);
            } else {
                Set<Long> existingIds = new HashSet<>(pdClient.getExistingFixtureIds(leagueId, season));
                List<Fixture> toInsert = leagueFixtures.stream()
                    .filter(f -> !existingIds.contains(f.getId()))
                    .collect(Collectors.toList());
                List<Fixture> toUpdate = leagueFixtures.stream()
                    .filter(f -> existingIds.contains(f.getId()))
                    .collect(Collectors.toList());

                log.info("Liga {} — toInsert: {} | toUpdate: {}", leagueId, toInsert.size(), toUpdate.size());

                if (!toInsert.isEmpty()) {
                    log.info("Liga {} — fixtures a INSERTAR:", leagueId);
                    logFixtures("  +INSERT", toInsert);
                    pdClient.saveAllFixtures(toInsert);
                }

                if (!toUpdate.isEmpty()) {
                    log.info("Liga {} — fixtures a ACTUALIZAR:", leagueId);
                    logFixtures("  ~UPDATE", toUpdate);
                    for (Fixture f : toUpdate)
                        pdClient.updateFixtureStatus(f);
                }
            }
            totalIngested += leagueFixtures.size();
            log.info("Liga {} — completada. Total acumulado: {} fixtures", leagueId, totalIngested);
        }

        log.info("=== IngestFixtures END — {} fixtures procesados en {} ligas ===",
                totalIngested, studiedLeagues.size());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("Ingestados " + totalIngested + " partidos de " + studiedLeagues.size() + " ligas (temporada " + season + ")");
        return response;
    }

    private Fixture mapToFixture(JsonNode item) {
        Fixture f = new Fixture();

        JsonNode fixtureNode = item.path("fixture");
        JsonNode leagueNode = item.path("league");
        JsonNode teamsNode = item.path("teams");
        JsonNode goalsNode = item.path("goals");
        JsonNode scoreNode = item.path("score");

        f.setId(fixtureNode.path("id").asLong());

        String dateStr = fixtureNode.path("date").asText(null);
        if (dateStr != null && !dateStr.isEmpty())
            f.setMatchDate(OffsetDateTime.parse(dateStr));

        JsonNode tsNode = fixtureNode.path("timestamp");
        f.setMatchTimestamp(tsNode.isNull() || tsNode.isMissingNode() ? null : tsNode.asLong());

        f.setReferee(textOrNull(fixtureNode, "referee"));

        JsonNode statusNode = fixtureNode.path("status");
        f.setStatusShort(statusNode.path("short").asText("NS"));
        f.setStatusLong(textOrNull(statusNode, "long"));
        f.setStatusElapsed(intOrNull(statusNode, "elapsed"));
        f.setStatusExtra(intOrNull(statusNode, "extra"));

        JsonNode venueNode = fixtureNode.path("venue");
        f.setVenueId(intOrNull(venueNode, "id"));
        f.setVenueName(textOrNull(venueNode, "name"));
        f.setVenueCity(textOrNull(venueNode, "city"));

        f.setLeagueId(leagueNode.path("id").asInt());
        f.setLeagueName(textOrNull(leagueNode, "name"));
        f.setSeason(leagueNode.path("season").asInt());
        f.setRound(textOrNull(leagueNode, "round"));

        JsonNode homeNode = teamsNode.path("home");
        JsonNode awayNode = teamsNode.path("away");
        f.setHomeTeamId(homeNode.path("id").asLong());
        f.setHomeTeamName(textOrNull(homeNode, "name"));
        f.setAwayTeamId(awayNode.path("id").asLong());
        f.setAwayTeamName(textOrNull(awayNode, "name"));

        f.setGoalsHome(intOrNull(goalsNode, "home"));
        f.setGoalsAway(intOrNull(goalsNode, "away"));

        f.setScoreHtHome(intOrNull(scoreNode.path("halftime"), "home"));
        f.setScoreHtAway(intOrNull(scoreNode.path("halftime"), "away"));
        f.setScoreFtHome(intOrNull(scoreNode.path("fulltime"), "home"));
        f.setScoreFtAway(intOrNull(scoreNode.path("fulltime"), "away"));
        f.setScoreEtHome(intOrNull(scoreNode.path("extratime"), "home"));
        f.setScoreEtAway(intOrNull(scoreNode.path("extratime"), "away"));
        f.setScorePenHome(intOrNull(scoreNode.path("penalty"), "home"));
        f.setScorePenAway(intOrNull(scoreNode.path("penalty"), "away"));

        return f;
    }

    private void ensureClubExists(Long teamId, String teamName, String countryName, Set<Long> ensuredClubs) throws PlayerDataDBException {
        if (ensuredClubs.contains(teamId)) return;
        Club existing = pdClient.findClub(teamId);
        if (existing == null) {
            Club club = new Club();
            club.setId(teamId);
            club.setNombre(teamName);
            club.setIdPais(resolveCountryId(countryName));
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

    private void logFixtures(String prefix, List<Fixture> fixtures) {
        for (Fixture f : fixtures) {
            String date = f.getMatchDate() != null ? f.getMatchDate().format(DATE_FMT) : "fecha?";
            String score = (f.getGoalsHome() != null && f.getGoalsAway() != null)
                    ? " [" + f.getGoalsHome() + "-" + f.getGoalsAway() + "]"
                    : "";
            log.info("{} id={} | {} vs {} | {} | status={}{}", prefix,
                    f.getId(), f.getHomeTeamName(), f.getAwayTeamName(),
                    date, f.getStatusShort(), score);
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
}
