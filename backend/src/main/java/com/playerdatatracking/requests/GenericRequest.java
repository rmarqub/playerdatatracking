package com.playerdatatracking.requests;

import java.util.Date;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericRequest {
	private Long id;
	private Long indexId;
    private String nombre;
    private float nota;
    private String club;
    private String posicion;
    private List<String> qualities;
    private String mostLikeDestination;
    private String likeable;
    private String age;
    private String date;
    private String birth;
    private String newKey;
    private String mail;
    private String plan;
    private String apiKey;
    private String update;
    private String restUpdate;
    private String updateWithTransfers;
    private int threads;
    private int idService;
    private MultipartFile photo;
    private String purgeBeforeRun;
    private List<Long> leagueIds;
    private String season;
    private ContextualAnalysisRequest contextualAnalysis;
    private String scope;
    private List<LeagueTierEntry> leagueTiers;

    public ContextualAnalysisRequest getContextualAnalysis() { return contextualAnalysis; }
    public void setContextualAnalysis(ContextualAnalysisRequest v) { contextualAnalysis = v; }
    public String getScope() { return scope; }
    public void setScope(String v) { scope = v; }
    public List<LeagueTierEntry> getLeagueTiers() { return leagueTiers; }
    public void setLeagueTiers(List<LeagueTierEntry> leagueTiers) { this.leagueTiers = leagueTiers; }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public List<Long> getLeagueIds() {
        return leagueIds;
    }

    public void setLeagueIds(List<Long> leagueIds) {
        this.leagueIds = leagueIds;
    }

    public String getRestUpdate() {
		return restUpdate;
	}

	public void setRestUpdate(String restUpdate) {
		this.restUpdate = restUpdate;
	}

	
    
    
    public String getUpdateWithTransfers() {
		return updateWithTransfers;
	}

	public void setUpdateWithTransfers(String updateWithTransfers) {
		this.updateWithTransfers = updateWithTransfers;
	}

	public int getThreads() {
		return threads;
	}

	public void setThreads(int threads) {
		this.threads = threads;
	}

	public String getPurgeBeforeRun() {
		return purgeBeforeRun;
	}

	public void setPurgeBeforeRun(String purgeBeforeRun) {
		this.purgeBeforeRun = purgeBeforeRun;
	}

	// Getters y setters
    public Long getId() {
        return id;
    }

    
    public MultipartFile getPhoto() {
		return photo;
	}

	public void setPhoto(MultipartFile photo) {
		this.photo = photo;
	}

	public Long getIndexId() {
		return indexId;
	}
	public void setIndexId(Long indexId) {
		this.indexId = indexId;
	}

	public String getAge() {
		return age;
	}

	public void setAge(String age) {
		this.age = age;
	}

	public String getBirth() {
		return birth;
	}

	public void setBirth(String birth) {
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

	public String getNewKey() {
		return newKey;
	}

	public void setNewKey(String newKey) {
		this.newKey = newKey;
	}

	public String getMail() {
		return mail;
	}

	public void setMail(String mail) {
		this.mail = mail;
	}

	public String getPlan() {
		return plan;
	}

	public void setPlan(String plan) {
		this.plan = plan;
	}

	public int getIdService() {
		return idService;
	}

	public void setIdService(int idService) {
		this.idService = idService;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getUpdate() {
		return update;
	}

	public void setUpdate(String update) {
		this.update = update;
	}
    
    
}
