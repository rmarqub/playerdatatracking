package com.playerdatatracking.entities.indexaldata;


import java.io.Serializable;
import java.util.Objects;

public class FixtureTeamStatsId implements Serializable {
	 
    private Long fixture;
    private Long teamId;
 
    public FixtureTeamStatsId() {}
 
    public FixtureTeamStatsId(Long fixture, Long teamId) {
        this.fixture = fixture;
        this.teamId = teamId;
    }
 
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixtureTeamStatsId)) return false;
        FixtureTeamStatsId that = (FixtureTeamStatsId) o;
        return Objects.equals(fixture, that.fixture) && Objects.equals(teamId, that.teamId);
    }
 
    @Override
    public int hashCode() {
        return Objects.hash(fixture, teamId);
    }
 
    public Long getFixture() { return fixture; }
    public void setFixture(Long fixture) { this.fixture = fixture; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
}
