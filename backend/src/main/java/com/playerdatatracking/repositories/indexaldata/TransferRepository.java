package com.playerdatatracking.repositories.indexaldata;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.Transfer;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {

	
	@Query(value = "SELECT * FROM transfer t WHERE t.player = :player", nativeQuery = true)
    List<Transfer> findByPlayer(Long player);
	@Query(value = "SELECT * FROM transfer t WHERE t.\"in\" = :inPlayer", nativeQuery = true)
    List<Transfer> findByInPlayer(Long inPlayer);
	@Query(value = "SELECT * FROM transfer t WHERE t.\"out\" = :outPlayer", nativeQuery = true)
    List<Transfer> findByOutPlayer(Long outPlayer);
}