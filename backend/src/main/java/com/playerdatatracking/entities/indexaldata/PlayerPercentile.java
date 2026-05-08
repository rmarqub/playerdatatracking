package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "player_percentiles")
public class PlayerPercentile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "index_id")
    private Long indexId;

    /** 0 = percentil global (todos los jugadores de la temporada) */
    @Column(name = "league_id", nullable = false)
    private Integer leagueId;

    @Column(name = "season", nullable = false)
    private String season;

    @Column(name = "pct_minutes")
    private Integer pctMinutes;

    @Column(name = "pct_rating")
    private Integer pctRating;

    @Column(name = "pct_goals_p90")
    private Integer pctGoalsP90;

    @Column(name = "pct_assists_p90")
    private Integer pctAssistsP90;

    @Column(name = "pct_shots_total_p90")
    private Integer pctShotsTotalP90;

    @Column(name = "pct_shots_on_p90")
    private Integer pctShotsOnP90;

    @Column(name = "pct_passes_total_p90")
    private Integer pctPassesTotalP90;

    @Column(name = "pct_passes_key_p90")
    private Integer pctPassesKeyP90;

    @Column(name = "pct_pass_accuracy")
    private Integer pctPassAccuracy;

    @Column(name = "pct_tackles_p90")
    private Integer pctTacklesP90;

    @Column(name = "pct_interceptions_p90")
    private Integer pctInterceptionsP90;

    @Column(name = "pct_duels_won")
    private Integer pctDuelsWon;

    @Column(name = "pct_dribbles_success")
    private Integer pctDribblesSuccess;

    @Column(name = "pct_fouls_drawn_p90")
    private Integer pctFoulsDrawnP90;

    @Column(name = "computed_at")
    private Timestamp computedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public Long getIndexId() { return indexId; }
    public void setIndexId(Long indexId) { this.indexId = indexId; }

    public Integer getLeagueId() { return leagueId; }
    public void setLeagueId(Integer leagueId) { this.leagueId = leagueId; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public Integer getPctMinutes() { return pctMinutes; }
    public void setPctMinutes(Integer v) { this.pctMinutes = v; }

    public Integer getPctRating() { return pctRating; }
    public void setPctRating(Integer v) { this.pctRating = v; }

    public Integer getPctGoalsP90() { return pctGoalsP90; }
    public void setPctGoalsP90(Integer v) { this.pctGoalsP90 = v; }

    public Integer getPctAssistsP90() { return pctAssistsP90; }
    public void setPctAssistsP90(Integer v) { this.pctAssistsP90 = v; }

    public Integer getPctShotsTotalP90() { return pctShotsTotalP90; }
    public void setPctShotsTotalP90(Integer v) { this.pctShotsTotalP90 = v; }

    public Integer getPctShotsOnP90() { return pctShotsOnP90; }
    public void setPctShotsOnP90(Integer v) { this.pctShotsOnP90 = v; }

    public Integer getPctPassesTotalP90() { return pctPassesTotalP90; }
    public void setPctPassesTotalP90(Integer v) { this.pctPassesTotalP90 = v; }

    public Integer getPctPassesKeyP90() { return pctPassesKeyP90; }
    public void setPctPassesKeyP90(Integer v) { this.pctPassesKeyP90 = v; }

    public Integer getPctPassAccuracy() { return pctPassAccuracy; }
    public void setPctPassAccuracy(Integer v) { this.pctPassAccuracy = v; }

    public Integer getPctTacklesP90() { return pctTacklesP90; }
    public void setPctTacklesP90(Integer v) { this.pctTacklesP90 = v; }

    public Integer getPctInterceptionsP90() { return pctInterceptionsP90; }
    public void setPctInterceptionsP90(Integer v) { this.pctInterceptionsP90 = v; }

    public Integer getPctDuelsWon() { return pctDuelsWon; }
    public void setPctDuelsWon(Integer v) { this.pctDuelsWon = v; }

    public Integer getPctDribblesSuccess() { return pctDribblesSuccess; }
    public void setPctDribblesSuccess(Integer v) { this.pctDribblesSuccess = v; }

    public Integer getPctFoulsDrawnP90() { return pctFoulsDrawnP90; }
    public void setPctFoulsDrawnP90(Integer v) { this.pctFoulsDrawnP90 = v; }

    public Timestamp getComputedAt() { return computedAt; }
    public void setComputedAt(Timestamp computedAt) { this.computedAt = computedAt; }
}
