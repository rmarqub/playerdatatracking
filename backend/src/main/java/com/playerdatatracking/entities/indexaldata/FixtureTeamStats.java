package com.playerdatatracking.entities.indexaldata;


import jakarta.persistence.*;
import java.math.BigDecimal;
 
@Entity
@Table(name = "fixture_team_stats")
@IdClass(FixtureTeamStatsId.class)
public class FixtureTeamStats {
	
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;
 
    @Id
    @Column(name = "team_id", nullable = false)
    private Long teamId;
 
    @Column(name = "shots_on_goal")
    private Integer shotsOnGoal;
 
    @Column(name = "shots_off_goal")
    private Integer shotsOffGoal;
 
    @Column(name = "shots_total")
    private Integer shotsTotal;
 
    @Column(name = "shots_blocked")
    private Integer shotsBlocked;
 
    @Column(name = "shots_inside_box")
    private Integer shotsInsideBox;
 
    @Column(name = "shots_outside_box")
    private Integer shotsOutsideBox;
 
    @Column(name = "fouls")
    private Integer fouls;
 
    @Column(name = "corner_kicks")
    private Integer cornerKicks;
 
    @Column(name = "offsides")
    private Integer offsides;
 
    @Column(name = "ball_possession", precision = 5, scale = 2)
    private BigDecimal ballPossession;
 
    @Column(name = "yellow_cards")
    private Integer yellowCards;
 
    @Column(name = "red_cards")
    private Integer redCards;
 
    @Column(name = "goalkeeper_saves")
    private Integer goalkeeperSaves;
 
    @Column(name = "total_passes")
    private Integer totalPasses;
 
    @Column(name = "passes_accurate")
    private Integer passesAccurate;
 
    @Column(name = "passes_pct", precision = 5, scale = 2)
    private BigDecimal passesPct;
 
    @Column(name = "expected_goals", precision = 5, scale = 2)
    private BigDecimal expectedGoals;
 
    @Column(name = "goals_prevented", precision = 5, scale = 2)
    private BigDecimal goalsPrevented;

    public Fixture getFixture() { return fixture; }
    public void setFixture(Fixture fixture) { this.fixture = fixture; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Integer getShotsOnGoal() { return shotsOnGoal; }
    public void setShotsOnGoal(Integer shotsOnGoal) { this.shotsOnGoal = shotsOnGoal; }
    public Integer getShotsOffGoal() { return shotsOffGoal; }
    public void setShotsOffGoal(Integer shotsOffGoal) { this.shotsOffGoal = shotsOffGoal; }
    public Integer getShotsTotal() { return shotsTotal; }
    public void setShotsTotal(Integer shotsTotal) { this.shotsTotal = shotsTotal; }
    public Integer getShotsBlocked() { return shotsBlocked; }
    public void setShotsBlocked(Integer shotsBlocked) { this.shotsBlocked = shotsBlocked; }
    public Integer getShotsInsideBox() { return shotsInsideBox; }
    public void setShotsInsideBox(Integer shotsInsideBox) { this.shotsInsideBox = shotsInsideBox; }
    public Integer getShotsOutsideBox() { return shotsOutsideBox; }
    public void setShotsOutsideBox(Integer shotsOutsideBox) { this.shotsOutsideBox = shotsOutsideBox; }
    public Integer getFouls() { return fouls; }
    public void setFouls(Integer fouls) { this.fouls = fouls; }
    public Integer getCornerKicks() { return cornerKicks; }
    public void setCornerKicks(Integer cornerKicks) { this.cornerKicks = cornerKicks; }
    public Integer getOffsides() { return offsides; }
    public void setOffsides(Integer offsides) { this.offsides = offsides; }
    public BigDecimal getBallPossession() { return ballPossession; }
    public void setBallPossession(BigDecimal ballPossession) { this.ballPossession = ballPossession; }
    public Integer getYellowCards() { return yellowCards; }
    public void setYellowCards(Integer yellowCards) { this.yellowCards = yellowCards; }
    public Integer getRedCards() { return redCards; }
    public void setRedCards(Integer redCards) { this.redCards = redCards; }
    public Integer getGoalkeeperSaves() { return goalkeeperSaves; }
    public void setGoalkeeperSaves(Integer goalkeeperSaves) { this.goalkeeperSaves = goalkeeperSaves; }
    public Integer getTotalPasses() { return totalPasses; }
    public void setTotalPasses(Integer totalPasses) { this.totalPasses = totalPasses; }
    public Integer getPassesAccurate() { return passesAccurate; }
    public void setPassesAccurate(Integer passesAccurate) { this.passesAccurate = passesAccurate; }
    public BigDecimal getPassesPct() { return passesPct; }
    public void setPassesPct(BigDecimal passesPct) { this.passesPct = passesPct; }
    public BigDecimal getExpectedGoals() { return expectedGoals; }
    public void setExpectedGoals(BigDecimal expectedGoals) { this.expectedGoals = expectedGoals; }
    public BigDecimal getGoalsPrevented() { return goalsPrevented; }
    public void setGoalsPrevented(BigDecimal goalsPrevented) { this.goalsPrevented = goalsPrevented; }
}
