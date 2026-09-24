package com.java2okf.model;

import java.util.List;

/**
 * A method declaration. Calls, callers, and other relationships are kept in
 * the {@link JavaProject} relationship list and indexed by the knowledge graph;
 * this record holds only the declaration facts.
 */
public record JavaMethod(
        String id,
        String declaringTypeId,
        String name,
        String signature,
        String declaration,
        TypeRef returnType,
        Visibility visibility,
        boolean isStatic,
        boolean isFinal,
        boolean isAbstract,
        boolean isSynchronized,
        boolean isDefault,
        List<String> typeParameters,
        List<JavaParameter> parameters,
        List<TypeRef> throwsTypes,
        List<JavaAnnotation> annotations,
        JavaSourceLocation location,
        ResolutionStatus signatureStatus) implements JavaExecutable {

    public JavaMethod {
        typeParameters = List.copyOf(typeParameters);
        parameters = List.copyOf(parameters);
        throwsTypes = List.copyOf(throwsTypes);
        annotations = List.copyOf(annotations);
    }
}
