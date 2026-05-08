package com.playerdatatracking.entities.indexaldata;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "contextual_weight_config")
public class ContextualWeightConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "w_forma")      private Double wForma;
    @Column(name = "w_needs")      private Double wNeeds;
    @Column(name = "w_def")        private Double wDef;
    @Column(name = "w_off")        private Double wOff;
    @Column(name = "w_fatigue")    private Double wFatigue;
    @Column(name = "w_set_pieces") private Double wSetPieces;
    @Column(name = "w_atm")        private Double wAtm;
    @Column(name = "w_unavail")    private Double wUnavail;

    @Column(name = "calibration_date") private LocalDate calibrationDate;
    @Column(name = "n_samples")        private Integer nSamples;
    @Column(name = "notes")            private String notes;

    public Long    getId()                      { return id; }
    public Double  getWForma()                  { return wForma; }
    public void    setWForma(Double v)          { wForma = v; }
    public Double  getWNeeds()                  { return wNeeds; }
    public void    setWNeeds(Double v)          { wNeeds = v; }
    public Double  getWDef()                    { return wDef; }
    public void    setWDef(Double v)            { wDef = v; }
    public Double  getWOff()                    { return wOff; }
    public void    setWOff(Double v)            { wOff = v; }
    public Double  getWFatigue()                { return wFatigue; }
    public void    setWFatigue(Double v)        { wFatigue = v; }
    public Double  getWSetPieces()              { return wSetPieces; }
    public void    setWSetPieces(Double v)      { wSetPieces = v; }
    public Double  getWAtm()                    { return wAtm; }
    public void    setWAtm(Double v)            { wAtm = v; }
    public Double  getWUnavail()                { return wUnavail; }
    public void    setWUnavail(Double v)        { wUnavail = v; }
    public LocalDate getCalibrationDate()       { return calibrationDate; }
    public void    setCalibrationDate(LocalDate v) { calibrationDate = v; }
    public Integer getNSamples()                { return nSamples; }
    public void    setNSamples(Integer v)       { nSamples = v; }
    public String  getNotes()                   { return notes; }
    public void    setNotes(String v)           { notes = v; }
}
