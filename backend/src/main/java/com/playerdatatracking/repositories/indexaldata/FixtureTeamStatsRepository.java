package com.playerdatatracking.repositories.indexaldata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.FixtureTeamStats;
import com.playerdatatracking.entities.indexaldata.FixtureTeamStatsId;

import java.util.List;
import java.util.Optional;
 
@Repository
public interface FixtureTeamStatsRepository extends JpaRepository<FixtureTeamStats, FixtureTeamStatsId> {
 
    List<FixtureTeamStats> findByFixtureId(Long fixtureId);
 
    Optional<FixtureTeamStats> findByFixtureIdAndTeamId(Long fixtureId, Long teamId);
 
    // Stats de un equipo en los últimos N partidos (para calcular promedios del modelo)
    @Query("SELECT s FROM FixtureTeamStats s JOIN s.fixture f WHERE s.teamId = :teamId AND f.statusShort = 'FT' ORDER BY f.matchDate DESC LIMIT :limit")
    List<FixtureTeamStats> findLastStatsByTeam(@Param("teamId") Long teamId, @Param("limit") int limit);
 
    // Posesión media de un equipo como local
    @Query("SELECT AVG(s.ballPossession) FROM FixtureTeamStats s JOIN s.fixture f WHERE s.teamId = :teamId AND f.homeTeamId = :teamId AND f.statusShort = 'FT'")
    Double avgPossessionHome(@Param("teamId") Long teamId);
 
    // Posesión media de un equipo como visitante
    @Query("SELECT AVG(s.ballPossession) FROM FixtureTeamStats s JOIN s.fixture f WHERE s.teamId = :teamId AND f.awayTeamId = :teamId AND f.statusShort = 'FT'")
    Double avgPossessionAway(@Param("teamId") Long teamId);
 
    // xG promedio de un equipo (para el modelo de goles)
    @Query("SELECT AVG(s.expectedGoals) FROM FixtureTeamStats s JOIN s.fixture f WHERE s.teamId = :teamId AND f.statusShort = 'FT' AND f.season = :season")
    Double avgExpectedGoalsBySeason(@Param("teamId") Long teamId, @Param("season") Integer season);
 
    void deleteByFixtureId(Long fixtureId);
}
 
