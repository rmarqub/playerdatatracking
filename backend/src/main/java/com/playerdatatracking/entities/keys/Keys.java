package com.playerdatatracking.entities.keys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "API_FOOTBALL_KEYS")
public class Keys {

	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable=false)
    private Long id;
	
	@Column(nullable=false)
	private String valor;
	
	@Column(name="last_used")
	private String lastUsed;
	
	@Column(name="today_uses", nullable=false)
	private int todayUses;
	
	@Column(name="total_uses", nullable=false)
	private int totalUses;
	
	@Column(name="is_valid", nullable=false)
	private boolean isValid;
	
	@Column(nullable=false, name="id_service")
	private int idService;
	
	@Column(nullable=false)
	private String mail;
	
	@Column(nullable=false, name= "ispermanentlyvalid")
	private boolean isPermanentlyValid;

	@Column(nullable=false, name= "hashkey")
	private String hashKey;
	
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getValor() {
		return valor;
	}

	public void setValor(String valor) {
		this.valor = valor;
	}

	public String getLastUsed() {
		return lastUsed;
	}

	public void setLastUsed(String lastUsed) {
		if (this.lastUsed==null || !this.lastUsed.equals(lastUsed))
			this.setTodayUses(0);
		this.lastUsed = lastUsed;
	}

	public int getTodayUses() {
		return todayUses;
	}

	public void setTodayUses(int todayUses) {
		this.todayUses = todayUses;
	}

	public int getTotalUses() {
		return totalUses;
	}

	public void setTotalUses(int totalUses) {
		this.totalUses = totalUses;
	}

	public boolean isValid() {
		return isValid;
	}

	public void setValid(boolean isValid) {
		this.isValid = isValid;
	}

	public int getIdService() {
		return idService;
	}

	public void setIdService(int idService) {
		this.idService = idService;
	}

	public String getMail() {
		return mail;
	}

	public void setMail(String mail) {
		this.mail = mail;
	}

	public boolean isPermanentlyValid() {
		return isPermanentlyValid;
	}

	public void setPermanentlyValid(boolean isPertmanentlyValid) {
		this.isPermanentlyValid = isPertmanentlyValid;
	}

	public String getHashKey() {
		return hashKey;
	}

	public void setHashKey(String hashKey) {
		this.hashKey = hashKey;
	}
	
	
	
}
