package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "torneo", schema = "public")
public class Torneo implements Serializable {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "tipo")
    private Integer tipoTorneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pais")
    private Pais pais;

    @Column(name = "studied")
    private Boolean studied;

    @Column(name = "fbrefdata")
    private Boolean fbrefdata;

    @Column(name = "fbrefid", updatable = false)
    private Integer fbrefid;

    @Column(name = "lastupdate")
    private Date lastupdate;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getTipoTorneo() {
        return tipoTorneo;
    }

    public void setTipoTorneo(Integer tipoTorneo) {
        this.tipoTorneo = tipoTorneo;
    }

    public Pais getPais() {
        return pais;
    }

    public void setPais(Pais pais) {
        this.pais = pais;
    }

    public Boolean getStudied() {
        return studied;
    }

    public void setStudied(Boolean studied) {
        this.studied = studied;
    }

    public Boolean getFbrefdata() {
        return fbrefdata;
    }

    public void setFbrefdata(Boolean fbrefdata) {
        this.fbrefdata = fbrefdata;
    }

    public Integer getFbrefid() {
        return fbrefid;
    }

    public void setFbrefid(Integer fbrefid) {
        this.fbrefid = fbrefid;
    }

    public Date getLastupdate() {
        return lastupdate;
    }

    public void setLastupdate(Date lastupdate) {
        this.lastupdate = lastupdate;
    }
}
