package com.playerdatatracking.responses;

public class AnalysisMatchResult {

    private Long    fixtureId;
    private String  homeTeamName;
    private String  awayTeamName;
    private String  matchDate;
    private Integer goalsHome;
    private Integer goalsAway;

    private String  actualResult;    // home_win | draw | away_win

    private Double  baseHomeWin;
    private Double  baseDraw;
    private Double  baseAwayWin;

    private Double  adjHomeWin;
    private Double  adjDraw;
    private Double  adjAwayWin;
    private String  adjPredicted;

    private Double  netDelta;
    private boolean correct;
    private boolean baseCorrect;

    private Double  brierScore;      // per-match Brier (adjusted probs)
    private Double  logLoss;         // per-match log-loss (adjusted probs)

    public Long    getFixtureId()               { return fixtureId; }
    public void    setFixtureId(Long v)         { fixtureId = v; }
    public String  getHomeTeamName()            { return homeTeamName; }
    public void    setHomeTeamName(String v)    { homeTeamName = v; }
    public String  getAwayTeamName()            { return awayTeamName; }
    public void    setAwayTeamName(String v)    { awayTeamName = v; }
    public String  getMatchDate()               { return matchDate; }
    public void    setMatchDate(String v)       { matchDate = v; }
    public Integer getGoalsHome()               { return goalsHome; }
    public void    setGoalsHome(Integer v)      { goalsHome = v; }
    public Integer getGoalsAway()               { return goalsAway; }
    public void    setGoalsAway(Integer v)      { goalsAway = v; }
    public String  getActualResult()            { return actualResult; }
    public void    setActualResult(String v)    { actualResult = v; }
    public Double  getBaseHomeWin()             { return baseHomeWin; }
    public void    setBaseHomeWin(Double v)     { baseHomeWin = v; }
    public Double  getBaseDraw()                { return baseDraw; }
    public void    setBaseDraw(Double v)        { baseDraw = v; }
    public Double  getBaseAwayWin()             { return baseAwayWin; }
    public void    setBaseAwayWin(Double v)     { baseAwayWin = v; }
    public Double  getAdjHomeWin()              { return adjHomeWin; }
    public void    setAdjHomeWin(Double v)      { adjHomeWin = v; }
    public Double  getAdjDraw()                 { return adjDraw; }
    public void    setAdjDraw(Double v)         { adjDraw = v; }
    public Double  getAdjAwayWin()              { return adjAwayWin; }
    public void    setAdjAwayWin(Double v)      { adjAwayWin = v; }
    public String  getAdjPredicted()            { return adjPredicted; }
    public void    setAdjPredicted(String v)    { adjPredicted = v; }
    public Double  getNetDelta()                { return netDelta; }
    public void    setNetDelta(Double v)        { netDelta = v; }
    public boolean isCorrect()                  { return correct; }
    public void    setCorrect(boolean v)        { correct = v; }
    public boolean isBaseCorrect()              { return baseCorrect; }
    public void    setBaseCorrect(boolean v)    { baseCorrect = v; }
    public Double  getBrierScore()              { return brierScore; }
    public void    setBrierScore(Double v)      { brierScore = v; }
    public Double  getLogLoss()                 { return logLoss; }
    public void    setLogLoss(Double v)         { logLoss = v; }
}
