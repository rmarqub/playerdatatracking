package com.playerdatatracking.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TorneoInfo {

    private Long id;
    private String name;
    private Boolean studied;
    private Integer paisId;
    private String paisName;

    public TorneoInfo() {}

    public TorneoInfo(Long id, String name, Boolean studied, Integer paisId, String paisName) {
        this.id = id;
        this.name = name;
        this.studied = studied;
        this.paisId = paisId;
        this.paisName = paisName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getStudied() { return studied; }
    public void setStudied(Boolean studied) { this.studied = studied; }

    public Integer getPaisId() { return paisId; }
    public void setPaisId(Integer paisId) { this.paisId = paisId; }

    public String getPaisName() { return paisName; }
    public void setPaisName(String paisName) { this.paisName = paisName; }
}
