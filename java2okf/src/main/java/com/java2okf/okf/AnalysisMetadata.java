package com.java2okf.okf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

/**
 * Content of {@code _metadata/analysis.json}. This is tool metadata about the
 * run, not an OKF concept. Timestamps are omitted for reproducible bundles.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"tool", "version", "okfVersion", "project", "startedAt", "completedAt", "durationMillis",
        "sourceFiles", "parsedFiles", "failedFiles", "packages", "classes", "interfaces", "enums", "records",
        "annotations", "methods", "constructors", "fields", "relationships", "resolvedRelationships",
        "unresolvedRelationships", "ambiguousRelationships", "notApplicableRelationships", "relationshipsByType",
        "documents", "issues", "symbolResolution"})
public record AnalysisMetadata(
        String tool,
        String version,
        String okfVersion,
        String project,
        String startedAt,
        String completedAt,
        Long durationMillis,
        int sourceFiles,
        int parsedFiles,
        int failedFiles,
        int packages,
        long classes,
        long interfaces,
        long enums,
        long records,
        long annotations,
        int methods,
        int constructors,
        int fields,
        int relationships,
        long resolvedRelationships,
        long unresolvedRelationships,
        long ambiguousRelationships,
        long notApplicableRelationships,
        Map<String, Long> relationshipsByType,
        int documents,
        int issues,
        boolean symbolResolution) {
}
