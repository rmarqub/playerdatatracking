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

}
