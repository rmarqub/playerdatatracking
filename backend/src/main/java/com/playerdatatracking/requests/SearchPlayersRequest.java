package com.playerdatatracking.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchPlayersRequest {

	
	private String player;
	private String team;
	
	
	public SearchPlayersRequest(String player, String name) {
		super();
		this.player = player;
		this.team = name;
	}
	public String getPlayer() {
		return player;
	}
	public void setPlayer(String player) {
		this.player = player;
	}
	public String getTeam() {
		return team;
	}
	public void setTeam(String name) {
		this.team = name;
	}
	
	
}
