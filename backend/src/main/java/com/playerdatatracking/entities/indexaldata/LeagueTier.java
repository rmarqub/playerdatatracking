package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "league_tier", schema = "public")
public class LeagueTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "torneo_id", nullable = false, unique = true)
    private Long torneoId;

    @Column(name = "tier", nullable = false)
    private Integer tier;

    @Column(name = "tier_factor", nullable = false)
    private Double tierFactor;

    @Column(name = "notes")
    private String notes;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    public LeagueTier() {}

    public LeagueTier(Long torneoId, Integer tier, Double tierFactor, String notes) {
        this.torneoId = torneoId;
        this.tier = tier;
        this.tierFactor = tierFactor;
        this.notes = notes;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTorneoId() { return torneoId; }
    public void setTorneoId(Long torneoId) { this.torneoId = torneoId; }

    public Integer getTier() { return tier; }
    public void setTier(Integer tier) { this.tier = tier; }

    public Double getTierFactor() { return tierFactor; }
    public void setTierFactor(Double tierFactor) { this.tierFactor = tierFactor; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
