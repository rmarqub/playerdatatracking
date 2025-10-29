package com.playerdatatracking.repositories.indexaldata;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.DuppedPlayers;

@Repository
public interface DuppedPlayerRepository extends JpaRepository<DuppedPlayers, Long> {

    List<DuppedPlayers> findBySeason(String season);

    List<DuppedPlayers> findByPlayer(Long player);

    List<DuppedPlayers> findByTeam(Long team);

    List<DuppedPlayers> findBySeasonAndPlayer(String season, Long player);

    Optional<DuppedPlayers> findBySeasonAndPlayerAndTeam(String season, Long player, Long team);

    boolean existsBySeasonAndPlayerAndTeam(String season, Long player, Long team);

    long deleteBySeasonAndPlayerAndTeam(String season, Long player, Long team);
}
