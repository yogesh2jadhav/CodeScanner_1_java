package com.java2okf.graph;

import com.java2okf.model.DependencyCategory;
import com.java2okf.model.EntityIds;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.JavaType;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the {@link KnowledgeGraph}: derives type-level relationships and
 * computes reverse and aggregated indexes.
 */
public class KnowledgeGraphBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeGraphBuilder.class);

    public KnowledgeGraph build(JavaProject project) {
        List<JavaRelationship> all = new ArrayList<>(project.relationships());
        all.addAll(deriveTypeOverrides(project));
        all.sort(null);

        Map<String, List<JavaRelationship>> outgoing = new HashMap<>();
        Map<String, List<JavaRelationship>> incoming = new HashMap<>();
        for (JavaRelationship relationship : all) {
            outgoing.computeIfAbsent(relationship.sourceId(), k -> new ArrayList<>()).add(relationship);
            if (relationship.targetId() != null) {
                incoming.computeIfAbsent(relationship.targetId(), k -> new ArrayList<>()).add(relationship);
            }
        }
        // 'all' is sorted, so every per-entity list is sorted as well.
        outgoing.replaceAll((k, v) -> List.copyOf(v));
        incoming.replaceAll((k, v) -> List.copyOf(v));

        KnowledgeGraph partial = new KnowledgeGraph(project, all, outgoing, incoming, Map.of(), Map.of(), Map.of(), Map.of());
        Map<String, SortedMap<String, TypeDependency>> dependencies = new HashMap<>();
        for (JavaType type : project.types().values()) {
            dependencies.put(type.id(), aggregateDependencies(partial, type));
        }

        Map<String, SortedSet<String>> dependents = new HashMap<>();
        Map<String, SortedSet<String>> packageDependencies = new HashMap<>();
        Map<String, SortedSet<String>> packageDependents = new HashMap<>();
        dependencies.forEach((sourceTypeId, targets) -> {
            JavaType source = project.types().get(sourceTypeId);
            for (TypeDependency dependency : targets.values()) {
                if (!dependency.internal()) {
                    continue;
                }
                dependents.computeIfAbsent(dependency.targetTypeId(), k -> new TreeSet<>()).add(sourceTypeId);
                JavaType target = project.types().get(dependency.targetTypeId());
                if (!target.packageName().equals(source.packageName())) {
                    packageDependencies.computeIfAbsent(source.packageName(), k -> new TreeSet<>()).add(target.packageName());
                    packageDependents.computeIfAbsent(target.packageName(), k -> new TreeSet<>()).add(source.packageName());
                }
            }
        });

        LOG.debug("Knowledge graph: {} relationships ({} derived)", all.size(), all.size() - project.relationships().size());
        return new KnowledgeGraph(project, all, outgoing, incoming, unmodifiable(dependencies), unmodifiableSets(dependents),
                unmodifiableSets(packageDependencies), unmodifiableSets(packageDependents));
    }

    /**
     * Aggregates the dependency relationships of a type and all its members
     * (fields, methods, constructors) into one entry per target type. Nested
     * types are separate entities and aggregate their own dependencies.
     */
    private SortedMap<String, TypeDependency> aggregateDependencies(KnowledgeGraph graph, JavaType type) {
        List<String> sources = new ArrayList<>();
        sources.add(type.id());
        type.fields().forEach(f -> sources.add(f.id()));
        type.methods().forEach(m -> sources.add(m.id()));
        type.constructors().forEach(c -> sources.add(c.id()));

        Map<String, SortedSet<DependencyCategory>> categories = new TreeMap<>();
        Map<String, String> names = new HashMap<>();
        for (String source : sources) {
            for (JavaRelationship relationship : graph.outgoing(source)) {
                if (relationship.category() == null || relationship.status() != ResolutionStatus.RESOLVED) {
                    continue;
                }
                Optional<String> targetType = targetTypeId(graph, relationship);
                if (targetType.isEmpty() || targetType.get().equals(type.id())) {
                    continue;
                }
                categories.computeIfAbsent(targetType.get(), k -> new TreeSet<>()).add(relationship.category());
                names.putIfAbsent(targetType.get(), EntityIds.localPart(targetType.get()));
            }
        }
        SortedMap<String, TypeDependency> result = new TreeMap<>();
        categories.forEach((targetId, cats) ->
                result.put(targetId, new TypeDependency(targetId, names.get(targetId), cats, graph.isInternal(targetId))));
        return Collections.unmodifiableSortedMap(result);
    }

    /** Maps a relationship target (type or member) to the ID of the type it belongs to. */
    private static Optional<String> targetTypeId(KnowledgeGraph graph, JavaRelationship relationship) {
        String target = relationship.targetId();
        if (EntityIds.isTypeId(target)) {
            return Optional.of(target);
        }
        Optional<JavaType> owner = graph.owningType(target);
        if (owner.isPresent()) {
            return Optional.of(owner.get().id());
        }
        // Library members carry their declaring type (with the correct kind) in the details.
        return Optional.ofNullable(relationship.details().get(JavaRelationship.DETAIL_DECLARING_TYPE));
    }

    /**
     * Derives type-level {@code OVERRIDES}: type T overrides a method of ancestor A
     * whenever a method of T has a resolved {@code OVERRIDES_METHOD} into A.
     */
    private List<JavaRelationship> deriveTypeOverrides(JavaProject project) {
        Map<String, JavaRelationship> derived = new TreeMap<>();
        for (JavaRelationship relationship : project.relationships()) {
            if (relationship.type() != RelationshipType.OVERRIDES_METHOD || !relationship.isResolved()) {
                continue;
            }
            String sourceTypeName = EntityIds.declaringTypeName(relationship.sourceId());
            Optional<JavaType> sourceType = sourceTypeName == null ? Optional.empty() : project.typeByQualifiedName(sourceTypeName);
            String targetTypeId = relationship.details().get(JavaRelationship.DETAIL_DECLARING_TYPE);
            if (sourceType.isEmpty() || targetTypeId == null) {
                continue;
            }
            JavaRelationship summary = new JavaRelationship(sourceType.get().id(), RelationshipType.OVERRIDES,
                    targetTypeId, EntityIds.localPart(targetTypeId), ResolutionStatus.RESOLVED, null, null, List.of(),
                    null, new TreeMap<>(Map.of(JavaRelationship.DETAIL_DERIVED, "true")));
            derived.putIfAbsent(summary.dedupKey(), summary);
        }
        return new ArrayList<>(derived.values());
    }

    private static <V> Map<String, V> unmodifiable(Map<String, V> map) {
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, SortedSet<String>> unmodifiableSets(Map<String, SortedSet<String>> map) {
        map.replaceAll((k, v) -> Collections.unmodifiableSortedSet(v));
        return Collections.unmodifiableMap(map);
    }
}
