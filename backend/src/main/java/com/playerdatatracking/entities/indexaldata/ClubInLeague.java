package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "club_in_league")
public class ClubInLeague {

    @EmbeddedId
    private ClubInLeagueId id;

    @ManyToOne
    @MapsId("clubId")
    @JoinColumn(name = "club", referencedColumnName = "id", nullable = false)
    private Club club;

    @ManyToOne
    @MapsId("torneoId")
    @JoinColumn(name = "torneo", referencedColumnName = "id", nullable = false)
    private Torneo torneo;
}
