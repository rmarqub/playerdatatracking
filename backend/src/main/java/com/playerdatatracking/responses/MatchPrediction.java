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

    @JsonAlias("over_under_25")
    private OverUnder25 overUnder25;

    private Btts btts;

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
    public static class OverUnder25 {

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
    public OverUnder25 getOverUnder25() { return overUnder25; }
    public void setOverUnder25(OverUnder25 v) { overUnder25 = v; }
    public Btts getBtts()            { return btts; }
    public void setBtts(Btts v)         { btts = v; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> v) { warnings = v; }
}
