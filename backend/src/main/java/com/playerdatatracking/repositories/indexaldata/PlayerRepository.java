package com.playerdatatracking.repositories.indexaldata;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playerdatatracking.entities.indexaldata.Player;

public interface PlayerRepository extends JpaRepository<Player, Long>{

}
