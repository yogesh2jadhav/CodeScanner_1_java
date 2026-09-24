package com.java2okf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Base class of all type declarations.
 *
 * <p>Members are appended while a file is analysed and sorted once by
 * {@link #sortMembers()} when the project model is assembled, so that iteration
 * order never depends on traversal order.</p>
 */
public abstract sealed class JavaType permits JavaClass, JavaInterface, JavaEnum, JavaRecord, JavaAnnotationType {

    private final JavaTypeHeader header;
    private final List<TypeRef> interfaces;
    private final List<JavaField> fields = new ArrayList<>();
    private final List<JavaConstructor> constructors = new ArrayList<>();
    private final List<JavaMethod> methods = new ArrayList<>();
    private final List<String> nestedTypeIds = new ArrayList<>();

    protected JavaType(JavaTypeHeader header, List<TypeRef> interfaces) {
        this.header = header;
        this.interfaces = List.copyOf(interfaces);
    }

    public JavaTypeHeader header() {
        return header;
    }

    public String id() {
        return header.id();
    }

    public String qualifiedName() {
        return header.qualifiedName();
    }

    public String simpleName() {
        return header.simpleName();
    }

    public String packageName() {
        return header.packageName();
    }

    public TypeKind kind() {
        return header.kind();
    }

    public JavaSourceLocation location() {
        return header.location();
    }

    /**
     * Implemented interfaces for classes, enums, and records; extended interfaces
     * for interfaces. Empty for annotation types.
     */
    public List<TypeRef> interfaces() {
        return interfaces;
    }

    public List<JavaField> fields() {
        return Collections.unmodifiableList(fields);
    }

    public List<JavaConstructor> constructors() {
        return Collections.unmodifiableList(constructors);
    }

    public List<JavaMethod> methods() {
        return Collections.unmodifiableList(methods);
    }

    public List<String> nestedTypeIds() {
        return Collections.unmodifiableList(nestedTypeIds);
    }

    public void addField(JavaField field) {
        fields.add(field);
    }

    public void addConstructor(JavaConstructor constructor) {
        constructors.add(constructor);
    }

    public void addMethod(JavaMethod method) {
        methods.add(method);
    }

    public void addNestedTypeId(String nestedTypeId) {
        nestedTypeIds.add(nestedTypeId);
    }

    /**
     * Sorts members into their canonical order. Fields keep declaration order
     * (it is meaningful, e.g. for records and enums' state) with the ID as a
     * tie-breaker; executables and nested types are sorted by ID.
     */
    public void sortMembers() {
        fields.sort(Comparator.comparingInt((JavaField f) -> f.location().lineStart()).thenComparing(JavaField::id));
        constructors.sort(Comparator.comparing(JavaConstructor::id));
        methods.sort(Comparator.comparing(JavaMethod::id));
        Collections.sort(nestedTypeIds);
    }
}
