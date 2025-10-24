package com.playerdatatracking.repositories.indexaldata;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.Player;

public interface PlayerRepository extends JpaRepository<Player, Long>{

	
	@Query("SELECT p FROM Player p WHERE LOWER(p.firstname) LIKE LOWER(CONCAT('%', :playerName, '%')) " +
            "OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', :playerName, '%')) " +
            "OR LOWER(p.fullname) LIKE LOWER(CONCAT('%', :playerName, '%'))")
    List<Player> findByPlayerName(@Param("playerName") String playerName);

	List<Player> findByTeam_NombreContainingIgnoreCase(String clubName);
	
	@Query("select p.photo from Player p where p.id = :id")
	byte[] findPhotoById(@Param("id") Long id);

	@Query("select p.photoContentType from Player p where p.id = :id")
	String findPhotoContentTypeById(@Param("id") Long id);

	@Query("select p.photoUpdatedAt from Player p where p.id = :id")
	LocalDateTime findPhotoUpdatedAtById(@Param("id") Long id);
	
	List<Player> findByTeamAndIndexId(Club team, Long indexId);
}
