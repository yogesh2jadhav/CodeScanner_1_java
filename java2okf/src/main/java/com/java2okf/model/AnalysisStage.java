package com.java2okf.model;

/**
 * Pipeline stage in which an {@link AnalysisIssue} was detected.
 */
public enum AnalysisStage {
    SCANNING,
    PARSING,
    SYMBOL_RESOLUTION,
    ANALYSIS,
    GENERATION,
    VALIDATION
}
