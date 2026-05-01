package com.playerdatatracking.repositories.indexaldata;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.FixturePlayerStats;
import com.playerdatatracking.entities.indexaldata.FixturePlayerStatsId;

import java.util.List;
import java.util.Optional;
 
@Repository
public interface FixturePlayerStatsRepository extends JpaRepository<FixturePlayerStats, FixturePlayerStatsId> {
 
    List<FixturePlayerStats> findByFixtureId(Long fixtureId);
 
    List<FixturePlayerStats> findByFixtureIdAndTeamId(Long fixtureId, Long teamId);
 
    Optional<FixturePlayerStats> findByFixtureIdAndPlayerId(Long fixtureId, Long playerId);
 
    // Historial de rendimiento de un jugador (para features del modelo)
    @Query("SELECT s FROM FixturePlayerStats s JOIN s.fixture f WHERE s.playerId = :playerId AND f.statusShort = 'FT' ORDER BY f.matchDate DESC")
    List<FixturePlayerStats> findByPlayerIdOrderByDateDesc(@Param("playerId") Long playerId);
 
    // Últimos N partidos de un jugador
    @Query("SELECT s FROM FixturePlayerStats s JOIN s.fixture f WHERE s.playerId = :playerId AND f.statusShort = 'FT' ORDER BY f.matchDate DESC LIMIT :limit")
    List<FixturePlayerStats> findLastStatsByPlayer(@Param("playerId") Long playerId, @Param("limit") int limit);
 
    // Partidos de un jugador en una temporada concreta
    @Query("SELECT s FROM FixturePlayerStats s JOIN s.fixture f WHERE s.playerId = :playerId AND f.season = :season ORDER BY f.matchDate DESC")
    List<FixturePlayerStats> findByPlayerIdAndSeason(@Param("playerId") Long playerId, @Param("season") Integer season);
 
    // Rating medio de un jugador en una temporada (indicador de forma)
    @Query("SELECT AVG(s.rating) FROM FixturePlayerStats s JOIN s.fixture f WHERE s.playerId = :playerId AND f.season = :season AND f.statusShort = 'FT'")
    Double avgRatingBySeason(@Param("playerId") Long playerId, @Param("season") Integer season);
 
    // Goles totales de un jugador en una temporada
    @Query("SELECT SUM(s.goalsScored) FROM FixturePlayerStats s JOIN s.fixture f WHERE s.playerId = :playerId AND f.season = :season AND f.statusShort = 'FT'")
    Integer sumGoalsBySeason(@Param("playerId") Long playerId, @Param("season") Integer season);
 
    // Asistencias totales de un jugador en una temporada
    @Query("SELECT SUM(s.assists) FROM FixturePlayerStats s JOIN s.fixture f WHERE s.playerId = :playerId AND f.season = :season AND f.statusShort = 'FT'")
    Integer sumAssistsBySeason(@Param("playerId") Long playerId, @Param("season") Integer season);
 
    // Minutos jugados totales (para normalización de stats por 90 min)
    @Query("SELECT SUM(s.minutesPlayed) FROM FixturePlayerStats s JOIN s.fixture f WHERE s.playerId = :playerId AND f.season = :season AND f.statusShort = 'FT'")
    Integer sumMinutesBySeason(@Param("playerId") Long playerId, @Param("season") Integer season);
 
    // Titulares de un equipo en un partido (substitute = false)
    @Query("SELECT s FROM FixturePlayerStats s WHERE s.fixture.id = :fixtureId AND s.teamId = :teamId AND s.substitute = false")
    List<FixturePlayerStats> findStartingLineup(@Param("fixtureId") Long fixtureId, @Param("teamId") Long teamId);
 
    void deleteByFixtureId(Long fixtureId);
}
