package com.playerdatatracking.entities.indexaldata;




import jakarta.persistence.*;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "MANUAL_TRACKED_PLAYER", uniqueConstraints = @UniqueConstraint(columnNames = "nombre"))
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManualTrackedPlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(nullable = false)
    private float nota;

    @Column(nullable = false)
    private String club;

    private String posicion;

    @ElementCollection
    @CollectionTable(name = "PLAYER_QUALITIES", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "quality")
    private List<String> qualities;

    private String mostLikeDestination;
    private String likeable;
    private Integer age;

    @Temporal(TemporalType.DATE)
    private Date date;
    
    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date birth;
    
    private Long fbrefID;
    private Long indexID;

    // Getters y setters
    public Long getId() {
        return id;
    }

    public Integer getAge() {
		return age;
	}

	public void setAge(Integer age) {
		this.age = age;
	}

	public Date getBirth() {
		return birth;
	}

	public void setBirth(Date birth) {
		this.birth = birth;
	}

	public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public float getNota() {
        return nota;
    }

    public void setNota(float nota) {
        this.nota = nota;
    }

    public String getClub() {
        return club;
    }

    public void setClub(String club) {
        this.club = club;
    }

    public String getPosicion() {
        return posicion;
    }

    public void setPosicion(String posicion) {
        this.posicion = posicion;
    }

    public List<String> getQualities() {
        return qualities;
    }

    public void setQualities(List<String> qualities) {
        this.qualities = qualities;
    }

    public String getMostLikeDestination() {
        return mostLikeDestination;
    }

    public void setMostLikeDestination(String mostLikeDestination) {
        this.mostLikeDestination = mostLikeDestination;
    }

    public String getLikeable() {
        return likeable;
    }

    public void setLikeable(String likeable) {
        this.likeable = likeable;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

	public void setFbrefID(long fbrefID) {
		this.fbrefID = fbrefID;
	}

	public void setIndexID(long indexID) {
		this.indexID = indexID;
	}
	
	public Long getFbrefID() {
	    return fbrefID != null ? fbrefID : 0L;
	}

	public Long getIndexID() {
	    return indexID != null ? indexID : 0L;
	}
    
    
}
