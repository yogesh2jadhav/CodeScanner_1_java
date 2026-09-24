package com.java2okf.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The complete, immutable Java knowledge model of an analysed project.
 *
 * <p>This is the central architectural boundary: analysers write it, output
 * generators read it. It holds no JavaParser AST nodes and no Markdown, so new
 * output formats (JSON, graph databases, ...) can be added without touching
 * the analysis.</p>
 */
public final class JavaProject {

    private final String name;
    private final SortedMap<String, JavaPackage> packages;
    private final SortedMap<String, JavaType> types;
    private final SortedMap<String, JavaMethod> methods;
    private final SortedMap<String, JavaConstructor> constructors;
    private final SortedMap<String, JavaField> fields;
    private final Map<String, JavaType> typesByQualifiedName;
    private final Set<String> packageIds;
    private final List<JavaRelationship> relationships;
    private final List<AnalysisIssue> issues;
    private final FileStatistics fileStatistics;

    /**
     * Creates the model. All collections must already be sorted and de-duplicated;
     * use {@code KnowledgeModelBuilder} rather than calling this directly.
     */
    public JavaProject(String name, Collection<JavaPackage> packages, Collection<JavaType> types,
                       List<JavaRelationship> relationships, List<AnalysisIssue> issues,
                       FileStatistics fileStatistics) {
        this.name = name;
        TreeMap<String, JavaPackage> packageMap = new TreeMap<>();
        packages.forEach(p -> packageMap.put(p.name(), p));
        TreeMap<String, JavaType> typeMap = new TreeMap<>();
        TreeMap<String, JavaType> byName = new TreeMap<>();
        TreeMap<String, JavaMethod> methodMap = new TreeMap<>();
        TreeMap<String, JavaConstructor> constructorMap = new TreeMap<>();
        TreeMap<String, JavaField> fieldMap = new TreeMap<>();
        for (JavaType type : types) {
            typeMap.put(type.id(), type);
            byName.put(type.qualifiedName(), type);
            type.methods().forEach(m -> methodMap.put(m.id(), m));
            type.constructors().forEach(c -> constructorMap.put(c.id(), c));
            type.fields().forEach(f -> fieldMap.put(f.id(), f));
        }
        this.packages = Collections.unmodifiableSortedMap(packageMap);
        this.packageIds = packageMap.values().stream().map(JavaPackage::id).collect(Collectors.toUnmodifiableSet());
        this.types = Collections.unmodifiableSortedMap(typeMap);
        this.typesByQualifiedName = Collections.unmodifiableMap(byName);
        this.methods = Collections.unmodifiableSortedMap(methodMap);
        this.constructors = Collections.unmodifiableSortedMap(constructorMap);
        this.fields = Collections.unmodifiableSortedMap(fieldMap);
        this.relationships = List.copyOf(relationships);
        this.issues = List.copyOf(issues);
        this.fileStatistics = fileStatistics;
    }

    public String name() {
        return name;
    }

    /** Packages keyed by package name. */
    public SortedMap<String, JavaPackage> packages() {
        return packages;
    }

    /** Types keyed by entity ID. */
    public SortedMap<String, JavaType> types() {
        return types;
    }

    public SortedMap<String, JavaMethod> methods() {
        return methods;
    }

    public SortedMap<String, JavaConstructor> constructors() {
        return constructors;
    }

    public SortedMap<String, JavaField> fields() {
        return fields;
    }

    /** All relationships, sorted and de-duplicated. */
    public List<JavaRelationship> relationships() {
        return relationships;
    }

    public List<AnalysisIssue> issues() {
        return issues;
    }

    public FileStatistics fileStatistics() {
        return fileStatistics;
    }

    public Optional<JavaType> type(String id) {
        return Optional.ofNullable(types.get(id));
    }

    public Optional<JavaType> typeByQualifiedName(String qualifiedName) {
        return Optional.ofNullable(typesByQualifiedName.get(qualifiedName));
    }

    /** Returns the method or constructor with the given ID. */
    public Optional<JavaExecutable> executable(String id) {
        JavaExecutable executable = methods.get(id);
        return Optional.ofNullable(executable != null ? executable : constructors.get(id));
    }

    public Optional<JavaField> field(String id) {
        return Optional.ofNullable(fields.get(id));
    }

    /** True if {@code id} denotes an entity declared in the analysed sources. */
    public boolean containsEntity(String id) {
        return types.containsKey(id) || methods.containsKey(id) || constructors.containsKey(id)
                || fields.containsKey(id) || packageIds.contains(id);
    }

    public long countTypes(TypeKind kind) {
        return types.values().stream().filter(t -> t.kind() == kind).count();
    }
}
