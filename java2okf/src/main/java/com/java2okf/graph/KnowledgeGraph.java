package com.java2okf.graph;

import com.java2okf.model.EntityIds;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.JavaType;
import com.java2okf.model.RelationshipType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.stream.Stream;

/**
 * Queryable relationship graph over a {@link JavaProject}.
 *
 * <p>Holds the forward and reverse indexes that answer questions such as
 * "what does this method call?", "who calls it?", "which types depend on this
 * type?". All query results are sorted, so consumers never observe hash order.
 * Instances are created by {@link KnowledgeGraphBuilder} and are immutable.</p>
 */
public final class KnowledgeGraph {

    private final JavaProject project;
    private final List<JavaRelationship> relationships;
    private final Map<String, List<JavaRelationship>> outgoing;
    private final Map<String, List<JavaRelationship>> incoming;
    private final Map<String, SortedMap<String, TypeDependency>> dependencies;
    private final Map<String, SortedSet<String>> dependents;
    private final Map<String, SortedSet<String>> packageDependencies;
    private final Map<String, SortedSet<String>> packageDependents;

    KnowledgeGraph(JavaProject project, List<JavaRelationship> relationships,
                   Map<String, List<JavaRelationship>> outgoing, Map<String, List<JavaRelationship>> incoming,
                   Map<String, SortedMap<String, TypeDependency>> dependencies, Map<String, SortedSet<String>> dependents,
                   Map<String, SortedSet<String>> packageDependencies, Map<String, SortedSet<String>> packageDependents) {
        this.project = project;
        this.relationships = List.copyOf(relationships);
        this.outgoing = outgoing;
        this.incoming = incoming;
        this.dependencies = dependencies;
        this.dependents = dependents;
        this.packageDependencies = packageDependencies;
        this.packageDependents = packageDependents;
    }

    public JavaProject project() {
        return project;
    }

    /** All relationships: the extracted ones plus derived ones, sorted. */
    public List<JavaRelationship> relationships() {
        return relationships;
    }

    /** Relationships starting at {@code sourceId} (resolved and unresolved). */
    public List<JavaRelationship> outgoing(String sourceId) {
        return outgoing.getOrDefault(sourceId, List.of());
    }

    public List<JavaRelationship> outgoing(String sourceId, RelationshipType type) {
        return outgoing(sourceId).stream().filter(r -> r.type() == type).toList();
    }

    /** Resolved relationships ending at {@code targetId}. */
    public List<JavaRelationship> incoming(String targetId) {
        return incoming.getOrDefault(targetId, List.of());
    }

    public List<JavaRelationship> incoming(String targetId, RelationshipType type) {
        return incoming(targetId).stream().filter(r -> r.type() == type).toList();
    }

    /** True if {@code id} denotes an entity declared in the analysed sources. */
    public boolean isInternal(String id) {
        return id != null && project.containsEntity(id);
    }

    /**
     * True for resolved members of analysed types that have no declaration in
     * source, e.g. {@code values()} of an enum or a record accessor.
     */
    public boolean isImplicitMember(String id) {
        if (id == null || isInternal(id)) {
            return false;
        }
        String owner = EntityIds.declaringTypeName(id);
        return owner != null && project.typeByQualifiedName(owner).isPresent();
    }

    /**
     * Returns the type that owns an entity: the type itself for type IDs, the
     * declaring type for members.
     */
    public Optional<JavaType> owningType(String entityId) {
        if (EntityIds.isTypeId(entityId)) {
            return project.type(entityId);
        }
        String owner = EntityIds.declaringTypeName(entityId);
        return owner == null ? Optional.empty() : project.typeByQualifiedName(owner);
    }

    /** Types that {@code typeId} depends on, keyed by target type ID; excludes the type itself. */
    public Collection<TypeDependency> dependenciesOf(String typeId) {
        SortedMap<String, TypeDependency> map = dependencies.get(typeId);
        return map == null ? List.of() : map.values();
    }

    /** Internal types that depend on {@code typeId}, sorted by ID. */
    public SortedSet<String> dependentsOf(String typeId) {
        return dependents.getOrDefault(typeId, Collections.emptySortedSet());
    }

    /** Internal packages (by name) that {@code packageName} depends on. */
    public SortedSet<String> packageDependenciesOf(String packageName) {
        return packageDependencies.getOrDefault(packageName, Collections.emptySortedSet());
    }

    /** Internal packages (by name) that depend on {@code packageName}. */
    public SortedSet<String> packageDependentsOf(String packageName) {
        return packageDependents.getOrDefault(packageName, Collections.emptySortedSet());
    }

    /**
     * Resolved calls into any method or constructor of {@code typeId} made from
     * code outside the type, sorted by caller.
     */
    public List<JavaRelationship> callsIntoType(String typeId) {
        Optional<JavaType> type = project.type(typeId);
        if (type.isEmpty()) {
            return List.of();
        }
        String typeName = type.get().qualifiedName();
        return Stream.concat(
                        type.get().methods().stream().map(m -> m.id()),
                        type.get().constructors().stream().map(c -> c.id()))
                .flatMap(id -> incoming(id, RelationshipType.CALLS).stream())
                .filter(r -> owningType(r.sourceId()).map(t -> !t.qualifiedName().equals(typeName)).orElse(true))
                .sorted()
                .toList();
    }
}
