package com.playerdatatracking.repositories.indexaldata;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.Squad;

import jakarta.transaction.Transactional;

@Repository
public interface SquadRepository extends JpaRepository<Squad, Long> {

    List<Squad> findByTeam(Long team);

    Optional<Squad> findByTeamAndId(Long team, Long id);

    @Query(value = "SELECT * FROM squad s WHERE :player = ANY(s.players)", nativeQuery = true)
    List<Squad> findByPlayerInSquad(@Param("player") Long player);

    @Modifying
    @Transactional
    @Query(value = "UPDATE squad SET players = :players WHERE id = :squadId", nativeQuery = true)
    int replacePlayers(@Param("squadId") Long squadId, @Param("players") Long[] players);
}
