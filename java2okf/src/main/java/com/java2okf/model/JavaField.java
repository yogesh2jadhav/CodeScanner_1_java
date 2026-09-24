package com.java2okf.model;

import java.util.List;

/**
 * A field declaration. One Java declaration with several variables
 * ({@code int a, b;}) produces one {@code JavaField} per variable.
 */
public record JavaField(
        String id,
        String declaringTypeId,
        String name,
        TypeRef type,
        Visibility visibility,
        boolean isStatic,
        boolean isFinal,
        boolean isVolatile,
        boolean isTransient,
        List<JavaAnnotation> annotations,
        JavaSourceLocation location) {

    public JavaField {
        annotations = List.copyOf(annotations);
    }
}
