package com.playerdatatracking.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeagueTierInfo {

    private Long torneoId;
    private String torneoName;
    private Integer paisId;
    private String paisName;
    private Integer tier;
    private Double tierFactor;
    private String notes;

    public LeagueTierInfo() {}

    public LeagueTierInfo(Long torneoId, String torneoName, Integer paisId, String paisName,
                          Integer tier, Double tierFactor, String notes) {
        this.torneoId = torneoId;
        this.torneoName = torneoName;
        this.paisId = paisId;
        this.paisName = paisName;
        this.tier = tier;
        this.tierFactor = tierFactor;
        this.notes = notes;
    }

    public Long getTorneoId() { return torneoId; }
    public void setTorneoId(Long torneoId) { this.torneoId = torneoId; }

    public String getTorneoName() { return torneoName; }
    public void setTorneoName(String torneoName) { this.torneoName = torneoName; }

    public Integer getPaisId() { return paisId; }
    public void setPaisId(Integer paisId) { this.paisId = paisId; }

    public String getPaisName() { return paisName; }
    public void setPaisName(String paisName) { this.paisName = paisName; }

    public Integer getTier() { return tier; }
    public void setTier(Integer tier) { this.tier = tier; }

    public Double getTierFactor() { return tierFactor; }
    public void setTierFactor(Double tierFactor) { this.tierFactor = tierFactor; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
