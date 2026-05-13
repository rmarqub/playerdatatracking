package com.playerdatatracking.operations.IndelxalData.predictions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PredictApiClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.ContextualWeightConfig;
import com.playerdatatracking.entities.indexaldata.FixtureContextualAnalysis;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.repositories.indexaldata.ContextualWeightConfigRepository;
import com.playerdatatracking.repositories.indexaldata.FixtureContextualAnalysisRepository;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.ContextualMatchPrediction;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.MatchPrediction;

@Component
public class GetContextualMatchPrediction {


    @Autowired
    private PredictApiClient predictApiClient;

    @Autowired
    private ContextualWeightConfigRepository weightConfigRepository;
    
    @Autowired
    private FixtureContextualAnalysisRepository analysisRepository;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    public GenericResponse<ContextualMatchPrediction> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<ContextualMatchPrediction> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        // 1. Try Python for a fresh base prediction (non-blocking failure)
        MatchPrediction base = null;
        try {
            base = predictApiClient.predict(request.getId());
        } catch (Exception e) {
            System.err.println("[GetContextualMatchPrediction] Python predict failed, intentando con base almacenada: " + e.getMessage());
        }

        // 2. Read analysis from DB (always fresh, regardless of Python status)
        Optional<FixtureContextualAnalysis> optAnalysis =
                analysisRepository.findByFixtureId(request.getId());

        // 3. Resolve base probabilities: Python first, stored snapshot as fallback
        ContextualMatchPrediction result = new ContextualMatchPrediction();
        result.setFixtureId(request.getId());

        double bH, bD, bA;

        if (base != null) {
            result.setHomeTeam(base.getHomeTeam());
            result.setAwayTeam(base.getAwayTeam());
            result.setOverUnder25(base.getOverUnder25());
            result.setBtts(base.getBtts());
            result.setWarnings(base.getWarnings());
            MatchPrediction.Result1x2 r = base.getResult1x2();
            bH = r != null && r.getHomeWin() != null ? r.getHomeWin() : 0.334;
            bD = r != null && r.getDraw()    != null ? r.getDraw()    : 0.333;
            bA = r != null && r.getAwayWin() != null ? r.getAwayWin() : 0.333;
        } else {
            Float storedH = optAnalysis.map(FixtureContextualAnalysis::getBaseHomeWin).orElse(null);
            Float storedD = optAnalysis.map(FixtureContextualAnalysis::getBaseDraw).orElse(null);
            Float storedA = optAnalysis.map(FixtureContextualAnalysis::getBaseAwayWin).orElse(null);
            if (storedH == null || storedD == null || storedA == null)
                throw new RuntimeException("Servicio de predicción no disponible y no hay predicción base almacenada para este partido");
            bH = storedH;
            bD = storedD;
            bA = storedA;
            result.setWarnings(Collections.singletonList("Usando predicción base almacenada (servicio de predicción no disponible)"));
        }

        result.setBaseHomeWin(bH);
        result.setBaseDraw(bD);
        result.setBaseAwayWin(bA);

        // 4. Apply contextual delta from fresh DB values
        if (optAnalysis.isEmpty()) {
            result.setAnalysisFound(false);
            result.setNetDelta(0.0);
            result.setAdjHomeWin(bH);
            result.setAdjDraw(bD);
            result.setAdjAwayWin(bA);
            result.setAdjPredicted(classify(bH, bD, bA));
            result.setAdjConfidence(confidence(bH, bD, bA));
        } else {
            FixtureContextualAnalysis a = optAnalysis.get();
            
            ContextualWeightConfig weights = getActiveWeights();
            
            double delta = computeDelta(a, weights)
                    + computeUnavailableImpact(
                            request.getId(),
                            a.getHomeUnavailablePlayers(),
                            a.getAwayUnavailablePlayers(),
                            weights
                    );

            double[] adj = applyBlend(bH, bD, bA, delta);
            result.setAnalysisFound(true);
            result.setNetDelta(delta);
            result.setAdjHomeWin(adj[0]);
            result.setAdjDraw(adj[1]);
            result.setAdjAwayWin(adj[2]);
            result.setAdjPredicted(classify(adj[0], adj[1], adj[2]));
            result.setAdjConfidence(confidence(adj[0], adj[1], adj[2]));
        }

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(result);
        return response;
    }

    // ---- Unavailable player impact -------------------------------------------

    // Percentile assumed for a player with no data in player_season_percentiles.
    // 65 → above-average player: each absence contributes (65-50)/100 = 0.15 to their team's impact.
    private static final double DEFAULT_PLAYER_PCT = 65.0;

    private double computeUnavailableImpact(
            Long fixtureId,
            String homeCsv,
            String awayCsv,
            ContextualWeightConfig weights
    ) {
        List<Long> homeIds = parseCsvIds(homeCsv);
        List<Long> awayIds = parseCsvIds(awayCsv);

        if (homeIds.isEmpty() && awayIds.isEmpty()) {
            return 0.0;
        }

        Map<Long, Double> pcts = tryQueryPercentiles(fixtureId, homeIds, awayIds);

        double homeImpact = homeIds.stream()
                .mapToDouble(id -> (pcts.getOrDefault(id, DEFAULT_PLAYER_PCT) - 50.0) / 100.0)
                .sum();

        double awayImpact = awayIds.stream()
                .mapToDouble(id -> (pcts.getOrDefault(id, DEFAULT_PLAYER_PCT) - 50.0) / 100.0)
                .sum();

        double wUnavail = safe(weights.getWUnavail(), 0.10);

        return wUnavail * (awayImpact - homeImpact);
    }

    private Map<Long, Double> tryQueryPercentiles(Long fixtureId, List<Long> homeIds, List<Long> awayIds) {
        try {
            List<Map<String, Object>> fixtureRows = namedJdbc.queryForList(
                    "SELECT league_id, season, match_date FROM fixture WHERE id = :fid",
                    Collections.singletonMap("fid", fixtureId));
            if (fixtureRows.isEmpty()) return Collections.emptyMap();

            Map<String, Object> fix = fixtureRows.get(0);
            long      leagueId  = ((Number) fix.get("league_id")).longValue();
            int       season    = ((Number) fix.get("season")).intValue();
            Timestamp matchDate = toTimestamp(fix.get("match_date"));
            if (matchDate == null) return Collections.emptyMap();

            List<Long> allIds = new ArrayList<>(homeIds);
            allIds.addAll(awayIds);
            return queryPercentiles(allIds, leagueId, season, matchDate);
        } catch (Exception e) {
            System.err.println("[computeUnavailableImpact] fixture " + fixtureId
                    + " — no se pudo obtener percentiles, se usa valor por defecto: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<Long, Double> queryPercentiles(List<Long> playerIds, long leagueId, int season, Timestamp matchDate) {
        if (playerIds.isEmpty()) return Collections.emptyMap();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids",       playerIds)
                .addValue("leagueId",  leagueId)
                .addValue("season",    season)
                .addValue("matchDate", matchDate);

        String sql = "SELECT DISTINCT ON (player_id) player_id, avg_rating_pct " +
                     "FROM player_season_percentiles " +
                     "WHERE player_id IN (:ids) AND league_id = :leagueId " +
                     "  AND season = :season AND as_of_date <= :matchDate " +
                     "ORDER BY player_id, as_of_date DESC";

        Map<Long, Double> result = new HashMap<>();
        namedJdbc.query(sql, params, (RowCallbackHandler) rs ->
                result.put(rs.getLong("player_id"), rs.getDouble("avg_rating_pct")));
        return result;
    }

    private List<Long> parseCsvIds(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        for (String s : csv.split(",")) {
            try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    private Timestamp toTimestamp(Object val) {
        if (val instanceof Timestamp t) return t;
        if (val instanceof java.util.Date d) return new Timestamp(d.getTime());
        if (val instanceof java.time.OffsetDateTime odt)
            return Timestamp.from(odt.toInstant());
        if (val instanceof java.time.LocalDateTime ldt)
            return Timestamp.valueOf(ldt);
        return null;
    }

    // ---- Blend logic ---------------------------------------------------------

    private double computeDelta(FixtureContextualAnalysis a, ContextualWeightConfig w) {
        return safe(w.getWForma(), 0.12)
                * diff(a.getHomeCurrentForm(), a.getAwayCurrentForm())

             + safe(w.getWNeeds(), 0.10)
                * diff(a.getHomeTeamNeeds(), a.getAwayTeamNeeds())

             + safe(w.getWDef(), 0.07)
                * diff(a.getHomeDefensiveBlock(), a.getAwayDefensiveBlock())

             + safe(w.getWOff(), 0.07)
                * diff(a.getHomeOffensiveRhythm(), a.getAwayOffensiveRhythm())

             + safe(w.getWFatigue(), 0.06)
                * diff(a.getAwayFatigue(), a.getHomeFatigue())

             + safe(w.getWSetPieces(), 0.06)
                * diff(a.getHomeSetPieces(), a.getAwaySetPieces())

             + safe(w.getWAtm(), 0.03)
                * diff(a.getHomeStadiumAtmosphere(), a.getAwayStadiumAtmosphere());
    }
    private double safe(Double value, double defaultValue) {
        return value != null ? value : defaultValue;
    }
    private double diff(Integer home, Integer away) {
        return (home != null ? home : 3) - (away != null ? away : 3);
    }

    private double[] applyBlend(double bH, double bD, double bA, double delta) {
        bH = Math.max(0.001, Math.min(0.999, bH));
        bD = Math.max(0.001, Math.min(0.999, bD));
        bA = Math.max(0.001, Math.min(0.999, bA));

        double logH = Math.log(bH) + delta;
        double logD = Math.log(bD);          // draw not shifted by team asymmetry
        double logA = Math.log(bA) - delta;

        double maxLog = Math.max(logH, Math.max(logD, logA));
        double sumExp = Math.exp(logH - maxLog) + Math.exp(logD - maxLog) + Math.exp(logA - maxLog);

        return new double[]{
            Math.exp(logH - maxLog) / sumExp,
            Math.exp(logD - maxLog) / sumExp,
            Math.exp(logA - maxLog) / sumExp
        };
    }

    private String classify(double h, double d, double a) {
        if (h >= d && h >= a) return "home_win";
        if (a >= h && a >= d) return "away_win";
        return "draw";
    }

    private double confidence(double h, double d, double a) {
        double[] s = {h, d, a};
        java.util.Arrays.sort(s);
        return s[2] - s[1];
    }
    
    private ContextualWeightConfig getActiveWeights() {
        return weightConfigRepository.findTopByOrderByIdDesc()
                .orElseGet(this::getDefaultWeights);
    }
    
    private ContextualWeightConfig getDefaultWeights() {
        ContextualWeightConfig cfg = new ContextualWeightConfig();

        cfg.setWForma(0.12);
        cfg.setWNeeds(0.10);
        cfg.setWDef(0.07);
        cfg.setWOff(0.07);
        cfg.setWFatigue(0.06);
        cfg.setWSetPieces(0.06);
        cfg.setWAtm(0.03);
        cfg.setWUnavail(0.10);

        return cfg;
    }
}
