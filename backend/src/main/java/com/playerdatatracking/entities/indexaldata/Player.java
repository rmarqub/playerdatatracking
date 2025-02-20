package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.*;
import java.sql.Date;
import java.sql.Timestamp;

@Entity
@Table(name = "player")
public class Player {
	
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name="firstname")
    private String firstname;
    
    @Column(name="lastname")
    private String lastname;
    
    @Column(name="fullname")
    private String fullname;
    
    @Column(name="nacionalidad")
    private Integer nacionalidad;
    
    @Column(name="birth")
    private Date birth;
    
    @Column(name="age")
    private Integer age;
    
    @Column(name="height")
    private Integer height;
    
    @Column(name="weight")
    private Integer weight;
    
    @Column(name="injured")
    private Boolean injured;
    
    @Column(name="team")
    private Long team;
    
    @Column(name = "last_updated", nullable = false)
    private Timestamp lastUpdated;
    
    @Column(name="index_id")
    private Long indexId;
    
    @Column(name="fbref_id")
    private Long fbrefId;

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

	public Integer getNacionalidad() {
		return nacionalidad;
	}

	public void setNacionalidad(Integer nacionalidad) {
		this.nacionalidad = nacionalidad;
	}

	public Date getBirth() {
		return birth;
	}

	public void setBirth(Date birth) {
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

	public Long getTeam() {
		return team;
	}

	public void setTeam(Long team) {
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
