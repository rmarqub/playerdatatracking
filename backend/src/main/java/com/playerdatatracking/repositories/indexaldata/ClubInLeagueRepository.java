package com.playerdatatracking.repositories.indexaldata;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playerdatatracking.entities.indexaldata.ClubInLeague;

public interface ClubInLeagueRepository extends JpaRepository<ClubInLeague, Long>  {
	Optional<ClubInLeague> findByClubIdAndTorneoId(Long club, Long torneo);
	List<ClubInLeague> findByClubId(Long club);
}
