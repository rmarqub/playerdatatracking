package com.playerdatatracking.repositories.indexaldata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.transaction.Transactional;

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

    @Query("SELECT f FROM Fixture f WHERE (f.homeTeamId IN :teamIds OR f.awayTeamId IN :teamIds) ORDER BY f.matchDate DESC")
    List<Fixture> findByTeamIds(@Param("teamIds") List<Long> teamIds);

    @Query("SELECT f FROM Fixture f WHERE (f.homeTeamId IN :teamIds OR f.awayTeamId IN :teamIds) AND f.leagueId IN :leagueIds ORDER BY f.matchDate DESC")
    List<Fixture> findByTeamIdsAndLeagueIds(@Param("teamIds") List<Long> teamIds, @Param("leagueIds") List<Integer> leagueIds);

    @Query("SELECT f FROM Fixture f WHERE f.leagueId IN :leagueIds ORDER BY f.matchDate DESC")
    List<Fixture> findByLeagueIds(@Param("leagueIds") List<Integer> leagueIds);

    @Transactional
    @Modifying
    void deleteByLeagueIdAndSeason(Integer leagueId, Integer season);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
        UPDATE Fixture f SET
            f.statusShort    = :statusShort,
            f.statusLong     = :statusLong,
            f.statusElapsed  = :statusElapsed,
            f.statusExtra    = :statusExtra,
            f.goalsHome      = :goalsHome,
            f.goalsAway      = :goalsAway,
            f.scoreHtHome    = :scoreHtHome,
            f.scoreHtAway    = :scoreHtAway,
            f.scoreFtHome    = :scoreFtHome,
            f.scoreFtAway    = :scoreFtAway,
            f.scoreEtHome    = :scoreEtHome,
            f.scoreEtAway    = :scoreEtAway,
            f.scorePenHome   = :scorePenHome,
            f.scorePenAway   = :scorePenAway,
            f.referee        = :referee,
            f.updatedAt      = :updatedAt
        WHERE f.id = :id
        """)
    void updateStatusAndScore(
        @Param("id")           Long id,
        @Param("statusShort")  String statusShort,
        @Param("statusLong")   String statusLong,
        @Param("statusElapsed") Integer statusElapsed,
        @Param("statusExtra")  Integer statusExtra,
        @Param("goalsHome")    Integer goalsHome,
        @Param("goalsAway")    Integer goalsAway,
        @Param("scoreHtHome")  Integer scoreHtHome,
        @Param("scoreHtAway")  Integer scoreHtAway,
        @Param("scoreFtHome")  Integer scoreFtHome,
        @Param("scoreFtAway")  Integer scoreFtAway,
        @Param("scoreEtHome")  Integer scoreEtHome,
        @Param("scoreEtAway")  Integer scoreEtAway,
        @Param("scorePenHome") Integer scorePenHome,
        @Param("scorePenAway") Integer scorePenAway,
        @Param("referee")      String referee,
        @Param("updatedAt")    java.time.OffsetDateTime updatedAt
    );

    @Query("SELECT f.id FROM Fixture f WHERE f.statusShort <> 'NS' ORDER BY f.id ASC")
    List<Long> findAllIds();

    @Query("SELECT f.id FROM Fixture f WHERE (f.eventsStored IS NULL OR f.eventsStored = false) AND NOT EXISTS (SELECT 1 FROM FixtureEvent e WHERE e.fixture.id = f.id) AND f.statusShort <> 'NS' ORDER BY f.id ASC")
    List<Long> findIdsWithoutEvents();

    @Modifying
    @Transactional
    @Query("UPDATE Fixture f SET f.eventsStored = true WHERE f.id = :id")
    void markEventsStored(@Param("id") Long id);

    @Query("SELECT f.id FROM Fixture f WHERE (f.matchStored IS NULL OR f.matchStored = false) AND NOT EXISTS (SELECT 1 FROM FixtureTeamStats s WHERE s.fixture.id = f.id) AND f.statusShort <> 'NS' ORDER BY f.id ASC")
    List<Long> findIdsWithoutTeamStats();

    @Query("SELECT f.id FROM Fixture f WHERE (f.statsStored IS NULL OR f.statsStored = false) AND NOT EXISTS (SELECT 1 FROM FixturePlayerStats s WHERE s.fixture.id = f.id) AND f.statusShort <> 'NS' ORDER BY f.id ASC")
    List<Long> findIdsWithoutPlayerStats();

    @Modifying
    @Transactional
    @Query("UPDATE Fixture f SET f.matchStored = true WHERE f.id = :id")
    void markMatchStored(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Fixture f SET f.statsStored = true WHERE f.id = :id")
    void markStatsStored(@Param("id") Long id);

    @Query("SELECT f.id FROM Fixture f WHERE (f.lineupStored IS NULL OR f.lineupStored = false) AND NOT EXISTS (SELECT 1 FROM FixtureLineup l WHERE l.fixture.id = f.id) AND f.statusShort <> 'NS' ORDER BY f.id ASC")
    List<Long> findIdsWithoutLineups();

    @Modifying
    @Transactional
    @Query("UPDATE Fixture f SET f.lineupStored = true WHERE f.id = :id")
    void markLineupStored(@Param("id") Long id);
}
