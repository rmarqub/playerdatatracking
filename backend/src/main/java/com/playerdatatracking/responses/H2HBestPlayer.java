package com.playerdatatracking.responses;

public class H2HBestPlayer {
    private Long playerId;
    private String playerName;
    private Long teamId;
    private Double rating;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
}
