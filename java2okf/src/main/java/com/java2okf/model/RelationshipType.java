package com.java2okf.model;

/**
 * Types of directed relationships in the knowledge graph.
 */
public enum RelationshipType {
    /** Package contains a top-level or nested type. */
    CONTAINS,
    /** Type declares a method, constructor, or nested type. */
    DECLARES,
    /** Class extends a class, or interface extends an interface. */
    EXTENDS,
    /** Class, enum, or record implements an interface. */
    IMPLEMENTS,
    /** A top-level type's compilation unit imports a type or package. */
    IMPORTS,
    /** A member uses a type (field type, local variable type, generic argument). */
    USES_TYPE,
    /** Type declares a field. */
    HAS_FIELD,
    /** Method or constructor has a parameter of the target type. */
    HAS_PARAMETER,
    /** Method returns the target type. */
    RETURNS_TYPE,
    /** Method or constructor declares the target exception type. */
    THROWS,
    /** Invocation of a method or constructor. */
    CALLS,
    /** {@code new T(...)} creates an instance of the target type. */
    INSTANTIATES,
    /** Derived summary: a type overrides at least one method of the target ancestor type. */
    OVERRIDES,
    /** A method overrides or implements the target method. */
    OVERRIDES_METHOD,
    /** Field access, method reference, cast, instanceof, or class literal. */
    REFERENCES,
    /** Declaration is annotated with the target annotation type. */
    ANNOTATED_WITH
}
