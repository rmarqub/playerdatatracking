package com.playerdatatracking.repositories.indexaldata;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.Player;

public interface ClubRepository extends JpaRepository<Club, Long> {

    List<Club> findByNombreContainingIgnoreCase(String nombre);
}

