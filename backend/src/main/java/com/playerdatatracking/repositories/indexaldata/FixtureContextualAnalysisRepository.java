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

    Optional<FixtureContextualAnalysis> findByFixtureId(Long fixtureId);

    boolean existsByFixtureId(Long fixtureId);

    @Query("SELECT a.fixtureId FROM FixtureContextualAnalysis a WHERE a.fixtureId IN :ids")
    List<Long> findFixtureIdsByFixtureIdIn(@Param("ids") Collection<Long> ids);

    @Query("SELECT a FROM FixtureContextualAnalysis a WHERE a.baseHomeWin IS NOT NULL")
    List<FixtureContextualAnalysis> findAllWithBaseSnapshot();
    
    @Query(value = "SELECT a.* FROM fixture_contextual_analysis a INNER JOIN fixture f ON f.id = a.fixture_id WHERE f.status_short = 'NS' AND a.base_home_win IS NOT NULL", nativeQuery = true)
    List<FixtureContextualAnalysis> findAllWithBaseSnapshotAndFixtureNotStarted();
}
