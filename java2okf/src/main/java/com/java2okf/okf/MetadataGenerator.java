package com.java2okf.okf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.model.AnalysisIssue;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;
import com.java2okf.model.TypeKind;
import com.java2okf.util.ToolVersion;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces the machine-readable files in {@code _metadata/}.
 */
public class MetadataGenerator {

    public static final String ANALYSIS_JSON = DocumentLayout.METADATA_DIR + "/analysis.json";
    public static final String ERRORS_JSON = DocumentLayout.METADATA_DIR + "/errors.json";

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Timing of the run; all fields are {@code null} when timestamps are disabled. */
    public record RunTiming(String startedAt, String completedAt, Long durationMillis) {
        public static RunTiming none() {
            return new RunTiming(null, null, null);
        }
    }

    public AnalysisMetadata metadata(KnowledgeGraph graph, RunTiming timing, String okfVersion, int documents,
                                     boolean symbolResolution) {
        JavaProject project = graph.project();
        List<JavaRelationship> relationships = graph.relationships();
        Map<String, Long> byType = new LinkedHashMap<>();
        for (RelationshipType type : RelationshipType.values()) {
            long count = relationships.stream().filter(r -> r.type() == type).count();
            if (count > 0) {
                byType.put(type.name(), count);
            }
        }
        return new AnalysisMetadata(
                ToolVersion.TOOL_NAME,
                ToolVersion.version(),
                okfVersion,
                project.name(),
                timing.startedAt(),
                timing.completedAt(),
                timing.durationMillis(),
                project.fileStatistics().total(),
                project.fileStatistics().parsed(),
                project.fileStatistics().failed(),
                project.packages().size(),
                project.countTypes(TypeKind.CLASS),
                project.countTypes(TypeKind.INTERFACE),
                project.countTypes(TypeKind.ENUM),
                project.countTypes(TypeKind.RECORD),
                project.countTypes(TypeKind.ANNOTATION),
                project.methods().size(),
                project.constructors().size(),
                project.fields().size(),
                relationships.size(),
                count(relationships, ResolutionStatus.RESOLVED),
                count(relationships, ResolutionStatus.UNRESOLVED),
                count(relationships, ResolutionStatus.AMBIGUOUS),
                count(relationships, ResolutionStatus.NOT_APPLICABLE),
                byType,
                documents,
                project.issues().size(),
                symbolResolution);
    }

    public BundleFile analysisFile(AnalysisMetadata metadata) {
        return new BundleFile(ANALYSIS_JSON, toJson(metadata));
    }

    public BundleFile errorsFile(List<AnalysisIssue> issues) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (AnalysisIssue issue : issues) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("file", issue.file());
            entry.put("stage", issue.stage().name());
            entry.put("severity", issue.severity().name());
            if (issue.line() != null) {
                entry.put("line", issue.line());
            }
            entry.put("error", issue.message());
            entries.add(entry);
        }
        return new BundleFile(ERRORS_JSON, toJson(entries));
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value) + "\n";
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Unable to serialise metadata", e);
        }
    }

    private static long count(List<JavaRelationship> relationships, ResolutionStatus status) {
        return relationships.stream().filter(r -> r.status() == status).count();
    }
}
