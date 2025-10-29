package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfer")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player", nullable = false)
    private Long player;

    @Column(name = "in", nullable = false)
    private Long inPlayer;

    @Column(name = "out", nullable = false)
    private Long outPlayer;

    @Column(name = "kind")
    private TipoTransfer kind;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getPlayer() {
		return player;
	}

	public void setPlayer(Long player) {
		this.player = player;
	}

	public Long getInPlayer() {
		return inPlayer;
	}

	public void setInPlayer(Long inPlayer) {
		this.inPlayer = inPlayer;
	}

	public Long getOutPlayer() {
		return outPlayer;
	}

	public void setOutPlayer(Long outPlayer) {
		this.outPlayer = outPlayer;
	}

	public TipoTransfer getKind() {
		return kind;
	}

	public void setKind(TipoTransfer kind) {
		this.kind = kind;
	}

    
    
}
