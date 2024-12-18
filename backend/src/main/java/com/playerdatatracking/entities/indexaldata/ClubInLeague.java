package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "club_in_league")
public class ClubInLeague {

    @Column(name = "club")
	private int clubId;
    
    @Column(name = "torneo")
	private int torneoId;
}
