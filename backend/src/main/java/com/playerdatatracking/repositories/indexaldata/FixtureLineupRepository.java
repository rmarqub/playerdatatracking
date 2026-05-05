package com.playerdatatracking.repositories.indexaldata;

import com.playerdatatracking.entities.indexaldata.FixtureLineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FixtureLineupRepository extends JpaRepository<FixtureLineup, Long> {

    List<FixtureLineup> findByFixtureId(Long fixtureId);

    boolean existsByFixtureId(Long fixtureId);

    void deleteByFixtureId(Long fixtureId);
}
