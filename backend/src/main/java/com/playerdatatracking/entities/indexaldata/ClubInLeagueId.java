package com.playerdatatracking.entities.indexaldata;

import java.io.Serializable;

import jakarta.persistence.Embeddable;

@Embeddable
public class ClubInLeagueId implements Serializable {
    private Long clubId;
    private Long torneoId;

    // Constructor por defecto
    public ClubInLeagueId() {
    }

    // Constructor con parámetros
    public ClubInLeagueId(Long clubId, Long torneoId) {
        this.clubId = clubId;
        this.torneoId = torneoId;
    }

    // Getters y Setters
    public Long getClubId() {
        return clubId;
    }

    public void setClubId(Long clubId) {
        this.clubId = clubId;
    }

    public Long getTorneoId() {
        return torneoId;
    }

    public void setTorneoId(Long torneoId) {
        this.torneoId = torneoId;
    }
}