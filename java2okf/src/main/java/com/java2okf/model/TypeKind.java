package com.java2okf.model;

/**
 * Kinds of Java type declarations. The ID prefix is part of the public entity
 * ID format and must never change once bundles have been published.
 */
public enum TypeKind {
    CLASS("java-class"),
    INTERFACE("java-interface"),
    ENUM("java-enum"),
    RECORD("java-record"),
    ANNOTATION("java-annotation");

    private final String idPrefix;

    TypeKind(String idPrefix) {
        this.idPrefix = idPrefix;
    }

    public String idPrefix() {
        return idPrefix;
    }
}
