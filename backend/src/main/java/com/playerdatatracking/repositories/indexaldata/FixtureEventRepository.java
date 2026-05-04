package com.playerdatatracking.repositories.indexaldata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.FixtureEvent;

import java.util.List;
 
@Repository
public interface FixtureEventRepository extends JpaRepository<FixtureEvent, Long> {
 
    List<FixtureEvent> findByFixtureIdOrderByTimeElapsedAsc(Long fixtureId);
 
    List<FixtureEvent> findByFixtureIdAndTeamId(Long fixtureId, Long teamId);
 
    List<FixtureEvent> findByFixtureIdAndEventType(Long fixtureId, String eventType);
 
    // Todos los goles de un partido
    @Query("SELECT e FROM FixtureEvent e WHERE e.fixture.id = :fixtureId AND e.eventType = 'Goal' ORDER BY e.timeElapsed ASC")
    List<FixtureEvent> findGoalsByFixture(@Param("fixtureId") Long fixtureId);
 
    // Tarjetas de un partido
    @Query("SELECT e FROM FixtureEvent e WHERE e.fixture.id = :fixtureId AND e.eventType = 'Card' ORDER BY e.timeElapsed ASC")
    List<FixtureEvent> findCardsByFixture(@Param("fixtureId") Long fixtureId);
 
    // Historial de goles de un jugador
    @Query("SELECT e FROM FixtureEvent e WHERE e.playerId = :playerId AND e.eventType = 'Goal' ORDER BY e.fixture.matchDate DESC")
    List<FixtureEvent> findGoalsByPlayer(@Param("playerId") Long playerId);
 
    // Tarjetas de un jugador (útil para flags de suspensión en el modelo)
    @Query("SELECT e FROM FixtureEvent e WHERE e.playerId = :playerId AND e.eventType = 'Card' ORDER BY e.fixture.matchDate DESC")
    List<FixtureEvent> findCardsByPlayer(@Param("playerId") Long playerId);
 
    void deleteByFixtureId(Long fixtureId);

    boolean existsByFixtureId(Long fixtureId);
}
