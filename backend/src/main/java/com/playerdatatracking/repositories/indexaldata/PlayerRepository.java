package com.playerdatatracking.repositories.indexaldata;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playerdatatracking.entities.indexaldata.Player;

public interface PlayerRepository extends JpaRepository<Player, Long>{

	
	@Query("SELECT p FROM Player p WHERE LOWER(p.firstname) LIKE LOWER(CONCAT('%', :playerName, '%')) " +
            "OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', :playerName, '%')) " +
            "OR LOWER(p.fullname) LIKE LOWER(CONCAT('%', :playerName, '%'))")
    List<Player> findByPlayerName(@Param("playerName") String playerName);

	@Query("SELECT p FROM Player p JOIN Torneo t ON p.team = t.id WHERE t.name = :teamName")
	List<Player> findByTeamName(@Param("teamName") String teamName);
}
