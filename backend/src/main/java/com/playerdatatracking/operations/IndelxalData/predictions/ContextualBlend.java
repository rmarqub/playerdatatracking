package com.playerdatatracking.operations.IndelxalData.predictions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.playerdatatracking.entities.indexaldata.ContextualWeightConfig;
import com.playerdatatracking.entities.indexaldata.FixtureContextualAnalysis;

/**
 * Single source of truth for the contextual blend formula.
 * All operations that compute adjusted predictions must use this component
 * to guarantee consistency between the fixture page and history/regeneration.
 */
@Component
public class ContextualBlend {

    private static final double DEFAULT_PLAYER_PCT = 65.0;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Full adjusted prediction. Returns [adjH, adjD, adjA], or null if base probs are missing.
     */
    public double[] computeAdj(Long fixtureId, FixtureContextualAnalysis a, ContextualWeightConfig w) {
        Float bHF = a.getBaseHomeWin(), bDF = a.getBaseDraw(), bAF = a.getBaseAwayWin();
        if (bHF == null || bDF == null || bAF == null) return null;

        double[] unavail = computeUnavailableImpacts(fixtureId, a.getHomeUnavailablePlayers(), a.getAwayUnavailablePlayers(), w);
        double deltaHA = computeHaDelta(a, w) + unavail[0];
        double deltaD  = computeDrawDelta(a, w) + unavail[1];
        return applyBlend(bHF, bDF, bAF, deltaHA, deltaD);
    }

    /**
     * Returns [deltaHA, deltaD] including unavailable player contributions.
     * Useful for netDelta display in the history view.
     */
    public double[] computeFullDeltas(Long fixtureId, FixtureContextualAnalysis a, ContextualWeightConfig w) {
        double[] unavail = computeUnavailableImpacts(fixtureId, a.getHomeUnavailablePlayers(), a.getAwayUnavailablePlayers(), w);
        return new double[]{
            computeHaDelta(a, w) + unavail[0],
            computeDrawDelta(a, w) + unavail[1]
        };
    }

    /** HA delta from contextual factors (excluding unavailable players). */
    public double computeHaDelta(FixtureContextualAnalysis a, ContextualWeightConfig w) {
        return safe(w.getWForma(),      0.12) * diff(a.getHomeCurrentForm(),       a.getAwayCurrentForm())
             + safe(w.getWNeeds(),      0.10) * diff(a.getHomeTeamNeeds(),         a.getAwayTeamNeeds())
             + Math.max(0.0, safe(w.getWDef(),      0.07)) * diff(a.getHomeDefensiveBlock(),    a.getAwayDefensiveBlock())
             + Math.max(0.0, safe(w.getWOff(),      0.07)) * diff(a.getHomeOffensiveRhythm(),   a.getAwayOffensiveRhythm())
             + safe(w.getWFatigue(),    0.06) * diff(a.getAwayFatigue(),           a.getHomeFatigue())
             + safe(w.getWSetPieces(),  0.06) * diff(a.getHomeSetPieces(),         a.getAwaySetPieces())
             + Math.max(0.0, safe(w.getWAtm(),      0.03)) * diff(a.getHomeStadiumAtmosphere(), a.getAwayStadiumAtmosphere());
    }

    /** Draw delta from contextual factors (excluding unavailable players). */
    public double computeDrawDelta(FixtureContextualAnalysis a, ContextualWeightConfig w) {
        return Math.max(0.0, safe(w.getWFormaD(),     0.0)) * drawBalance(a.getHomeCurrentForm(),       a.getAwayCurrentForm())
             + Math.max(0.0, safe(w.getWNeedsD(),     0.0)) * drawBalance(a.getHomeTeamNeeds(),         a.getAwayTeamNeeds())
             + Math.max(0.0, safe(w.getWDefD(),        0.0)) * drawBalance(a.getHomeDefensiveBlock(),    a.getAwayDefensiveBlock())
             + Math.max(0.0, safe(w.getWOffD(),        0.0)) * drawBalance(a.getHomeOffensiveRhythm(),   a.getAwayOffensiveRhythm())
             + Math.max(0.0, safe(w.getWFatigueD(),   0.0)) * drawBalance(a.getHomeFatigue(),           a.getAwayFatigue())
             + Math.max(0.0, safe(w.getWSetPiecesD(), 0.0)) * drawBalance(a.getHomeSetPieces(),         a.getAwaySetPieces())
             + Math.max(0.0, safe(w.getWAtmD(),        0.0)) * drawBalance(a.getHomeStadiumAtmosphere(), a.getAwayStadiumAtmosphere());
    }

    /**
     * Returns [haImpact, drawImpact] from unavailable players using percentile data from DB.
     * haImpact   = wUnavail  * (awayImpact − homeImpact)
     * drawImpact = wUnavailD * (awayImpact + homeImpact)
     */
    public double[] computeUnavailableImpacts(Long fixtureId, String homeCsv, String awayCsv, ContextualWeightConfig w) {
        List<Long> homeIds = parseCsvIds(homeCsv);
        List<Long> awayIds = parseCsvIds(awayCsv);

        if (homeIds.isEmpty() && awayIds.isEmpty()) return new double[]{0.0, 0.0};

        Map<Long, Double> pcts = tryQueryPercentiles(fixtureId, homeIds, awayIds);

        double homeImpact = homeIds.stream()
                .mapToDouble(id -> (pcts.getOrDefault(id, DEFAULT_PLAYER_PCT) - 50.0) / 100.0)
                .sum();
        double awayImpact = awayIds.stream()
                .mapToDouble(id -> (pcts.getOrDefault(id, DEFAULT_PLAYER_PCT) - 50.0) / 100.0)
                .sum();

        return new double[]{
            safe(w.getWUnavail(),  0.10) * (awayImpact - homeImpact),
            safe(w.getWUnavailD(), 0.0)  * (awayImpact + homeImpact)
        };
    }

    /**
     * Softmax blend:
     *   logit(home) += deltaHA
     *   logit(draw) += deltaD
     *   logit(away) -= deltaHA
     */
    public double[] applyBlend(double bH, double bD, double bA, double deltaHA, double deltaD) {
        bH = Math.max(0.001, Math.min(0.999, bH));
        bD = Math.max(0.001, Math.min(0.999, bD));
        bA = Math.max(0.001, Math.min(0.999, bA));

        double logH = Math.log(bH) + deltaHA;
        double logD = Math.log(bD) + deltaD;
        double logA = Math.log(bA) - deltaHA;

        double maxLog = Math.max(logH, Math.max(logD, logA));
        double sumExp = Math.exp(logH - maxLog) + Math.exp(logD - maxLog) + Math.exp(logA - maxLog);

        return new double[]{
            Math.exp(logH - maxLog) / sumExp,
            Math.exp(logD - maxLog) / sumExp,
            Math.exp(logA - maxLog) / sumExp
        };
    }

    /** Default weights when no user config exists. */
    public ContextualWeightConfig defaultWeights() {
        ContextualWeightConfig cfg = new ContextualWeightConfig();
        cfg.setWForma(0.12);    cfg.setWNeeds(0.10);    cfg.setWDef(0.07);
        cfg.setWOff(0.07);      cfg.setWFatigue(0.06);  cfg.setWSetPieces(0.06);
        cfg.setWAtm(0.03);      cfg.setWUnavail(0.10);
        cfg.setWFormaD(0.0);    cfg.setWNeedsD(0.0);    cfg.setWDefD(0.0);
        cfg.setWOffD(0.0);      cfg.setWFatigueD(0.0);  cfg.setWSetPiecesD(0.0);
        cfg.setWAtmD(0.0);      cfg.setWUnavailD(0.0);
        return cfg;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private double diff(Integer home, Integer away) { return nvl(home) - nvl(away); }

    /** Equality signal: +2 = identical teams, −2 = max difference (1 vs 5). */
    private double drawBalance(Integer home, Integer away) { return 2.0 - Math.abs(nvl(home) - nvl(away)); }

    private double nvl(Integer v) { return v != null ? v : 3.0; }
    private double safe(Double v, double def) { return v != null ? v : def; }

    private Map<Long, Double> tryQueryPercentiles(Long fixtureId, List<Long> homeIds, List<Long> awayIds) {
        try {
            List<Map<String, Object>> rows = namedJdbc.queryForList(
                    "SELECT league_id, season, match_date FROM fixture WHERE id = :fid",
                    Collections.singletonMap("fid", fixtureId));
            if (rows.isEmpty()) return Collections.emptyMap();

            Map<String, Object> fix = rows.get(0);
            long      leagueId  = ((Number) fix.get("league_id")).longValue();
            int       season    = ((Number) fix.get("season")).intValue();
            Timestamp matchDate = toTimestamp(fix.get("match_date"));
            if (matchDate == null) return Collections.emptyMap();

            List<Long> allIds = new ArrayList<>(homeIds);
            allIds.addAll(awayIds);
            return queryPercentiles(allIds, leagueId, season, matchDate);
        } catch (Exception e) {
            System.err.println("[ContextualBlend] fixture " + fixtureId
                    + " — no se pudo obtener percentiles, usando valor por defecto: " + e.getMessage());
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
        if (val instanceof java.time.OffsetDateTime odt) return Timestamp.from(odt.toInstant());
        if (val instanceof java.time.LocalDateTime ldt) return Timestamp.valueOf(ldt);
        return null;
    }
}
