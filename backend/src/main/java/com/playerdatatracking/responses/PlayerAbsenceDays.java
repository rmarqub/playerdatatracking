package com.playerdatatracking.responses;

import java.time.OffsetDateTime;

public class PlayerAbsenceDays {

    private Long playerId;
    private String playerName;
    private Boolean injured;
    private Long daysAbsent;
    private OffsetDateTime lastMatchDate;
    private Long lastFixtureId;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public Boolean getInjured() { return injured; }
    public void setInjured(Boolean injured) { this.injured = injured; }

    public Long getDaysAbsent() { return daysAbsent; }
    public void setDaysAbsent(Long daysAbsent) { this.daysAbsent = daysAbsent; }

    public OffsetDateTime getLastMatchDate() { return lastMatchDate; }
    public void setLastMatchDate(OffsetDateTime lastMatchDate) { this.lastMatchDate = lastMatchDate; }

    public Long getLastFixtureId() { return lastFixtureId; }
    public void setLastFixtureId(Long lastFixtureId) { this.lastFixtureId = lastFixtureId; }
}
