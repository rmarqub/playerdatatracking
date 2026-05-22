package com.playerdatatracking.responses;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlayerMarketValue {

    @JsonAlias("index_id")
    private Long indexId;

    @JsonAlias("player_name")
    private String playerName;

    private Integer age;

    private String position;

    private Boolean injured;

    @JsonAlias("team_name")
    private String teamName;

    @JsonAlias("league_name")
    private String leagueName;

    @JsonAlias("league_tier")
    private Integer leagueTier;

    @JsonAlias("tier_factor")
    private Double tierFactor;

    @JsonAlias("performance_score")
    private Double performanceScore;

    @JsonAlias("age_factor")
    private Double ageFactor;

    @JsonAlias("minutes_factor")
    private Double minutesFactor;

    @JsonAlias("injury_penalty")
    private Double injuryPenalty;

    @JsonAlias("market_value")
    private Long marketValue;

    @JsonAlias("market_value_fmt")
    private String marketValueFmt;

    private String season;

    private String note;

    public Long getIndexId() { return indexId; }
    public void setIndexId(Long indexId) { this.indexId = indexId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public Boolean getInjured() { return injured; }
    public void setInjured(Boolean injured) { this.injured = injured; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getLeagueName() { return leagueName; }
    public void setLeagueName(String leagueName) { this.leagueName = leagueName; }

    public Integer getLeagueTier() { return leagueTier; }
    public void setLeagueTier(Integer leagueTier) { this.leagueTier = leagueTier; }

    public Double getTierFactor() { return tierFactor; }
    public void setTierFactor(Double tierFactor) { this.tierFactor = tierFactor; }

    public Double getPerformanceScore() { return performanceScore; }
    public void setPerformanceScore(Double performanceScore) { this.performanceScore = performanceScore; }

    public Double getAgeFactor() { return ageFactor; }
    public void setAgeFactor(Double ageFactor) { this.ageFactor = ageFactor; }

    public Double getMinutesFactor() { return minutesFactor; }
    public void setMinutesFactor(Double minutesFactor) { this.minutesFactor = minutesFactor; }

    public Double getInjuryPenalty() { return injuryPenalty; }
    public void setInjuryPenalty(Double injuryPenalty) { this.injuryPenalty = injuryPenalty; }

    public Long getMarketValue() { return marketValue; }
    public void setMarketValue(Long marketValue) { this.marketValue = marketValue; }

    public String getMarketValueFmt() { return marketValueFmt; }
    public void setMarketValueFmt(String marketValueFmt) { this.marketValueFmt = marketValueFmt; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
