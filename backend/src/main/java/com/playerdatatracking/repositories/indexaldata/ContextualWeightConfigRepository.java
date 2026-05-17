package com.playerdatatracking.repositories.indexaldata;

import com.playerdatatracking.entities.indexaldata.ContextualWeightConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ContextualWeightConfigRepository extends JpaRepository<ContextualWeightConfig, Long> {

    Optional<ContextualWeightConfig> findByUserId(Long userId);
}
