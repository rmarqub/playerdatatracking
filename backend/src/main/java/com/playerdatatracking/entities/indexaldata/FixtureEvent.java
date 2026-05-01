package com.playerdatatracking.entities.indexaldata;


import jakarta.persistence.*;

@Entity
@Table(name = "fixture_event")
public class FixtureEvent {

	
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fixture_event_seq")
    @SequenceGenerator(name = "fixture_event_seq", sequenceName = "fixture_event_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Long id;
 
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;
 
    @Column(name = "team_id")
    private Long teamId;
 
    @Column(name = "time_elapsed")
    private Integer timeElapsed;
 
    @Column(name = "time_extra")
    private Integer timeExtra;
 
    // FK a player.index_id — puede ser null si el jugador no está indexado en el sistema
    @Column(name = "player_id")
    private Long playerId;
 
    @Column(name = "player_name")
    private String playerName;
 
    // FK a player.index_id del asistente
    @Column(name = "assist_id")
    private Long assistId;
 
    @Column(name = "assist_name")
    private String assistName;
 
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // Goal, Card, subst, Var
 
    @Column(name = "event_detail", length = 100)
    private String eventDetail; // Normal Goal, Yellow Card, Substitution 1...
 
    @Column(name = "comments")
    private String comments;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Fixture getFixture() {
		return fixture;
	}

	public void setFixture(Fixture fixture) {
		this.fixture = fixture;
	}

	public Long getTeamId() {
		return teamId;
	}

	public void setTeamId(Long teamId) {
		this.teamId = teamId;
	}

	public Integer getTimeElapsed() {
		return timeElapsed;
	}

	public void setTimeElapsed(Integer timeElapsed) {
		this.timeElapsed = timeElapsed;
	}

	public Integer getTimeExtra() {
		return timeExtra;
	}

	public void setTimeExtra(Integer timeExtra) {
		this.timeExtra = timeExtra;
	}

	public Long getPlayerId() {
		return playerId;
	}

	public void setPlayerId(Long playerId) {
		this.playerId = playerId;
	}

	public String getPlayerName() {
		return playerName;
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	public Long getAssistId() {
		return assistId;
	}

	public void setAssistId(Long assistId) {
		this.assistId = assistId;
	}

	public String getAssistName() {
		return assistName;
	}

	public void setAssistName(String assistName) {
		this.assistName = assistName;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public String getEventDetail() {
		return eventDetail;
	}

	public void setEventDetail(String eventDetail) {
		this.eventDetail = eventDetail;
	}

	public String getComments() {
		return comments;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}
    
    
}
