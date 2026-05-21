package com.playerdatatracking.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueTierEntry {

    private Long torneoId;
    private Integer tier;
    private Double tierFactor;
    private String notes;

    public Long getTorneoId() { return torneoId; }
    public void setTorneoId(Long torneoId) { this.torneoId = torneoId; }

    public Integer getTier() { return tier; }
    public void setTier(Integer tier) { this.tier = tier; }

    public Double getTierFactor() { return tierFactor; }
    public void setTierFactor(Double tierFactor) { this.tierFactor = tierFactor; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
