package com.playerdatatracking.responses;

public class ContextualWeightsSnapshot {

    // ── Home-Away asymmetry weights ──────────────────────────────────────────
    private Double  wForma;
    private Double  wNeeds;
    private Double  wDef;
    private Double  wOff;
    private Double  wFatigue;
    private Double  wSetPieces;
    private Double  wAtm;
    private Double  wUnavail;

    // ── Draw-affinity weights ────────────────────────────────────────────────
    private Double  wFormaD;
    private Double  wNeedsD;
    private Double  wDefD;
    private Double  wOffD;
    private Double  wFatigueD;
    private Double  wSetPiecesD;
    private Double  wAtmD;
    private Double  wUnavailD;

    private String  calibrationDate;
    private Integer nSamples;
    private String  notes;
    private boolean fromDb;

    // ── HA getters/setters ───────────────────────────────────────────────────
    public Double  getWForma()             { return wForma; }
    public void    setWForma(Double v)     { wForma = v; }
    public Double  getWNeeds()             { return wNeeds; }
    public void    setWNeeds(Double v)     { wNeeds = v; }
    public Double  getWDef()               { return wDef; }
    public void    setWDef(Double v)       { wDef = v; }
    public Double  getWOff()               { return wOff; }
    public void    setWOff(Double v)       { wOff = v; }
    public Double  getWFatigue()           { return wFatigue; }
    public void    setWFatigue(Double v)   { wFatigue = v; }
    public Double  getWSetPieces()         { return wSetPieces; }
    public void    setWSetPieces(Double v) { wSetPieces = v; }
    public Double  getWAtm()               { return wAtm; }
    public void    setWAtm(Double v)       { wAtm = v; }
    public Double  getWUnavail()           { return wUnavail; }
    public void    setWUnavail(Double v)   { wUnavail = v; }

    // ── Draw getters/setters ─────────────────────────────────────────────────
    public Double  getWFormaD()              { return wFormaD; }
    public void    setWFormaD(Double v)      { wFormaD = v; }
    public Double  getWNeedsD()              { return wNeedsD; }
    public void    setWNeedsD(Double v)      { wNeedsD = v; }
    public Double  getWDefD()                { return wDefD; }
    public void    setWDefD(Double v)        { wDefD = v; }
    public Double  getWOffD()                { return wOffD; }
    public void    setWOffD(Double v)        { wOffD = v; }
    public Double  getWFatigueD()            { return wFatigueD; }
    public void    setWFatigueD(Double v)    { wFatigueD = v; }
    public Double  getWSetPiecesD()          { return wSetPiecesD; }
    public void    setWSetPiecesD(Double v)  { wSetPiecesD = v; }
    public Double  getWAtmD()                { return wAtmD; }
    public void    setWAtmD(Double v)        { wAtmD = v; }
    public Double  getWUnavailD()            { return wUnavailD; }
    public void    setWUnavailD(Double v)    { wUnavailD = v; }

    public String  getCalibrationDate()      { return calibrationDate; }
    public void    setCalibrationDate(String v) { calibrationDate = v; }
    public Integer getNSamples()             { return nSamples; }
    public void    setNSamples(Integer v)    { nSamples = v; }
    public String  getNotes()                { return notes; }
    public void    setNotes(String v)        { notes = v; }
    public boolean isFromDb()                { return fromDb; }
    public void    setFromDb(boolean v)      { fromDb = v; }
}
