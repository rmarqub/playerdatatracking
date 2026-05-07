package com.playerdatatracking.responses;

public class H2HGoalScorer {
    private Long playerId;
    private String playerName;
    private Long teamId;
    private Integer minute;
    private Integer minuteExtra;
    private String detail;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Integer getMinute() { return minute; }
    public void setMinute(Integer minute) { this.minute = minute; }
    public Integer getMinuteExtra() { return minuteExtra; }
    public void setMinuteExtra(Integer minuteExtra) { this.minuteExtra = minuteExtra; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
