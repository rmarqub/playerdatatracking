package com.playerdatatracking.operations.IndelxalData.player;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.PlayerPercentile;
import com.playerdatatracking.repositories.indexaldata.PlayerPercentileRepository;
import com.playerdatatracking.responses.GenericResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GeneratePlayerPercentilesFrontend {

    @Autowired
    private PlayerPercentileRepository percentileRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public GenericResponse<String> ejecutar(String season) {
        GenericResponse<String> response = new GenericResponse<>();
        try {
            int rowsInserted = calculateAndPersistPercentiles(season);
            response.setCODE(Constants.CODE_OK);
            response.setDescription("OK");
            response.setEntity(rowsInserted + " registros de percentiles actualizados para el frontend");
            return response;
        } catch (Exception e) {
            response.setCODE(Constants.CODE_ERR_PDDB);
            response.setDescription("Error al generar percentiles: " + e.getMessage());
            return response;
        }
    }

    protected int calculateAndPersistPercentiles(String seasonFilter) {
        String whereClause = (seasonFilter != null && !seasonFilter.isBlank())
                ? "WHERE CAST(f.season AS VARCHAR) = '" + seasonFilter + "'"
                : "";

        // Obtener todas las estadísticas de jugadores
        @SuppressWarnings("unchecked")
        List<Object[]> playerStats = entityManager.createNativeQuery(
                """
                SELECT
                    ps.player_id,
                    p.index_id,
                    f.league_id,
                    CAST(f.season AS VARCHAR),
                    COALESCE(SUM(ps.minutes_played), 0)::FLOAT as total_minutes,
                    COALESCE(AVG(CAST(ps.rating AS FLOAT)), 0)::FLOAT as avg_rating,
                    COALESCE(SUM(ps.goals_scored), 0)::FLOAT as total_goals,
                    COALESCE(SUM(ps.assists), 0)::FLOAT as total_assists,
                    COALESCE(SUM(ps.shots_total), 0)::FLOAT as total_shots,
                    COALESCE(SUM(ps.shots_on), 0)::FLOAT as total_shots_on,
                    COALESCE(SUM(ps.passes_total), 0)::FLOAT as total_passes,
                    COALESCE(SUM(ps.passes_key), 0)::FLOAT as total_passes_key,
                    COALESCE(AVG(CAST(ps.passes_accuracy AS FLOAT)), 0)::FLOAT as avg_passes_accuracy,
                    COALESCE(SUM(ps.tackles_total), 0)::FLOAT as total_tackles,
                    COALESCE(SUM(ps.interceptions), 0)::FLOAT as total_interceptions,
                    COALESCE(SUM(ps.duels_won), 0)::FLOAT as total_duels_won,
                    COALESCE(SUM(ps.dribbles_suc), 0)::FLOAT as total_dribbles_suc,
                    COALESCE(SUM(ps.fouls_drawn), 0)::FLOAT as total_fouls_drawn
                FROM fixture_player_stats ps
                JOIN fixture f ON f.id = ps.fixture_id
                JOIN player p ON p.index_id = ps.player_id
                """ + whereClause + """
                AND f.status_short = 'FT'
                AND ps.minutes_played > 0
                GROUP BY ps.player_id, p.index_id, f.league_id, f.season
                HAVING SUM(ps.minutes_played) >= 90
                """
        ).getResultList();

        // Agrupar por liga y temporada para calcular percentiles
        Map<String, List<PlayerStatsSnapshot>> groupedByLeagueSeason = new HashMap<>();
        for (Object[] row : playerStats) {
            Long playerId = ((Number) row[0]).longValue();
            Long indexId = row[1] != null ? ((Number) row[1]).longValue() : null;
            Integer leagueId = ((Number) row[2]).intValue();
            String season = (String) row[3];
            Float totalMinutes = row[4] != null ? ((Number) row[4]).floatValue() : 0f;
            Float avgRating = row[5] != null ? ((Number) row[5]).floatValue() : 0f;
            Float totalGoals = row[6] != null ? ((Number) row[6]).floatValue() : 0f;
            Float totalAssists = row[7] != null ? ((Number) row[7]).floatValue() : 0f;
            Float totalShots = row[8] != null ? ((Number) row[8]).floatValue() : 0f;
            Float totalShotsOn = row[9] != null ? ((Number) row[9]).floatValue() : 0f;
            Float totalPasses = row[10] != null ? ((Number) row[10]).floatValue() : 0f;
            Float totalPassesKey = row[11] != null ? ((Number) row[11]).floatValue() : 0f;
            Float avgPassAccuracy = row[12] != null ? ((Number) row[12]).floatValue() : 0f;
            Float totalTackles = row[13] != null ? ((Number) row[13]).floatValue() : 0f;
            Float totalInterceptions = row[14] != null ? ((Number) row[14]).floatValue() : 0f;
            Float totalDuelsWon = row[15] != null ? ((Number) row[15]).floatValue() : 0f;
            Float totalDribblesSuc = row[16] != null ? ((Number) row[16]).floatValue() : 0f;
            Float totalFoulsDrawn = row[17] != null ? ((Number) row[17]).floatValue() : 0f;

            PlayerStatsSnapshot snapshot = new PlayerStatsSnapshot(
                    playerId, indexId, leagueId, season,
                    totalMinutes, avgRating, totalGoals, totalAssists,
                    totalShots, totalShotsOn, totalPasses, totalPassesKey,
                    avgPassAccuracy, totalTackles, totalInterceptions,
                    totalDuelsWon, totalDribblesSuc, totalFoulsDrawn
            );

            String key = leagueId + "_" + season;
            groupedByLeagueSeason.computeIfAbsent(key, k -> new ArrayList<>()).add(snapshot);
        }

        // Calcular percentiles para cada liga/temporada
        List<PlayerPercentile> percentilesToSave = new ArrayList<>();
        for (List<PlayerStatsSnapshot> snapshots : groupedByLeagueSeason.values()) {
            calculatePercentiles(snapshots);
            for (PlayerStatsSnapshot snapshot : snapshots) {
                PlayerPercentile pp = new PlayerPercentile();
                pp.setPlayerId(snapshot.playerId);
                pp.setIndexId(snapshot.indexId);
                pp.setLeagueId(snapshot.leagueId);
                pp.setSeason(snapshot.season);
                pp.setPctMinutes(snapshot.pctMinutes);
                pp.setPctRating(snapshot.pctRating);
                pp.setPctGoalsP90(snapshot.pctGoalsP90);
                pp.setPctAssistsP90(snapshot.pctAssistsP90);
                pp.setPctShotsTotalP90(snapshot.pctShotsTotalP90);
                pp.setPctShotsOnP90(snapshot.pctShotsOnP90);
                pp.setPctPassesTotalP90(snapshot.pctPassesTotalP90);
                pp.setPctPassesKeyP90(snapshot.pctPassesKeyP90);
                pp.setPctPassAccuracy(snapshot.pctPassAccuracy);
                pp.setPctTacklesP90(snapshot.pctTacklesP90);
                pp.setPctInterceptionsP90(snapshot.pctInterceptionsP90);
                pp.setPctDuelsWon(snapshot.pctDuelsWon);
                pp.setPctDribblesSuccess(snapshot.pctDribblesSuccess);
                pp.setPctFoulsDrawnP90(snapshot.pctFoulsDrawnP90);
                pp.setComputedAt(new Timestamp(System.currentTimeMillis()));
                percentilesToSave.add(pp);
            }
        }

        // Calcular percentiles globales (league_id = 0): unir todas las ligas por jugador/temporada
        Map<String, PlayerStatsSnapshot> globalMap = new HashMap<>();
        for (List<PlayerStatsSnapshot> leagueSnapshots : groupedByLeagueSeason.values()) {
            for (PlayerStatsSnapshot s : leagueSnapshots) {
                String key = s.playerId + "_" + s.season;
                PlayerStatsSnapshot g = globalMap.get(key);
                if (g == null) {
                    globalMap.put(key, new PlayerStatsSnapshot(
                            s.playerId, s.indexId, 0, s.season,
                            s.totalMinutes, s.avgRating, s.totalGoals, s.totalAssists,
                            s.totalShots, s.totalShotsOn, s.totalPasses, s.totalPassesKey,
                            s.avgPassAccuracy, s.totalTackles, s.totalInterceptions,
                            s.totalDuelsWon, s.totalDribblesSuc, s.totalFoulsDrawn));
                } else {
                    float newMinutes = g.totalMinutes + s.totalMinutes;
                    float newPasses  = g.totalPasses  + s.totalPasses;
                    g.avgRating       = newMinutes > 0 ? (g.avgRating * g.totalMinutes + s.avgRating * s.totalMinutes) / newMinutes : 0f;
                    g.avgPassAccuracy = newPasses  > 0 ? (g.avgPassAccuracy * g.totalPasses + s.avgPassAccuracy * s.totalPasses) / newPasses : 0f;
                    g.totalMinutes       = newMinutes;
                    g.totalGoals        += s.totalGoals;
                    g.totalAssists      += s.totalAssists;
                    g.totalShots        += s.totalShots;
                    g.totalShotsOn      += s.totalShotsOn;
                    g.totalPasses        = newPasses;
                    g.totalPassesKey    += s.totalPassesKey;
                    g.totalTackles      += s.totalTackles;
                    g.totalInterceptions += s.totalInterceptions;
                    g.totalDuelsWon     += s.totalDuelsWon;
                    g.totalDribblesSuc  += s.totalDribblesSuc;
                    g.totalFoulsDrawn   += s.totalFoulsDrawn;
                }
            }
        }

        Map<String, List<PlayerStatsSnapshot>> globalBySeason = new HashMap<>();
        for (PlayerStatsSnapshot g : globalMap.values()) {
            globalBySeason.computeIfAbsent(g.season, k -> new ArrayList<>()).add(g);
        }

        for (List<PlayerStatsSnapshot> seasonSnapshots : globalBySeason.values()) {
            calculatePercentiles(seasonSnapshots);
            for (PlayerStatsSnapshot snapshot : seasonSnapshots) {
                PlayerPercentile pp = new PlayerPercentile();
                pp.setPlayerId(snapshot.playerId);
                pp.setIndexId(snapshot.indexId);
                pp.setLeagueId(0);
                pp.setSeason(snapshot.season);
                pp.setPctMinutes(snapshot.pctMinutes);
                pp.setPctRating(snapshot.pctRating);
                pp.setPctGoalsP90(snapshot.pctGoalsP90);
                pp.setPctAssistsP90(snapshot.pctAssistsP90);
                pp.setPctShotsTotalP90(snapshot.pctShotsTotalP90);
                pp.setPctShotsOnP90(snapshot.pctShotsOnP90);
                pp.setPctPassesTotalP90(snapshot.pctPassesTotalP90);
                pp.setPctPassesKeyP90(snapshot.pctPassesKeyP90);
                pp.setPctPassAccuracy(snapshot.pctPassAccuracy);
                pp.setPctTacklesP90(snapshot.pctTacklesP90);
                pp.setPctInterceptionsP90(snapshot.pctInterceptionsP90);
                pp.setPctDuelsWon(snapshot.pctDuelsWon);
                pp.setPctDribblesSuccess(snapshot.pctDribblesSuccess);
                pp.setPctFoulsDrawnP90(snapshot.pctFoulsDrawnP90);
                pp.setComputedAt(new Timestamp(System.currentTimeMillis()));
                percentilesToSave.add(pp);
            }
        }

        // UPSERT: si el trio (player, league, season) ya existe, asignar el ID para que JPA haga UPDATE
        if (!percentilesToSave.isEmpty()) {
            Set<String> affectedSeasons = percentilesToSave.stream()
                    .map(PlayerPercentile::getSeason)
                    .collect(Collectors.toSet());

            Map<String, Long> existingIds = new HashMap<>();
            for (String s : affectedSeasons) {
                for (PlayerPercentile ex : percentileRepository.findAllBySeason(s)) {
                    existingIds.put(ex.getPlayerId() + "_" + ex.getLeagueId() + "_" + ex.getSeason(), ex.getId());
                }
            }

            for (PlayerPercentile pp : percentilesToSave) {
                Long existingId = existingIds.get(pp.getPlayerId() + "_" + pp.getLeagueId() + "_" + pp.getSeason());
                if (existingId != null) {
                    pp.setId(existingId);
                }
            }

            percentileRepository.saveAll(percentilesToSave);
        }

        return percentilesToSave.size();
    }

    private void calculatePercentiles(List<PlayerStatsSnapshot> snapshots) {
        // Calcular valores por 90 minutos
        for (PlayerStatsSnapshot s : snapshots) {
            s.goalsP90 = (s.totalGoals * 90f) / s.totalMinutes;
            s.assistsP90 = (s.totalAssists * 90f) / s.totalMinutes;
            s.shotsP90 = (s.totalShots * 90f) / s.totalMinutes;
            s.shotsOnP90 = (s.totalShotsOn * 90f) / s.totalMinutes;
            s.passesP90 = (s.totalPasses * 90f) / s.totalMinutes;
            s.passesKeyP90 = (s.totalPassesKey * 90f) / s.totalMinutes;
            s.tacklesP90 = (s.totalTackles * 90f) / s.totalMinutes;
            s.interceptionsP90 = (s.totalInterceptions * 90f) / s.totalMinutes;
            s.foulsDrawnP90 = (s.totalFoulsDrawn * 90f) / s.totalMinutes;
        }

        // Calcular percentiles usando ranking
        for (PlayerStatsSnapshot s : snapshots) {
            s.pctMinutes = rankPercentile(snapshots, snap -> snap.totalMinutes, s);
            s.pctRating = rankPercentile(snapshots, snap -> snap.avgRating, s);
            s.pctGoalsP90 = rankPercentile(snapshots, snap -> snap.goalsP90, s);
            s.pctAssistsP90 = rankPercentile(snapshots, snap -> snap.assistsP90, s);
            s.pctShotsTotalP90 = rankPercentile(snapshots, snap -> snap.shotsP90, s);
            s.pctShotsOnP90 = rankPercentile(snapshots, snap -> snap.shotsOnP90, s);
            s.pctPassesTotalP90 = rankPercentile(snapshots, snap -> snap.passesP90, s);
            s.pctPassesKeyP90 = rankPercentile(snapshots, snap -> snap.passesKeyP90, s);
            s.pctPassAccuracy = rankPercentile(snapshots, snap -> snap.avgPassAccuracy, s);
            s.pctTacklesP90 = rankPercentile(snapshots, snap -> snap.tacklesP90, s);
            s.pctInterceptionsP90 = rankPercentile(snapshots, snap -> snap.interceptionsP90, s);
            s.pctDuelsWon = rankPercentile(snapshots, snap -> snap.totalDuelsWon, s);
            s.pctDribblesSuccess = rankPercentile(snapshots, snap -> snap.totalDribblesSuc, s);
            s.pctFoulsDrawnP90 = rankPercentile(snapshots, snap -> snap.foulsDrawnP90, s);
        }
    }

    private Integer rankPercentile(List<PlayerStatsSnapshot> snapshots, java.util.function.Function<PlayerStatsSnapshot, Float> metric, PlayerStatsSnapshot target) {
        List<PlayerStatsSnapshot> sorted = snapshots.stream()
                .sorted(Comparator.comparingDouble(s -> metric.apply(s)))
                .collect(Collectors.toList());

        int index = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).playerId.equals(target.playerId)) {
                index = i;
                break;
            }
        }

        if (index == -1 || sorted.isEmpty()) return null;
        return (int) ((index / (double) sorted.size()) * 100);
    }

    private static class PlayerStatsSnapshot {
        Long playerId;
        Long indexId;
        Integer leagueId;
        String season;
        Float totalMinutes;
        Float avgRating;
        Float totalGoals;
        Float totalAssists;
        Float totalShots;
        Float totalShotsOn;
        Float totalPasses;
        Float totalPassesKey;
        Float avgPassAccuracy;
        Float totalTackles;
        Float totalInterceptions;
        Float totalDuelsWon;
        Float totalDribblesSuc;
        Float totalFoulsDrawn;

        Float goalsP90, assistsP90, shotsP90, shotsOnP90, passesP90, passesKeyP90;
        Float tacklesP90, interceptionsP90, foulsDrawnP90;

        Integer pctMinutes, pctRating, pctGoalsP90, pctAssistsP90;
        Integer pctShotsTotalP90, pctShotsOnP90, pctPassesTotalP90, pctPassesKeyP90;
        Integer pctPassAccuracy, pctTacklesP90, pctInterceptionsP90;
        Integer pctDuelsWon, pctDribblesSuccess, pctFoulsDrawnP90;

        public PlayerStatsSnapshot(Long playerId, Long indexId, Integer leagueId, String season,
                                   Float totalMinutes, Float avgRating, Float totalGoals, Float totalAssists,
                                   Float totalShots, Float totalShotsOn, Float totalPasses, Float totalPassesKey,
                                   Float avgPassAccuracy, Float totalTackles, Float totalInterceptions,
                                   Float totalDuelsWon, Float totalDribblesSuc, Float totalFoulsDrawn) {
            this.playerId = playerId;
            this.indexId = indexId;
            this.leagueId = leagueId;
            this.season = season;
            this.totalMinutes = totalMinutes;
            this.avgRating = avgRating;
            this.totalGoals = totalGoals;
            this.totalAssists = totalAssists;
            this.totalShots = totalShots;
            this.totalShotsOn = totalShotsOn;
            this.totalPasses = totalPasses;
            this.totalPassesKey = totalPassesKey;
            this.avgPassAccuracy = avgPassAccuracy;
            this.totalTackles = totalTackles;
            this.totalInterceptions = totalInterceptions;
            this.totalDuelsWon = totalDuelsWon;
            this.totalDribblesSuc = totalDribblesSuc;
            this.totalFoulsDrawn = totalFoulsDrawn;
        }
    }
}
