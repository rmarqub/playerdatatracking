package com.playerdatatracking.repositories.indexaldata;

import com.playerdatatracking.entities.indexaldata.LeagueTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeagueTierRepository extends JpaRepository<LeagueTier, Long> {

    Optional<LeagueTier> findByTorneoId(Long torneoId);

    List<LeagueTier> findByTier(Integer tier);
}
