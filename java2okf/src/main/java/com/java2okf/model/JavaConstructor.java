package com.java2okf.model;

import java.util.List;

/**
 * A constructor declaration (including compact canonical record constructors).
 */
public record JavaConstructor(
        String id,
        String declaringTypeId,
        String name,
        String signature,
        String declaration,
        Visibility visibility,
        List<JavaParameter> parameters,
        List<TypeRef> throwsTypes,
        List<JavaAnnotation> annotations,
        JavaSourceLocation location,
        ResolutionStatus signatureStatus) implements JavaExecutable {

    public JavaConstructor {
        parameters = List.copyOf(parameters);
        throwsTypes = List.copyOf(throwsTypes);
        annotations = List.copyOf(annotations);
    }
}
