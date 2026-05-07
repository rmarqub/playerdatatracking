package com.playerdatatracking.responses;

import java.util.List;

public class FixtureSquadData {
    private List<SquadPlayerEntry> homePlayers;
    private List<SquadPlayerEntry> awayPlayers;

    public List<SquadPlayerEntry> getHomePlayers()          { return homePlayers; }
    public void setHomePlayers(List<SquadPlayerEntry> v)    { homePlayers = v; }
    public List<SquadPlayerEntry> getAwayPlayers()          { return awayPlayers; }
    public void setAwayPlayers(List<SquadPlayerEntry> v)    { awayPlayers = v; }
}
