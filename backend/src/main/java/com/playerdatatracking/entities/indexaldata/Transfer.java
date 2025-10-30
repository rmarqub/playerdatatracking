package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.*;

@Entity
@Table(name = "transfer")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player")
    private Long player;

    @Column(name="\"in\"")
    private Long in;

    @Column(name="\"out\"")
    private Long out;
    
    
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "kind", nullable = true, foreignKey = @ForeignKey(name = "fk_transfer_kind"))
    private TipoTransfer kind;

    @Column(name="season")
    private String season;
    
    
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

	public Long getIn() {
		return in;
	}

	public void setIn(Long in) {
		this.in = in;
	}

	public Long getOut() {
		return out;
	}

	public void setOut(Long out) {
		this.out = out;
	}

	public TipoTransfer getKind() {
		return kind;
	}

	public void setKind(TipoTransfer kind) {
		this.kind = kind;
	}

	public String getSeason() {
		return season;
	}

	public void setSeason(String season) {
		this.season = season;
	}

    
    
}
