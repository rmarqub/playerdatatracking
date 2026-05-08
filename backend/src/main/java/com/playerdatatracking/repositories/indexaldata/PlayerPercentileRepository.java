package com.playerdatatracking.repositories.indexaldata;

import com.playerdatatracking.entities.indexaldata.PlayerPercentile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerPercentileRepository extends JpaRepository<PlayerPercentile, Long> {

    List<PlayerPercentile> findByIndexId(Long indexId);

    List<PlayerPercentile> findByIndexIdAndSeason(Long indexId, String season);

    List<PlayerPercentile> findByPlayerIdAndSeason(Long playerId, String season);
}
