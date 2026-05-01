package com.playerdatatracking.entities.indexaldata;


import java.io.Serializable;
import java.util.Objects;


public class FixturePlayerStatsId implements Serializable {
	
    private Long fixture;
    private Long playerId;
 
    public FixturePlayerStatsId() {}
 
    public FixturePlayerStatsId(Long fixture, Long playerId) {
        this.fixture = fixture;
        this.playerId = playerId;
    }
 
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixturePlayerStatsId)) return false;
        FixturePlayerStatsId that = (FixturePlayerStatsId) o;
        return Objects.equals(fixture, that.fixture) && Objects.equals(playerId, that.playerId);
    }
 
    @Override
    public int hashCode() {
        return Objects.hash(fixture, playerId);
    }
 
    public Long getFixture() { return fixture; }
    public void setFixture(Long fixture) { this.fixture = fixture; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
}
