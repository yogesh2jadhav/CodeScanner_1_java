package com.java2okf.model;

/**
 * Severity of an {@link AnalysisIssue}.
 */
public enum IssueSeverity {
    /** The affected file or entity could not be analysed. */
    ERROR,
    /** Analysis continued but a fact could not be established. */
    WARNING
}
