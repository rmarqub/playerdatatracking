package com.playerdatatracking.responses;

import java.util.List;

public class ContextualMatchPrediction {

    private Long   fixtureId;
    private String homeTeam;
    private String awayTeam;

    private boolean analysisFound;
    private double  netDelta;

    // ---- Base 1X2 ------------------------------------------------------------
    private Double baseHomeWin;
    private Double baseDraw;
    private Double baseAwayWin;

    // ---- Adjusted 1X2 --------------------------------------------------------
    private Double adjHomeWin;
    private Double adjDraw;
    private Double adjAwayWin;
    private String adjPredicted;   // home_win | draw | away_win
    private Double adjConfidence;  // max - second-highest adjusted prob

    // ---- Unchanged from base -------------------------------------------------
    private MatchPrediction.OverUnder25 overUnder25;
    private MatchPrediction.Btts        btts;

    private List<String> warnings;

    // ---- Getters / Setters ---------------------------------------------------
    public Long   getFixtureId()                       { return fixtureId; }
    public void   setFixtureId(Long v)                 { fixtureId = v; }
    public String getHomeTeam()                        { return homeTeam; }
    public void   setHomeTeam(String v)                { homeTeam = v; }
    public String getAwayTeam()                        { return awayTeam; }
    public void   setAwayTeam(String v)                { awayTeam = v; }
    public boolean isAnalysisFound()                   { return analysisFound; }
    public void    setAnalysisFound(boolean v)         { analysisFound = v; }
    public double  getNetDelta()                       { return netDelta; }
    public void    setNetDelta(double v)               { netDelta = v; }
    public Double  getBaseHomeWin()                    { return baseHomeWin; }
    public void    setBaseHomeWin(Double v)            { baseHomeWin = v; }
    public Double  getBaseDraw()                       { return baseDraw; }
    public void    setBaseDraw(Double v)               { baseDraw = v; }
    public Double  getBaseAwayWin()                    { return baseAwayWin; }
    public void    setBaseAwayWin(Double v)            { baseAwayWin = v; }
    public Double  getAdjHomeWin()                     { return adjHomeWin; }
    public void    setAdjHomeWin(Double v)             { adjHomeWin = v; }
    public Double  getAdjDraw()                        { return adjDraw; }
    public void    setAdjDraw(Double v)                { adjDraw = v; }
    public Double  getAdjAwayWin()                     { return adjAwayWin; }
    public void    setAdjAwayWin(Double v)             { adjAwayWin = v; }
    public String  getAdjPredicted()                   { return adjPredicted; }
    public void    setAdjPredicted(String v)           { adjPredicted = v; }
    public Double  getAdjConfidence()                  { return adjConfidence; }
    public void    setAdjConfidence(Double v)          { adjConfidence = v; }
    public MatchPrediction.OverUnder25 getOverUnder25(){ return overUnder25; }
    public void setOverUnder25(MatchPrediction.OverUnder25 v){ overUnder25 = v; }
    public MatchPrediction.Btts getBtts()              { return btts; }
    public void setBtts(MatchPrediction.Btts v)        { btts = v; }
    public List<String> getWarnings()                  { return warnings; }
    public void setWarnings(List<String> v)            { warnings = v; }
}
