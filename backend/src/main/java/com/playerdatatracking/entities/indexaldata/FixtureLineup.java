package com.playerdatatracking.entities.indexaldata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "fixture_lineup")
public class FixtureLineup {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fixture_lineup_seq")
    @SequenceGenerator(name = "fixture_lineup_seq", sequenceName = "fixture_lineup_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "formation", length = 20)
    private String formation;

    @Column(name = "coach_id")
    private Long coachId;

    @Column(name = "coach_name", length = 100)
    private String coachName;

    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "player_name", length = 150)
    private String playerName;

    @Column(name = "player_number")
    private Integer playerNumber;

    @Column(name = "position", length = 10)
    private String position;

    @Column(name = "grid", length = 10)
    private String grid;

    @Column(name = "substitute", nullable = false)
    private Boolean substitute = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Fixture getFixture() { return fixture; }
    public void setFixture(Fixture fixture) { this.fixture = fixture; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getFormation() { return formation; }
    public void setFormation(String formation) { this.formation = formation; }
    public Long getCoachId() { return coachId; }
    public void setCoachId(Long coachId) { this.coachId = coachId; }
    public String getCoachName() { return coachName; }
    public void setCoachName(String coachName) { this.coachName = coachName; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public Integer getPlayerNumber() { return playerNumber; }
    public void setPlayerNumber(Integer playerNumber) { this.playerNumber = playerNumber; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getGrid() { return grid; }
    public void setGrid(String grid) { this.grid = grid; }
    public Boolean getSubstitute() { return substitute; }
    public void setSubstitute(Boolean substitute) { this.substitute = substitute; }
}
