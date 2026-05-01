package com.playerdatatracking.entities.indexaldata;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
 
@Entity
@Table(name = "fixture")
public class Fixture {

	 
    @Id
    @Column(name = "id", nullable = false)
    private Long id; // ID externo de api-football, no autogenerado
 
    @Column(name = "league_id", nullable = false)
    private Integer leagueId;
 
    @Column(name = "league_name")
    private String leagueName;
 
    @Column(name = "season", nullable = false)
    private Integer season;
 
    @Column(name = "round")
    private String round;
 
    @Column(name = "match_date", nullable = false)
    private OffsetDateTime matchDate;
 
    @Column(name = "match_timestamp")
    private Long matchTimestamp;
 
    @Column(name = "status_short", nullable = false, length = 10)
    private String statusShort;
 
    @Column(name = "status_long")
    private String statusLong;
 
    @Column(name = "status_elapsed")
    private Integer statusElapsed;
 
    @Column(name = "status_extra")
    private Integer statusExtra;
 
    @Column(name = "referee")
    private String referee;
 
    @Column(name = "venue_id")
    private Integer venueId;
 
    @Column(name = "venue_name")
    private String venueName;
 
    @Column(name = "venue_city")
    private String venueCity;
 
    @Column(name = "home_team_id", nullable = false)
    private Long homeTeamId;
 
    @Column(name = "home_team_name")
    private String homeTeamName;
 
    @Column(name = "away_team_id", nullable = false)
    private Long awayTeamId;
 
    @Column(name = "away_team_name")
    private String awayTeamName;
 
    @Column(name = "goals_home")
    private Integer goalsHome;
 
    @Column(name = "goals_away")
    private Integer goalsAway;
 
    @Column(name = "score_ht_home")
    private Integer scoreHtHome;
 
    @Column(name = "score_ht_away")
    private Integer scoreHtAway;
 
    @Column(name = "score_ft_home")
    private Integer scoreFtHome;
 
    @Column(name = "score_ft_away")
    private Integer scoreFtAway;
 
    @Column(name = "score_et_home")
    private Integer scoreEtHome;
 
    @Column(name = "score_et_away")
    private Integer scoreEtAway;
 
    @Column(name = "score_pen_home")
    private Integer scorePenHome;
 
    @Column(name = "score_pen_away")
    private Integer scorePenAway;
 
    @Column(name = "ingested_at", updatable = false)
    private OffsetDateTime ingestedAt;
 
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
 
    @OneToMany(mappedBy = "fixture", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FixtureEvent> events = new ArrayList<>();
 
    @OneToMany(mappedBy = "fixture", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FixtureTeamStats> teamStats = new ArrayList<>();
 
    @OneToMany(mappedBy = "fixture", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FixturePlayerStats> playerStats = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        this.ingestedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
 
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Integer getLeagueId() {
		return leagueId;
	}

	public void setLeagueId(Integer leagueId) {
		this.leagueId = leagueId;
	}

	public String getLeagueName() {
		return leagueName;
	}

	public void setLeagueName(String leagueName) {
		this.leagueName = leagueName;
	}

	public Integer getSeason() {
		return season;
	}

	public void setSeason(Integer season) {
		this.season = season;
	}

	public String getRound() {
		return round;
	}

	public void setRound(String round) {
		this.round = round;
	}

	public OffsetDateTime getMatchDate() {
		return matchDate;
	}

	public void setMatchDate(OffsetDateTime matchDate) {
		this.matchDate = matchDate;
	}

	public Long getMatchTimestamp() {
		return matchTimestamp;
	}

	public void setMatchTimestamp(Long matchTimestamp) {
		this.matchTimestamp = matchTimestamp;
	}

	public String getStatusShort() {
		return statusShort;
	}

	public void setStatusShort(String statusShort) {
		this.statusShort = statusShort;
	}

	public String getStatusLong() {
		return statusLong;
	}

	public void setStatusLong(String statusLong) {
		this.statusLong = statusLong;
	}

	public Integer getStatusElapsed() {
		return statusElapsed;
	}

	public void setStatusElapsed(Integer statusElapsed) {
		this.statusElapsed = statusElapsed;
	}

	public Integer getStatusExtra() {
		return statusExtra;
	}

	public void setStatusExtra(Integer statusExtra) {
		this.statusExtra = statusExtra;
	}

	public String getReferee() {
		return referee;
	}

	public void setReferee(String referee) {
		this.referee = referee;
	}

	public Integer getVenueId() {
		return venueId;
	}

	public void setVenueId(Integer venueId) {
		this.venueId = venueId;
	}

	public String getVenueName() {
		return venueName;
	}

	public void setVenueName(String venueName) {
		this.venueName = venueName;
	}

	public String getVenueCity() {
		return venueCity;
	}

	public void setVenueCity(String venueCity) {
		this.venueCity = venueCity;
	}

	public Long getHomeTeamId() {
		return homeTeamId;
	}

	public void setHomeTeamId(Long homeTeamId) {
		this.homeTeamId = homeTeamId;
	}

	public String getHomeTeamName() {
		return homeTeamName;
	}

	public void setHomeTeamName(String homeTeamName) {
		this.homeTeamName = homeTeamName;
	}

	public Long getAwayTeamId() {
		return awayTeamId;
	}

	public void setAwayTeamId(Long awayTeamId) {
		this.awayTeamId = awayTeamId;
	}

	public String getAwayTeamName() {
		return awayTeamName;
	}

	public void setAwayTeamName(String awayTeamName) {
		this.awayTeamName = awayTeamName;
	}

	public Integer getGoalsHome() {
		return goalsHome;
	}

	public void setGoalsHome(Integer goalsHome) {
		this.goalsHome = goalsHome;
	}

	public Integer getGoalsAway() {
		return goalsAway;
	}

	public void setGoalsAway(Integer goalsAway) {
		this.goalsAway = goalsAway;
	}

	public Integer getScoreHtHome() {
		return scoreHtHome;
	}

	public void setScoreHtHome(Integer scoreHtHome) {
		this.scoreHtHome = scoreHtHome;
	}

	public Integer getScoreHtAway() {
		return scoreHtAway;
	}

	public void setScoreHtAway(Integer scoreHtAway) {
		this.scoreHtAway = scoreHtAway;
	}

	public Integer getScoreFtHome() {
		return scoreFtHome;
	}

	public void setScoreFtHome(Integer scoreFtHome) {
		this.scoreFtHome = scoreFtHome;
	}

	public Integer getScoreFtAway() {
		return scoreFtAway;
	}

	public void setScoreFtAway(Integer scoreFtAway) {
		this.scoreFtAway = scoreFtAway;
	}

	public Integer getScoreEtHome() {
		return scoreEtHome;
	}

	public void setScoreEtHome(Integer scoreEtHome) {
		this.scoreEtHome = scoreEtHome;
	}

	public Integer getScoreEtAway() {
		return scoreEtAway;
	}

	public void setScoreEtAway(Integer scoreEtAway) {
		this.scoreEtAway = scoreEtAway;
	}

	public Integer getScorePenHome() {
		return scorePenHome;
	}

	public void setScorePenHome(Integer scorePenHome) {
		this.scorePenHome = scorePenHome;
	}

	public Integer getScorePenAway() {
		return scorePenAway;
	}

	public void setScorePenAway(Integer scorePenAway) {
		this.scorePenAway = scorePenAway;
	}

	public OffsetDateTime getIngestedAt() {
		return ingestedAt;
	}

	public void setIngestedAt(OffsetDateTime ingestedAt) {
		this.ingestedAt = ingestedAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(OffsetDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public List<FixtureEvent> getEvents() {
		return events;
	}

	public void setEvents(List<FixtureEvent> events) {
		this.events = events;
	}

	public List<FixtureTeamStats> getTeamStats() {
		return teamStats;
	}

	public void setTeamStats(List<FixtureTeamStats> teamStats) {
		this.teamStats = teamStats;
	}

	public List<FixturePlayerStats> getPlayerStats() {
		return playerStats;
	}

	public void setPlayerStats(List<FixturePlayerStats> playerStats) {
		this.playerStats = playerStats;
	}
	
}
