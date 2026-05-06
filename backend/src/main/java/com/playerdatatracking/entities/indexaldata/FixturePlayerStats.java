package com.playerdatatracking.entities.indexaldata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "fixture_player_stats")
@IdClass(FixturePlayerStatsId.class)
public class FixturePlayerStats {

    @Id
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;
 
    @Id
    @Column(name = "player_id", nullable = false)
    private Long playerId;
 
    @Column(name = "team_id", nullable = false)
    private Long teamId;
 
    @Column(name = "player_name")
    private String playerName;
 
    @Column(name = "position", length = 10)
    private String position; // G / D / M / F
 
    @Column(name = "minutes_played")
    private Integer minutesPlayed;
 
    @Column(name = "rating", precision = 4, scale = 2)
    private BigDecimal rating;
 
    @Column(name = "captain")
    private Boolean captain = false;
 
    @Column(name = "substitute")
    private Boolean substitute = false;
 
    // Ofensivo
    @Column(name = "offsides")
    private Integer offsides;
 
    @Column(name = "shots_total")
    private Integer shotsTotal;
 
    @Column(name = "shots_on")
    private Integer shotsOn;
 
    @Column(name = "goals_scored")
    private Integer goalsScored;
 
    @Column(name = "goals_conceded")
    private Integer goalsConceded;
 
    @Column(name = "assists")
    private Integer assists;
 
    @Column(name = "saves")
    private Integer saves;
 
    // Pases
    @Column(name = "passes_total")
    private Integer passesTotal;
 
    @Column(name = "passes_key")
    private Integer passesKey;
 
    @Column(name = "passes_accuracy", precision = 5, scale = 2)
    private BigDecimal passesAccuracy;
 
    // Defensa
    @Column(name = "tackles_total")
    private Integer tacklesTotal;
 
    @Column(name = "tackles_blocks")
    private Integer tacklesBlocks;
 
    @Column(name = "interceptions")
    private Integer interceptions;
 
    // Duelos
    @Column(name = "duels_total")
    private Integer duelsTotal;
 
    @Column(name = "duels_won")
    private Integer duelsWon;
 
    // Regates
    @Column(name = "dribbles_att")
    private Integer dribblesAtt;
 
    @Column(name = "dribbles_suc")
    private Integer dribblesSuc;
 
    @Column(name = "dribbles_past")
    private Integer dribblesPast;
 
    // Faltas y tarjetas
    @Column(name = "fouls_drawn")
    private Integer foulsDrawn;
 
    @Column(name = "fouls_committed")
    private Integer foulsCommitted;
 
    @Column(name = "yellow_cards")
    private Integer yellowCards;
 
    @Column(name = "red_cards")
    private Integer redCards;
 
    @Column(name = "yellow_red_cards")
    private Integer yellowRedCards;
 
    // Penaltis
    @Column(name = "penalty_won")
    private Integer penaltyWon;
 
    @Column(name = "penalty_scored")
    private Integer penaltyScored;
 
    @Column(name = "penalty_missed")
    private Integer penaltyMissed;
 
    @Column(name = "penalty_saved")
    private Integer penaltySaved;
 
    @Column(name = "penalty_committed")
    private Integer penaltyCommitted;

	public Fixture getFixture() {
		return fixture;
	}

	public void setFixture(Fixture fixture) {
		this.fixture = fixture;
	}

	public Long getPlayerId() {
		return playerId;
	}

	public void setPlayerId(Long playerId) {
		this.playerId = playerId;
	}

	public Long getTeamId() {
		return teamId;
	}

	public void setTeamId(Long teamId) {
		this.teamId = teamId;
	}

	public String getPlayerName() {
		return playerName;
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	public String getPosition() {
		return position;
	}

	public void setPosition(String position) {
		this.position = position;
	}

	public Integer getMinutesPlayed() {
		return minutesPlayed;
	}

	public void setMinutesPlayed(Integer minutesPlayed) {
		this.minutesPlayed = minutesPlayed;
	}

	public BigDecimal getRating() {
		return rating;
	}

	public void setRating(BigDecimal rating) {
		this.rating = rating;
	}

	public Boolean getCaptain() {
		return captain;
	}

	public void setCaptain(Boolean captain) {
		this.captain = captain;
	}

	public Boolean getSubstitute() {
		return substitute;
	}

	public void setSubstitute(Boolean substitute) {
		this.substitute = substitute;
	}

	public Integer getOffsides() {
		return offsides;
	}

	public void setOffsides(Integer offsides) {
		this.offsides = offsides;
	}

	public Integer getShotsTotal() {
		return shotsTotal;
	}

	public void setShotsTotal(Integer shotsTotal) {
		this.shotsTotal = shotsTotal;
	}

	public Integer getShotsOn() {
		return shotsOn;
	}

	public void setShotsOn(Integer shotsOn) {
		this.shotsOn = shotsOn;
	}

	public Integer getGoalsScored() {
		return goalsScored;
	}

	public void setGoalsScored(Integer goalsScored) {
		this.goalsScored = goalsScored;
	}

	public Integer getGoalsConceded() {
		return goalsConceded;
	}

	public void setGoalsConceded(Integer goalsConceded) {
		this.goalsConceded = goalsConceded;
	}

	public Integer getAssists() {
		return assists;
	}

	public void setAssists(Integer assists) {
		this.assists = assists;
	}

	public Integer getSaves() {
		return saves;
	}

	public void setSaves(Integer saves) {
		this.saves = saves;
	}

	public Integer getPassesTotal() {
		return passesTotal;
	}

	public void setPassesTotal(Integer passesTotal) {
		this.passesTotal = passesTotal;
	}

	public Integer getPassesKey() {
		return passesKey;
	}

	public void setPassesKey(Integer passesKey) {
		this.passesKey = passesKey;
	}

	public BigDecimal getPassesAccuracy() {
		return passesAccuracy;
	}

	public void setPassesAccuracy(BigDecimal passesAccuracy) {
		this.passesAccuracy = passesAccuracy;
	}

	public Integer getTacklesTotal() {
		return tacklesTotal;
	}

	public void setTacklesTotal(Integer tacklesTotal) {
		this.tacklesTotal = tacklesTotal;
	}

	public Integer getTacklesBlocks() {
		return tacklesBlocks;
	}

	public void setTacklesBlocks(Integer tacklesBlocks) {
		this.tacklesBlocks = tacklesBlocks;
	}

	public Integer getInterceptions() {
		return interceptions;
	}

	public void setInterceptions(Integer interceptions) {
		this.interceptions = interceptions;
	}

	public Integer getDuelsTotal() {
		return duelsTotal;
	}

	public void setDuelsTotal(Integer duelsTotal) {
		this.duelsTotal = duelsTotal;
	}

	public Integer getDuelsWon() {
		return duelsWon;
	}

	public void setDuelsWon(Integer duelsWon) {
		this.duelsWon = duelsWon;
	}

	public Integer getDribblesAtt() {
		return dribblesAtt;
	}

	public void setDribblesAtt(Integer dribblesAtt) {
		this.dribblesAtt = dribblesAtt;
	}

	public Integer getDribblesSuc() {
		return dribblesSuc;
	}

	public void setDribblesSuc(Integer dribblesSuc) {
		this.dribblesSuc = dribblesSuc;
	}

	public Integer getDribblesPast() {
		return dribblesPast;
	}

	public void setDribblesPast(Integer dribblesPast) {
		this.dribblesPast = dribblesPast;
	}

	public Integer getFoulsDrawn() {
		return foulsDrawn;
	}

	public void setFoulsDrawn(Integer foulsDrawn) {
		this.foulsDrawn = foulsDrawn;
	}

	public Integer getFoulsCommitted() {
		return foulsCommitted;
	}

	public void setFoulsCommitted(Integer foulsCommitted) {
		this.foulsCommitted = foulsCommitted;
	}

	public Integer getYellowCards() {
		return yellowCards;
	}

	public void setYellowCards(Integer yellowCards) {
		this.yellowCards = yellowCards;
	}

	public Integer getRedCards() {
		return redCards;
	}

	public void setRedCards(Integer redCards) {
		this.redCards = redCards;
	}

	public Integer getYellowRedCards() {
		return yellowRedCards;
	}

	public void setYellowRedCards(Integer yellowRedCards) {
		this.yellowRedCards = yellowRedCards;
	}

	public Integer getPenaltyWon() {
		return penaltyWon;
	}

	public void setPenaltyWon(Integer penaltyWon) {
		this.penaltyWon = penaltyWon;
	}

	public Integer getPenaltyScored() {
		return penaltyScored;
	}

	public void setPenaltyScored(Integer penaltyScored) {
		this.penaltyScored = penaltyScored;
	}

	public Integer getPenaltyMissed() {
		return penaltyMissed;
	}

	public void setPenaltyMissed(Integer penaltyMissed) {
		this.penaltyMissed = penaltyMissed;
	}

	public Integer getPenaltySaved() {
		return penaltySaved;
	}

	public void setPenaltySaved(Integer penaltySaved) {
		this.penaltySaved = penaltySaved;
	}

	public Integer getPenaltyCommitted() {
		return penaltyCommitted;
	}

	public void setPenaltyCommitted(Integer penaltyCommitted) {
		this.penaltyCommitted = penaltyCommitted;
	}
    
    
	
}
