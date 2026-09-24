package com.java2okf.model;

/**
 * Why one type depends on another. Categories are attached to the relationship
 * that establishes the dependency and aggregated per type by the knowledge graph.
 */
public enum DependencyCategory {
    FIELD_DEPENDENCY,
    PARAMETER_DEPENDENCY,
    RETURN_TYPE_DEPENDENCY,
    LOCAL_VARIABLE_DEPENDENCY,
    CALL_DEPENDENCY,
    INHERITANCE_DEPENDENCY,
    ANNOTATION_DEPENDENCY,
    /** Declared thrown exception ({@code throws} clause). */
    EXCEPTION_DEPENDENCY,
    /** Cast, instanceof, class literal, method reference, or field access. */
    TYPE_REFERENCE_DEPENDENCY
}
