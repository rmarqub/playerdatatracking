package com.playerdatatracking.responses;

import java.util.List;

public class AnalysisHistoryData {

    // ── Counts ────────────────────────────────────────────────────────────────
    private int totalAnalysed;        // all analyses in DB (including upcoming)
    private int processedAnalyses;    // FT matches with base snapshot → evaluable
    private int correctPredictions;   // adjPredicted == actualResult
    private int incorrectPredictions;
    private double accuracyRate;      // % over processedAnalyses

    // ── Calibration metrics (over processedAnalyses, adjusted probs) ──────────
    private double avgBrierScore;     // 0 = perfect, 0.333 = random
    private double avgLogLoss;        // lower is better

    // ── Calibration run ───────────────────────────────────────────────────────
    private boolean calibrationRun;   // whether script was executed
    private boolean weightsUpdated;   // whether script inserted a new weights row
    private String  calibrationMessage;

    // ── Current weights in use ────────────────────────────────────────────────
    private ContextualWeightsSnapshot currentWeights;

    // ── Per-match breakdown ───────────────────────────────────────────────────
    private List<AnalysisMatchResult> matchResults;

    public int    getTotalAnalysed()                    { return totalAnalysed; }
    public void   setTotalAnalysed(int v)               { totalAnalysed = v; }
    public int    getProcessedAnalyses()                { return processedAnalyses; }
    public void   setProcessedAnalyses(int v)           { processedAnalyses = v; }
    public int    getCorrectPredictions()               { return correctPredictions; }
    public void   setCorrectPredictions(int v)          { correctPredictions = v; }
    public int    getIncorrectPredictions()             { return incorrectPredictions; }
    public void   setIncorrectPredictions(int v)        { incorrectPredictions = v; }
    public double getAccuracyRate()                     { return accuracyRate; }
    public void   setAccuracyRate(double v)             { accuracyRate = v; }
    public double getAvgBrierScore()                    { return avgBrierScore; }
    public void   setAvgBrierScore(double v)            { avgBrierScore = v; }
    public double getAvgLogLoss()                       { return avgLogLoss; }
    public void   setAvgLogLoss(double v)               { avgLogLoss = v; }
    public boolean isCalibrationRun()                   { return calibrationRun; }
    public void   setCalibrationRun(boolean v)          { calibrationRun = v; }
    public boolean isWeightsUpdated()                   { return weightsUpdated; }
    public void   setWeightsUpdated(boolean v)          { weightsUpdated = v; }
    public String getCalibrationMessage()               { return calibrationMessage; }
    public void   setCalibrationMessage(String v)       { calibrationMessage = v; }
    public ContextualWeightsSnapshot getCurrentWeights(){ return currentWeights; }
    public void   setCurrentWeights(ContextualWeightsSnapshot v) { currentWeights = v; }
    public List<AnalysisMatchResult> getMatchResults()  { return matchResults; }
    public void   setMatchResults(List<AnalysisMatchResult> v) { matchResults = v; }
}
