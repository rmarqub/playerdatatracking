package com.playerdatatracking.repositories.indexaldata;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.entities.indexaldata.PlayerPhotoData;
import com.playerdatatracking.requests.IndexTeamPair;

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

	@Query("SELECT p.photo AS photo, p.photoContentType AS photoContentType, p.photoUpdatedAt AS photoUpdatedAt FROM Player p WHERE p.id = :id")
	PlayerPhotoData findPhotoDataById(@Param("id") Long id);
	
	List<Player> findByTeamAndIndexId(Club team, Long indexId);
	
    @Query(value = """
            SELECT p.index_id AS indexId, p.team AS team
            FROM public.player p
            WHERE p.index_id IN (
                SELECT index_id
                FROM public.player
                WHERE index_id IS NOT NULL
                GROUP BY index_id
                HAVING COUNT(DISTINCT team) > 1
            )
            GROUP BY p.index_id, p.team
            ORDER BY p.index_id, p.team
            """, nativeQuery = true)
        List<IndexTeamPair> findIndexIdTeamPairsWithCrossTeamDuplicates();
}
