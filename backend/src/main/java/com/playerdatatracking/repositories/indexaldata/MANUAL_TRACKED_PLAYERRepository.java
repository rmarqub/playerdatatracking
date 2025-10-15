package com.playerdatatracking.repositories.indexaldata;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;

@Repository
public interface MANUAL_TRACKED_PLAYERRepository extends JpaRepository<ManualTrackedPlayer, Long> {
	
	
	ManualTrackedPlayer findByNombre(String nombre);
	List<ManualTrackedPlayer> findAllByUserId(Long userId);
    Optional<ManualTrackedPlayer> findByNombreAndUserId(String nombre, Long userId);
	
}
