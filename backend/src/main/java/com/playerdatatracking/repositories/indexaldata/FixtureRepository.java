package com.playerdatatracking.repositories.indexaldata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.Fixture;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
 
@Repository
public interface FixtureRepository extends JpaRepository<Fixture, Long>{
	List<Fixture> findByLeagueIdAndSeason(Integer leagueId, Integer season);
	 
    List<Fixture> findByLeagueIdAndSeasonOrderByMatchDateAsc(Integer leagueId, Integer season);
 
    // Partidos de un equipo (local o visitante) en una temporada
    @Query("SELECT f FROM Fixture f WHERE (f.homeTeamId = :teamId OR f.awayTeamId = :teamId) AND f.season = :season ORDER BY f.matchDate DESC")
    List<Fixture> findByTeamAndSeason(@Param("teamId") Long teamId, @Param("season") Integer season);
 
    // Partidos de un equipo (local o visitante) en una liga y temporada
    @Query("SELECT f FROM Fixture f WHERE f.leagueId = :leagueId AND (f.homeTeamId = :teamId OR f.awayTeamId = :teamId) AND f.season = :season ORDER BY f.matchDate DESC")
    List<Fixture> findByLeagueAndTeamAndSeason(@Param("leagueId") Integer leagueId, @Param("teamId") Long teamId, @Param("season") Integer season);
 
    // --- Calendario: partidos por fecha ---
 
    // Partidos de hoy para una lista de ligas (útil para el calendario en vivo)
    @Query("SELECT f FROM Fixture f WHERE f.leagueId IN :leagueIds AND f.matchDate BETWEEN :from AND :to ORDER BY f.matchDate ASC")
    List<Fixture> findByLeaguesAndDateRange(@Param("leagueIds") List<Integer> leagueIds,
                                             @Param("from") OffsetDateTime from,
                                             @Param("to") OffsetDateTime to);
 
    // Partidos en juego actualmente
    @Query("SELECT f FROM Fixture f WHERE f.statusShort IN ('1H','HT','2H','ET','BT','P','LIVE') ORDER BY f.matchDate ASC")
    List<Fixture> findLiveFixtures();
 
    // Próximos X partidos de una liga
    @Query("SELECT f FROM Fixture f WHERE f.leagueId = :leagueId AND f.matchDate > :now ORDER BY f.matchDate ASC LIMIT :limit")
    List<Fixture> findNextFixturesByLeague(@Param("leagueId") Integer leagueId,
                                            @Param("now") OffsetDateTime now,
                                            @Param("limit") int limit);
 
    // Últimos X partidos de un equipo (para features del modelo)
    @Query("SELECT f FROM Fixture f WHERE (f.homeTeamId = :teamId OR f.awayTeamId = :teamId) AND f.statusShort = 'FT' ORDER BY f.matchDate DESC LIMIT :limit")
    List<Fixture> findLastFinishedByTeam(@Param("teamId") Long teamId, @Param("limit") int limit);
 
    // --- Comprobación de existencia ---
 
    boolean existsById(Long fixtureId);
 
    // IDs ya almacenados de una liga/temporada (para sincronización incremental)
    @Query("SELECT f.id FROM Fixture f WHERE f.leagueId = :leagueId AND f.season = :season")
    List<Long> findIdsByLeagueAndSeason(@Param("leagueId") Integer leagueId, @Param("season") Integer season);
 
    // --- Para el modelo predictivo ---
 
    // Partidos finalizados de dos equipos entre sí (head-to-head)
    @Query("SELECT f FROM Fixture f WHERE f.statusShort = 'FT' AND ((f.homeTeamId = :team1 AND f.awayTeamId = :team2) OR (f.homeTeamId = :team2 AND f.awayTeamId = :team1)) ORDER BY f.matchDate DESC")
    List<Fixture> findHeadToHead(@Param("team1") Long team1, @Param("team2") Long team2);
 
    // Partidos finalizados de un equipo como local
    @Query("SELECT f FROM Fixture f WHERE f.homeTeamId = :teamId AND f.statusShort = 'FT' ORDER BY f.matchDate DESC LIMIT :limit")
    List<Fixture> findLastHomeFixtures(@Param("teamId") Long teamId, @Param("limit") int limit);
 
    // Partidos finalizados de un equipo como visitante
    @Query("SELECT f FROM Fixture f WHERE f.awayTeamId = :teamId AND f.statusShort = 'FT' ORDER BY f.matchDate DESC LIMIT :limit")
    List<Fixture> findLastAwayFixtures(@Param("teamId") Long teamId, @Param("limit") int limit);

    @Query("SELECT f FROM Fixture f WHERE LOWER(f.homeTeamName) LIKE LOWER(CONCAT('%', :teamName, '%')) OR LOWER(f.awayTeamName) LIKE LOWER(CONCAT('%', :teamName, '%')) ORDER BY f.matchDate DESC")
    List<Fixture> findByTeamNameContaining(@Param("teamName") String teamName);

    List<Fixture> findByLeagueIdOrderByMatchDateDesc(Integer leagueId);
}
