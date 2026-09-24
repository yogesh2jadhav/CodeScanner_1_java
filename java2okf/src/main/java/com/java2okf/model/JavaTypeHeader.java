package com.java2okf.model;

import java.util.List;

/**
 * Declaration facts shared by every kind of type.
 *
 * @param id              entity ID, e.g. {@code java-class:com.example.Order}
 * @param qualifiedName   canonical name; nested types use dots ({@code a.Outer.Inner})
 * @param simpleName      simple name
 * @param packageName     package name; empty for the default package
 * @param kind            declaration kind
 * @param location        source location of the declaration
 * @param visibility      access level
 * @param isAbstract      declared {@code abstract} (always false for interfaces, which are implicitly abstract)
 * @param isFinal         declared {@code final}
 * @param isStatic        declared {@code static} (nested types only)
 * @param isSealed        declared {@code sealed}
 * @param annotations     annotations on the declaration
 * @param enclosingTypeId ID of the enclosing type for nested types, otherwise {@code null}
 * @param typeParameters  type parameters as written, e.g. {@code T extends Comparable<T>}
 */
public record JavaTypeHeader(
        String id,
        String qualifiedName,
        String simpleName,
        String packageName,
        TypeKind kind,
        JavaSourceLocation location,
        Visibility visibility,
        boolean isAbstract,
        boolean isFinal,
        boolean isStatic,
        boolean isSealed,
        List<JavaAnnotation> annotations,
        String enclosingTypeId,
        List<String> typeParameters) {

    public JavaTypeHeader {
        annotations = List.copyOf(annotations);
        typeParameters = List.copyOf(typeParameters);
    }
}
