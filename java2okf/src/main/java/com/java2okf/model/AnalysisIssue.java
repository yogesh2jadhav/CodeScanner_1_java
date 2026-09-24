package com.java2okf.model;

import java.util.Comparator;

/**
 * A problem encountered during analysis. Issues are collected rather than
 * thrown so that one bad file never aborts the analysis of a whole project.
 *
 * @param file     project-relative path of the affected file, or {@code null} for project-wide issues
 * @param stage    pipeline stage that detected the issue
 * @param severity severity
 * @param line     1-based line, or {@code null} when unknown
 * @param message  human-readable description
 */
public record AnalysisIssue(String file, AnalysisStage stage, IssueSeverity severity, Integer line, String message)
        implements Comparable<AnalysisIssue> {

    private static final Comparator<AnalysisIssue> ORDER = Comparator
            .comparing(AnalysisIssue::file, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(i -> i.line() == null ? 0 : i.line())
            .thenComparing(AnalysisIssue::stage)
            .thenComparing(AnalysisIssue::severity)
            .thenComparing(AnalysisIssue::message);

    @Override
    public int compareTo(AnalysisIssue other) {
        return ORDER.compare(this, other);
    }
}
