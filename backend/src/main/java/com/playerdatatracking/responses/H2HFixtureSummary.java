package com.playerdatatracking.responses;

import java.util.List;

public class H2HFixtureSummary {
    private Long fixtureId;
    private String matchDate;
    private Integer season;
    private String leagueName;
    private String round;
    private Long homeTeamId;
    private String homeTeamName;
    private Long awayTeamId;
    private String awayTeamName;
    private Integer goalsHome;
    private Integer goalsAway;
    private List<H2HGoalScorer> scorers;
    private List<H2HBestPlayer> bestPlayers;

    public Long getFixtureId() { return fixtureId; }
    public void setFixtureId(Long fixtureId) { this.fixtureId = fixtureId; }
    public String getMatchDate() { return matchDate; }
    public void setMatchDate(String matchDate) { this.matchDate = matchDate; }
    public Integer getSeason() { return season; }
    public void setSeason(Integer season) { this.season = season; }
    public String getLeagueName() { return leagueName; }
    public void setLeagueName(String leagueName) { this.leagueName = leagueName; }
    public String getRound() { return round; }
    public void setRound(String round) { this.round = round; }
    public Long getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(Long homeTeamId) { this.homeTeamId = homeTeamId; }
    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }
    public Long getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(Long awayTeamId) { this.awayTeamId = awayTeamId; }
    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }
    public Integer getGoalsHome() { return goalsHome; }
    public void setGoalsHome(Integer goalsHome) { this.goalsHome = goalsHome; }
    public Integer getGoalsAway() { return goalsAway; }
    public void setGoalsAway(Integer goalsAway) { this.goalsAway = goalsAway; }
    public List<H2HGoalScorer> getScorers() { return scorers; }
    public void setScorers(List<H2HGoalScorer> scorers) { this.scorers = scorers; }
    public List<H2HBestPlayer> getBestPlayers() { return bestPlayers; }
    public void setBestPlayers(List<H2HBestPlayer> bestPlayers) { this.bestPlayers = bestPlayers; }
}
