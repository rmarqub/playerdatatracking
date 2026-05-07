package com.playerdatatracking.responses;

public class ContextualWeightsSnapshot {

    private Double  wForma;
    private Double  wNeeds;
    private Double  wDef;
    private Double  wOff;
    private Double  wFatigue;
    private Double  wSetPieces;
    private Double  wAtm;
    private String  calibrationDate;
    private Integer nSamples;
    private String  notes;
    private boolean fromDb;

    public Double  getWForma()            { return wForma; }
    public void    setWForma(Double v)    { wForma = v; }
    public Double  getWNeeds()            { return wNeeds; }
    public void    setWNeeds(Double v)    { wNeeds = v; }
    public Double  getWDef()              { return wDef; }
    public void    setWDef(Double v)      { wDef = v; }
    public Double  getWOff()              { return wOff; }
    public void    setWOff(Double v)      { wOff = v; }
    public Double  getWFatigue()          { return wFatigue; }
    public void    setWFatigue(Double v)  { wFatigue = v; }
    public Double  getWSetPieces()        { return wSetPieces; }
    public void    setWSetPieces(Double v){ wSetPieces = v; }
    public Double  getWAtm()              { return wAtm; }
    public void    setWAtm(Double v)      { wAtm = v; }
    public String  getCalibrationDate()   { return calibrationDate; }
    public void    setCalibrationDate(String v) { calibrationDate = v; }
    public Integer getNSamples()          { return nSamples; }
    public void    setNSamples(Integer v) { nSamples = v; }
    public String  getNotes()             { return notes; }
    public void    setNotes(String v)     { notes = v; }
    public boolean isFromDb()             { return fromDb; }
    public void    setFromDb(boolean v)   { fromDb = v; }
}
