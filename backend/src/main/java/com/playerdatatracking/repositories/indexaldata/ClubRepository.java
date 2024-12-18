package com.playerdatatracking.repositories.indexaldata;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playerdatatracking.entities.indexaldata.Club;

public interface ClubRepository extends JpaRepository<Club, Long> {

}
