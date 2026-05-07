package com.playerdatatracking.responses;

import java.util.List;

public class ContextualAnalysisData {

    private Long fixtureId;

    private Integer homeCurrentForm;
    private Integer homeStadiumAtmosphere;
    private Integer homeDefensiveBlock;
    private Integer homeOffensiveRhythm;
    private Integer homeTeamNeeds;
    private Integer homeSetPieces;
    private Integer homeFatigue;
    private List<String> homeUnavailablePlayers;

    private Integer awayCurrentForm;
    private Integer awayStadiumAtmosphere;
    private Integer awayDefensiveBlock;
    private Integer awayOffensiveRhythm;
    private Integer awayTeamNeeds;
    private Integer awaySetPieces;
    private Integer awayFatigue;
    private List<String> awayUnavailablePlayers;

    private String notes;
    private String updatedAt;
    private Float baseHomeWin;
    private Float baseDraw;
    private Float baseAwayWin;

    public Long getFixtureId()                          { return fixtureId; }
    public void setFixtureId(Long v)                    { fixtureId = v; }

    public Integer getHomeCurrentForm()                 { return homeCurrentForm; }
    public void setHomeCurrentForm(Integer v)           { homeCurrentForm = v; }
    public Integer getHomeStadiumAtmosphere()           { return homeStadiumAtmosphere; }
    public void setHomeStadiumAtmosphere(Integer v)     { homeStadiumAtmosphere = v; }
    public Integer getHomeDefensiveBlock()              { return homeDefensiveBlock; }
    public void setHomeDefensiveBlock(Integer v)        { homeDefensiveBlock = v; }
    public Integer getHomeOffensiveRhythm()             { return homeOffensiveRhythm; }
    public void setHomeOffensiveRhythm(Integer v)       { homeOffensiveRhythm = v; }
    public Integer getHomeTeamNeeds()                   { return homeTeamNeeds; }
    public void setHomeTeamNeeds(Integer v)             { homeTeamNeeds = v; }
    public Integer getHomeSetPieces()                   { return homeSetPieces; }
    public void setHomeSetPieces(Integer v)             { homeSetPieces = v; }
    public Integer getHomeFatigue()                     { return homeFatigue; }
    public void setHomeFatigue(Integer v)               { homeFatigue = v; }
    public List<String> getHomeUnavailablePlayers()     { return homeUnavailablePlayers; }
    public void setHomeUnavailablePlayers(List<String> v){ homeUnavailablePlayers = v; }

    public Integer getAwayCurrentForm()                 { return awayCurrentForm; }
    public void setAwayCurrentForm(Integer v)           { awayCurrentForm = v; }
    public Integer getAwayStadiumAtmosphere()           { return awayStadiumAtmosphere; }
    public void setAwayStadiumAtmosphere(Integer v)     { awayStadiumAtmosphere = v; }
    public Integer getAwayDefensiveBlock()              { return awayDefensiveBlock; }
    public void setAwayDefensiveBlock(Integer v)        { awayDefensiveBlock = v; }
    public Integer getAwayOffensiveRhythm()             { return awayOffensiveRhythm; }
    public void setAwayOffensiveRhythm(Integer v)       { awayOffensiveRhythm = v; }
    public Integer getAwayTeamNeeds()                   { return awayTeamNeeds; }
    public void setAwayTeamNeeds(Integer v)             { awayTeamNeeds = v; }
    public Integer getAwaySetPieces()                   { return awaySetPieces; }
    public void setAwaySetPieces(Integer v)             { awaySetPieces = v; }
    public Integer getAwayFatigue()                     { return awayFatigue; }
    public void setAwayFatigue(Integer v)               { awayFatigue = v; }
    public List<String> getAwayUnavailablePlayers()     { return awayUnavailablePlayers; }
    public void setAwayUnavailablePlayers(List<String> v){ awayUnavailablePlayers = v; }

    public String getNotes()                            { return notes; }
    public void setNotes(String v)                      { notes = v; }
    public String getUpdatedAt()                        { return updatedAt; }
    public void setUpdatedAt(String v)                  { updatedAt = v; }
    public Float getBaseHomeWin()                       { return baseHomeWin; }
    public void setBaseHomeWin(Float v)                 { baseHomeWin = v; }
    public Float getBaseDraw()                          { return baseDraw; }
    public void setBaseDraw(Float v)                    { baseDraw = v; }
    public Float getBaseAwayWin()                       { return baseAwayWin; }
    public void setBaseAwayWin(Float v)                 { baseAwayWin = v; }
}
