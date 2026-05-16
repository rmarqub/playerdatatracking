package com.playerdatatracking.responses;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchPrediction {

    @JsonAlias("fixture_id")
    private Long fixtureId;

    @JsonAlias("home_team")
    private String homeTeam;

    @JsonAlias("away_team")
    private String awayTeam;

    private String league;
    private Integer season;

    @JsonAlias("match_date")
    private String matchDate;

    private String status;

    @JsonAlias("result_1x2")
    private Result1x2 result1x2;

    private Goals goals;
    private Btts btts;
    private Corners corners;

    private List<String> warnings;

    // ---- Inner DTOs --------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result1x2 {

        @JsonAlias("home_win")
        private Double homeWin;

        private Double draw;

        @JsonAlias("away_win")
        private Double awayWin;

        private String predicted;
        private Double confidence;

        public Double getHomeWin()    { return homeWin; }
        public void setHomeWin(Double v) { homeWin = v; }
        public Double getDraw()       { return draw; }
        public void setDraw(Double v)    { draw = v; }
        public Double getAwayWin()    { return awayWin; }
        public void setAwayWin(Double v) { awayWin = v; }
        public String getPredicted()  { return predicted; }
        public void setPredicted(String v) { predicted = v; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double v) { confidence = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoalLine {
        private Double over;
        private Double under;
        private String predicted;

        public Double getOver()      { return over; }
        public void setOver(Double v)   { over = v; }
        public Double getUnder()     { return under; }
        public void setUnder(Double v)  { under = v; }
        public String getPredicted() { return predicted; }
        public void setPredicted(String v) { predicted = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Goals {

        @JsonAlias("expected_total")
        private Double expectedTotal;

        @JsonAlias("over_05")
        private GoalLine over05;

        @JsonAlias("over_15")
        private GoalLine over15;

        @JsonAlias("over_25")
        private GoalLine over25;

        @JsonAlias("over_35")
        private GoalLine over35;

        public Double  getExpectedTotal()      { return expectedTotal; }
        public void    setExpectedTotal(Double v) { expectedTotal = v; }
        public GoalLine getOver05()            { return over05; }
        public void    setOver05(GoalLine v)   { over05 = v; }
        public GoalLine getOver15()            { return over15; }
        public void    setOver15(GoalLine v)   { over15 = v; }
        public GoalLine getOver25()            { return over25; }
        public void    setOver25(GoalLine v)   { over25 = v; }
        public GoalLine getOver35()            { return over35; }
        public void    setOver35(GoalLine v)   { over35 = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Btts {

        private Double yes;
        private Double no;
        private String predicted;

        public Double getYes()       { return yes; }
        public void setYes(Double v)    { yes = v; }
        public Double getNo()        { return no; }
        public void setNo(Double v)     { no = v; }
        public String getPredicted() { return predicted; }
        public void setPredicted(String v) { predicted = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Corners {

        @JsonAlias("expected_total")
        private Double expectedTotal;

        @JsonAlias("over_3")  private Double over3;
        @JsonAlias("over_4")  private Double over4;
        @JsonAlias("over_5")  private Double over5;
        @JsonAlias("over_6")  private Double over6;
        @JsonAlias("over_7")  private Double over7;
        @JsonAlias("over_8")  private Double over8;
        @JsonAlias("over_9")  private Double over9;
        @JsonAlias("over_10") private Double over10;

        public Double getExpectedTotal()       { return expectedTotal; }
        public void   setExpectedTotal(Double v){ expectedTotal = v; }
        public Double getOver3()  { return over3; }
        public void   setOver3(Double v)  { over3 = v; }
        public Double getOver4()  { return over4; }
        public void   setOver4(Double v)  { over4 = v; }
        public Double getOver5()  { return over5; }
        public void   setOver5(Double v)  { over5 = v; }
        public Double getOver6()  { return over6; }
        public void   setOver6(Double v)  { over6 = v; }
        public Double getOver7()  { return over7; }
        public void   setOver7(Double v)  { over7 = v; }
        public Double getOver8()  { return over8; }
        public void   setOver8(Double v)  { over8 = v; }
        public Double getOver9()  { return over9; }
        public void   setOver9(Double v)  { over9 = v; }
        public Double getOver10() { return over10; }
        public void   setOver10(Double v) { over10 = v; }
    }

    // ---- Getters / Setters -------------------------------------------------

    public Long getFixtureId()       { return fixtureId; }
    public void setFixtureId(Long v)    { fixtureId = v; }
    public String getHomeTeam()      { return homeTeam; }
    public void setHomeTeam(String v)   { homeTeam = v; }
    public String getAwayTeam()      { return awayTeam; }
    public void setAwayTeam(String v)   { awayTeam = v; }
    public String getLeague()        { return league; }
    public void setLeague(String v)     { league = v; }
    public Integer getSeason()       { return season; }
    public void setSeason(Integer v)    { season = v; }
    public String getMatchDate()     { return matchDate; }
    public void setMatchDate(String v)  { matchDate = v; }
    public String getStatus()        { return status; }
    public void setStatus(String v)     { status = v; }
    public Result1x2 getResult1x2()  { return result1x2; }
    public void setResult1x2(Result1x2 v) { result1x2 = v; }
    public Goals getGoals()          { return goals; }
    public void setGoals(Goals v)       { goals = v; }
    public Btts getBtts()            { return btts; }
    public void setBtts(Btts v)         { btts = v; }
    public Corners getCorners()      { return corners; }
    public void setCorners(Corners v)   { corners = v; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> v) { warnings = v; }
}
