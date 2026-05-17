package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "fixture_contextual_analysis",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_fca_fixture_user",
        columnNames = {"fixture_id", "user_id"}
    )
)
public class FixtureContextualAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(
            name = "fixture_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false
        )
    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // ---- Home metrics --------------------------------------------------------

    @Column(name = "home_current_form")
    private Integer homeCurrentForm = 3;

    @Column(name = "home_stadium_atmosphere")
    private Integer homeStadiumAtmosphere = 3;

    @Column(name = "home_defensive_block")
    private Integer homeDefensiveBlock = 3;

    @Column(name = "home_offensive_rhythm")
    private Integer homeOffensiveRhythm = 3;

    @Column(name = "home_team_needs")
    private Integer homeTeamNeeds = 3;

    @Column(name = "home_set_pieces")
    private Integer homeSetPieces = 2;

    @Column(name = "home_fatigue")
    private Integer homeFatigue = 3;

    @Column(name = "home_unavailable_players", columnDefinition = "TEXT")
    private String homeUnavailablePlayers;

    // ---- Away metrics --------------------------------------------------------

    @Column(name = "away_current_form")
    private Integer awayCurrentForm = 3;

    @Column(name = "away_stadium_atmosphere")
    private Integer awayStadiumAtmosphere = 3;

    @Column(name = "away_defensive_block")
    private Integer awayDefensiveBlock = 3;

    @Column(name = "away_offensive_rhythm")
    private Integer awayOffensiveRhythm = 3;

    @Column(name = "away_team_needs")
    private Integer awayTeamNeeds = 3;

    @Column(name = "away_set_pieces")
    private Integer awaySetPieces = 2;

    @Column(name = "away_fatigue")
    private Integer awayFatigue = 3;

    @Column(name = "away_unavailable_players", columnDefinition = "TEXT")
    private String awayUnavailablePlayers;

    // ---- General -------------------------------------------------------------

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ---- Base model snapshot (stored at analysis time) -----------------------

    @Column(name = "base_home_win")
    private Float baseHomeWin;

    @Column(name = "base_draw")
    private Float baseDraw;

    @Column(name = "base_away_win")
    private Float baseAwayWin;

    // ---- Adjusted predictions snapshot (stored at save time with weights of that moment) ---

    @Column(name = "adj_home_win")
    private Float adjHomeWin;

    @Column(name = "adj_draw")
    private Float adjDraw;

    @Column(name = "adj_away_win")
    private Float adjAwayWin;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ---- Getters / Setters ---------------------------------------------------

    public Long getId()                         { return id; }
    public void setId(Long v)                   { id = v; }
    public Long getFixtureId()                  { return fixtureId; }
    public void setFixtureId(Long v)            { fixtureId = v; }
    public Long getUserId()                     { return userId; }
    public void setUserId(Long v)               { userId = v; }

    public Integer getHomeCurrentForm()         { return homeCurrentForm; }
    public void setHomeCurrentForm(Integer v)   { homeCurrentForm = v; }
    public Integer getHomeStadiumAtmosphere()   { return homeStadiumAtmosphere; }
    public void setHomeStadiumAtmosphere(Integer v) { homeStadiumAtmosphere = v; }
    public Integer getHomeDefensiveBlock()      { return homeDefensiveBlock; }
    public void setHomeDefensiveBlock(Integer v){ homeDefensiveBlock = v; }
    public Integer getHomeOffensiveRhythm()     { return homeOffensiveRhythm; }
    public void setHomeOffensiveRhythm(Integer v){ homeOffensiveRhythm = v; }
    public Integer getHomeTeamNeeds()           { return homeTeamNeeds; }
    public void setHomeTeamNeeds(Integer v)     { homeTeamNeeds = v; }
    public Integer getHomeSetPieces()           { return homeSetPieces; }
    public void setHomeSetPieces(Integer v)     { homeSetPieces = v; }
    public Integer getHomeFatigue()             { return homeFatigue; }
    public void setHomeFatigue(Integer v)       { homeFatigue = v; }
    public String getHomeUnavailablePlayers()   { return homeUnavailablePlayers; }
    public void setHomeUnavailablePlayers(String v) { homeUnavailablePlayers = v; }

    public Integer getAwayCurrentForm()         { return awayCurrentForm; }
    public void setAwayCurrentForm(Integer v)   { awayCurrentForm = v; }
    public Integer getAwayStadiumAtmosphere()   { return awayStadiumAtmosphere; }
    public void setAwayStadiumAtmosphere(Integer v) { awayStadiumAtmosphere = v; }
    public Integer getAwayDefensiveBlock()      { return awayDefensiveBlock; }
    public void setAwayDefensiveBlock(Integer v){ awayDefensiveBlock = v; }
    public Integer getAwayOffensiveRhythm()     { return awayOffensiveRhythm; }
    public void setAwayOffensiveRhythm(Integer v){ awayOffensiveRhythm = v; }
    public Integer getAwayTeamNeeds()           { return awayTeamNeeds; }
    public void setAwayTeamNeeds(Integer v)     { awayTeamNeeds = v; }
    public Integer getAwaySetPieces()           { return awaySetPieces; }
    public void setAwaySetPieces(Integer v)     { awaySetPieces = v; }
    public Integer getAwayFatigue()             { return awayFatigue; }
    public void setAwayFatigue(Integer v)       { awayFatigue = v; }
    public String getAwayUnavailablePlayers()   { return awayUnavailablePlayers; }
    public void setAwayUnavailablePlayers(String v) { awayUnavailablePlayers = v; }

    public String getNotes()                    { return notes; }
    public void setNotes(String v)              { notes = v; }
    public Float getBaseHomeWin()               { return baseHomeWin; }
    public void setBaseHomeWin(Float v)         { baseHomeWin = v; }
    public Float getBaseDraw()                  { return baseDraw; }
    public void setBaseDraw(Float v)            { baseDraw = v; }
    public Float getBaseAwayWin()               { return baseAwayWin; }
    public void setBaseAwayWin(Float v)         { baseAwayWin = v; }
    public Float getAdjHomeWin()               { return adjHomeWin; }
    public void setAdjHomeWin(Float v)         { adjHomeWin = v; }
    public Float getAdjDraw()                  { return adjDraw; }
    public void setAdjDraw(Float v)            { adjDraw = v; }
    public Float getAdjAwayWin()               { return adjAwayWin; }
    public void setAdjAwayWin(Float v)         { adjAwayWin = v; }
    public OffsetDateTime getCreatedAt()        { return createdAt; }
    public OffsetDateTime getUpdatedAt()        { return updatedAt; }
}
