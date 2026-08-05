package com.playerdatatracking.entities.indexaldata;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Club")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Club {

	@Id
    @Column(name = "id")
	private Long id;
	
	@Column(name = "pais")
	private int idPais;
	
	@Column(name = "codeaf")
	private String codeaf;
	
	@Column(name = "nombre")
	private String nombre;

	@Column(name = "es_seleccion")
	private Boolean esSeleccion;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public int getIdPais() {
		return idPais;
	}

	public void setIdPais(int idPais) {
		this.idPais = idPais;
	}

	public String getCodeaf() {
		return codeaf;
	}

	public void setCodeaf(String codeaf) {
		this.codeaf = codeaf;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public Boolean getEsSeleccion() {
		return esSeleccion;
	}

	public void setEsSeleccion(Boolean esSeleccion) {
		this.esSeleccion = esSeleccion;
	}
}
