package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "contextual_weight_config")
public class ContextualWeightConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // ── Home-Away asymmetry weights ──────────────────────────────────────────
    @Column(name = "w_forma")      private Double wForma;
    @Column(name = "w_needs")      private Double wNeeds;
    @Column(name = "w_def")        private Double wDef;
    @Column(name = "w_off")        private Double wOff;
    @Column(name = "w_fatigue")    private Double wFatigue;
    @Column(name = "w_set_pieces") private Double wSetPieces;
    @Column(name = "w_atm")        private Double wAtm;
    @Column(name = "w_unavail")    private Double wUnavail;

    // ── Draw-affinity weights (new) ──────────────────────────────────────────
    @Column(name = "w_forma_d")      private Double wFormaD;
    @Column(name = "w_needs_d")      private Double wNeedsD;
    @Column(name = "w_def_d")        private Double wDefD;
    @Column(name = "w_off_d")        private Double wOffD;
    @Column(name = "w_fatigue_d")    private Double wFatigueD;
    @Column(name = "w_set_pieces_d") private Double wSetPiecesD;
    @Column(name = "w_atm_d")        private Double wAtmD;
    @Column(name = "w_unavail_d")    private Double wUnavailD;

    @Column(name = "calibration_date") private LocalDate calibrationDate;
    @Column(name = "n_samples")        private Integer nSamples;
    @Column(name = "notes")            private String notes;

    // ── HA getters/setters ───────────────────────────────────────────────────
    public Long    getId()                         { return id; }
    public void    setId(Long v)                   { id = v; }
    public Long    getUserId()                     { return userId; }
    public void    setUserId(Long v)               { userId = v; }
    public Double  getWForma()                     { return wForma; }
    public void    setWForma(Double v)             { wForma = v; }
    public Double  getWNeeds()                     { return wNeeds; }
    public void    setWNeeds(Double v)             { wNeeds = v; }
    public Double  getWDef()                       { return wDef; }
    public void    setWDef(Double v)               { wDef = v; }
    public Double  getWOff()                       { return wOff; }
    public void    setWOff(Double v)               { wOff = v; }
    public Double  getWFatigue()                   { return wFatigue; }
    public void    setWFatigue(Double v)           { wFatigue = v; }
    public Double  getWSetPieces()                 { return wSetPieces; }
    public void    setWSetPieces(Double v)         { wSetPieces = v; }
    public Double  getWAtm()                       { return wAtm; }
    public void    setWAtm(Double v)               { wAtm = v; }
    public Double  getWUnavail()                   { return wUnavail; }
    public void    setWUnavail(Double v)           { wUnavail = v; }

    // ── Draw getters/setters ─────────────────────────────────────────────────
    public Double  getWFormaD()                    { return wFormaD; }
    public void    setWFormaD(Double v)            { wFormaD = v; }
    public Double  getWNeedsD()                    { return wNeedsD; }
    public void    setWNeedsD(Double v)            { wNeedsD = v; }
    public Double  getWDefD()                      { return wDefD; }
    public void    setWDefD(Double v)              { wDefD = v; }
    public Double  getWOffD()                      { return wOffD; }
    public void    setWOffD(Double v)              { wOffD = v; }
    public Double  getWFatigueD()                  { return wFatigueD; }
    public void    setWFatigueD(Double v)          { wFatigueD = v; }
    public Double  getWSetPiecesD()                { return wSetPiecesD; }
    public void    setWSetPiecesD(Double v)        { wSetPiecesD = v; }
    public Double  getWAtmD()                      { return wAtmD; }
    public void    setWAtmD(Double v)              { wAtmD = v; }
    public Double  getWUnavailD()                  { return wUnavailD; }
    public void    setWUnavailD(Double v)          { wUnavailD = v; }

    public LocalDate getCalibrationDate()          { return calibrationDate; }
    public void    setCalibrationDate(LocalDate v) { calibrationDate = v; }
    public Integer getNSamples()                   { return nSamples; }
    public void    setNSamples(Integer v)          { nSamples = v; }
    public String  getNotes()                      { return notes; }
    public void    setNotes(String v)              { notes = v; }
}
