package com.java2okf.model;

import java.util.List;

/**
 * Common view of methods and constructors.
 */
public sealed interface JavaExecutable permits JavaMethod, JavaConstructor {

    String id();

    String declaringTypeId();

    String name();

    /**
     * Erased, fully qualified signature used for identity,
     * e.g. {@code process(java.lang.String,com.example.Order)}.
     */
    String signature();

    /** Human-readable declaration as written, e.g. {@code boolean process(String id, Order order)}. */
    String declaration();

    Visibility visibility();

    List<JavaParameter> parameters();

    List<TypeRef> throwsTypes();

    List<JavaAnnotation> annotations();

    JavaSourceLocation location();

    /**
     * {@link ResolutionStatus#RESOLVED} when every parameter type was resolved, so the
     * signature (and therefore the ID) matches the one computed at call sites.
     */
    ResolutionStatus signatureStatus();
}
