package com.playerdatatracking.responses;

public class SquadPlayerEntry {
    private Long playerId;
    private String playerName;

    public SquadPlayerEntry() {}
    public SquadPlayerEntry(Long playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public Long getPlayerId()          { return playerId; }
    public void setPlayerId(Long v)    { playerId = v; }
    public String getPlayerName()      { return playerName; }
    public void setPlayerName(String v){ playerName = v; }
}
