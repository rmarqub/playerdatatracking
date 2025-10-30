package com.playerdatatracking.entities.indexaldata;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.Type;


import jakarta.persistence.*;

@Entity
@Table(name = "squad")
public class Squad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team", nullable = false)
    private Long team;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "players", nullable = false, columnDefinition = "bigint[]")
    private Long[] players;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getTeam() {
		return team;
	}

	public void setTeam(Long team) {
		this.team = team;
	}

	public Long[] getPlayers() {
		return players;
	}

	public void setPlayers(Long[] players) {
		this.players = players;
	}
    
    
}
