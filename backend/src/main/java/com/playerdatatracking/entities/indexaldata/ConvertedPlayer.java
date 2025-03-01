package com.playerdatatracking.entities.indexaldata;

import java.sql.Timestamp;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConvertedPlayer {

    private Long id;
    private String firstname;
    private String lastname;
    private String fullname;
    private String nacionalidad;
    private LocalDate birth;
    private Integer age;
    private Integer height;
    private Integer weight;
    private Boolean injured;
    private String team;
    private Timestamp lastUpdated;
    private Long indexId;
    private Long fbrefId;
    
    public ConvertedPlayer(Player p, PlayerDataClient pdClient) throws PlayerDataDBException{
    	this.id = p.getId();
    	this.firstname = p.getFirstname();
    	this.lastname = p.getLastname();
    	this.fullname = p.getFullname();
    	if (p.getNacionalidad()!=null)
    		this.nacionalidad = pdClient.findCountry(Integer.toUnsignedLong(p.getNacionalidad())).getName();
    	this.birth = p.getBirth();
    	this.age = p.getAge();
    	this.height = p.getHeight();
    	this.weight = p.getWeight();
    	this.injured = p.getInjured();
    	if (p.getTeam() !=null)
    		this.team = pdClient.findClub(p.getTeam()).getNombre();
    	this.lastUpdated = p.getLastUpdated();
    	this.indexId = p.getIndexId();
    	this.fbrefId = p.getFbrefId();
    }
    
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getFirstname() {
		return firstname;
	}
	public void setFirstname(String firstname) {
		this.firstname = firstname;
	}
	public String getLastname() {
		return lastname;
	}
	public void setLastname(String lastname) {
		this.lastname = lastname;
	}
	public String getFullname() {
		return fullname;
	}
	public void setFullname(String fullname) {
		this.fullname = fullname;
	}
	public String getNacionalidad() {
		return nacionalidad;
	}
	public void setNacionalidad(String nacionalidad) {
		this.nacionalidad = nacionalidad;
	}
	public LocalDate getBirth() {
		return birth;
	}
	public void setBirth(LocalDate birth) {
		this.birth = birth;
	}
	public Integer getAge() {
		return age;
	}
	public void setAge(Integer age) {
		this.age = age;
	}
	public Integer getHeight() {
		return height;
	}
	public void setHeight(Integer height) {
		this.height = height;
	}
	public Integer getWeight() {
		return weight;
	}
	public void setWeight(Integer weight) {
		this.weight = weight;
	}
	public Boolean getInjured() {
		return injured;
	}
	public void setInjured(Boolean injured) {
		this.injured = injured;
	}
	public String getTeam() {
		return team;
	}
	public void setTeam(String team) {
		this.team = team;
	}
	public Timestamp getLastUpdated() {
		return lastUpdated;
	}
	public void setLastUpdated(Timestamp lastUpdated) {
		this.lastUpdated = lastUpdated;
	}
	public Long getIndexId() {
		return indexId;
	}
	public void setIndexId(Long indexId) {
		this.indexId = indexId;
	}
	public Long getFbrefId() {
		return fbrefId;
	}
	public void setFbrefId(Long fbrefId) {
		this.fbrefId = fbrefId;
	}
    
    
    
    
}
