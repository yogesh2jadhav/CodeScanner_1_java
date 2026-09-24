package com.java2okf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A directed relationship between two entities.
 *
 * <p>For {@link ResolutionStatus#UNRESOLVED} and {@link ResolutionStatus#AMBIGUOUS}
 * relationships {@code targetId} is always {@code null}: an unproven target is never
 * fabricated. {@code targetName} then carries what the source code says, e.g.
 * {@code repository.save(order)}.</p>
 *
 * @param sourceId       entity ID of the source
 * @param type           relationship type
 * @param targetId       entity ID of the target, or {@code null} when not resolved
 * @param targetName     human-readable target: qualified name when resolved, source text otherwise
 * @param status         resolution outcome
 * @param confidence     reserved for future non-deterministic producers; always {@code null} for static facts
 * @param sourceLocation location of the first occurrence, or {@code null} for derived relationships
 * @param lines          every source line on which the relationship occurs, sorted and distinct
 * @param category       dependency category, or {@code null} if the relationship is not a dependency
 * @param details        additional facts (sorted by key)
 */
public record JavaRelationship(
        String sourceId,
        RelationshipType type,
        String targetId,
        String targetName,
        ResolutionStatus status,
        Double confidence,
        JavaSourceLocation sourceLocation,
        List<Integer> lines,
        DependencyCategory category,
        SortedMap<String, String> details) implements Comparable<JavaRelationship> {

    /** Detail key holding the entity ID of the type that declares a member target. */
    public static final String DETAIL_DECLARING_TYPE = "declaringType";

    /** Detail key marking relationships derived from other relationships rather than read from source. */
    public static final String DETAIL_DERIVED = "derived";

    private static final Comparator<JavaRelationship> ORDER = Comparator
            .comparing(JavaRelationship::sourceId)
            .thenComparing(JavaRelationship::type)
            .thenComparing(JavaRelationship::targetKey)
            .thenComparing(r -> r.category() == null ? "" : r.category().name());

    public JavaRelationship {
        if ((status == ResolutionStatus.UNRESOLVED || status == ResolutionStatus.AMBIGUOUS) && targetId != null) {
            throw new IllegalArgumentException("Unresolved relationships must not carry a target ID: " + targetId);
        }
        if (status == ResolutionStatus.RESOLVED && targetId == null) {
            throw new IllegalArgumentException("Resolved relationship without target ID from " + sourceId);
        }
        lines = List.copyOf(new TreeSet<>(lines));
        details = Collections.unmodifiableSortedMap(new TreeMap<>(details == null ? Map.of() : details));
    }

    /** Creates a relationship observed at a single source location. */
    public static JavaRelationship of(String sourceId, RelationshipType type, String targetId, String targetName,
                                      ResolutionStatus status, JavaSourceLocation location,
                                      DependencyCategory category, Map<String, String> details) {
        List<Integer> lines = location == null ? List.of() : List.of(location.lineStart());
        return new JavaRelationship(sourceId, type, targetId, targetName, status, null, location, lines,
                category, details == null ? new TreeMap<>() : new TreeMap<>(details));
    }

    /**
     * Identity used for de-duplication: repeated occurrences of the same fact
     * (e.g. two calls to the same method) collapse into one relationship.
     */
    public String dedupKey() {
        return sourceId + "|" + type + "|" + targetKey() + "|" + (category == null ? "" : category.name());
    }

    /** Resolved targets sort by ID; unresolved ones by their source text, after all resolved ones. */
    public String targetKey() {
        return targetId != null ? targetId : "~" + status + ":" + targetName;
    }

    public boolean isResolved() {
        return status == ResolutionStatus.RESOLVED;
    }

    /** Merges another occurrence of the same fact: lines are united, the earliest location is kept. */
    public JavaRelationship mergeWith(JavaRelationship other) {
        List<Integer> merged = new ArrayList<>(lines);
        merged.addAll(other.lines);
        JavaSourceLocation location = sourceLocation;
        if (location == null || (other.sourceLocation != null
                && other.sourceLocation.lineStart() < location.lineStart())) {
            location = other.sourceLocation;
        }
        TreeMap<String, String> mergedDetails = new TreeMap<>(other.details);
        mergedDetails.putAll(details);
        return new JavaRelationship(sourceId, type, targetId, targetName, status, confidence, location, merged,
                category, mergedDetails);
    }

    @Override
    public int compareTo(JavaRelationship other) {
        return ORDER.compare(this, other);
    }
}
