package com.playerdatatracking.repositories.indexaldata;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playerdatatracking.entities.indexaldata.FixtureContextualAnalysis;

public interface FixtureContextualAnalysisRepository
        extends JpaRepository<FixtureContextualAnalysis, Long> {

    Optional<FixtureContextualAnalysis> findByFixtureIdAndUserId(Long fixtureId, Long userId);

    boolean existsByFixtureIdAndUserId(Long fixtureId, Long userId);

    List<FixtureContextualAnalysis> findByUserId(Long userId);

    long countByUserId(Long userId);

    @Query("SELECT a.fixtureId FROM FixtureContextualAnalysis a WHERE a.fixtureId IN :ids AND a.userId = :userId")
    List<Long> findFixtureIdsByFixtureIdInAndUserId(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    @Query("SELECT a FROM FixtureContextualAnalysis a WHERE a.baseHomeWin IS NOT NULL AND a.userId = :userId")
    List<FixtureContextualAnalysis> findAllWithBaseSnapshotByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT a.* FROM fixture_contextual_analysis a INNER JOIN fixture f ON f.id = a.fixture_id WHERE f.status_short = 'NS' AND a.base_home_win IS NOT NULL AND a.user_id = :userId", nativeQuery = true)
    List<FixtureContextualAnalysis> findAllWithBaseSnapshotAndFixtureNotStartedByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT a.* FROM fixture_contextual_analysis a INNER JOIN fixture f ON f.id = a.fixture_id WHERE f.status_short IN ('FT','AET','PEN','AWD') AND a.base_home_win IS NOT NULL AND a.user_id = :userId", nativeQuery = true)
    List<FixtureContextualAnalysis> findAllWithBaseSnapshotAndFixtureFinishedByUserId(@Param("userId") Long userId);
}
