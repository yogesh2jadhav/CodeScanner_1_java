package com.java2okf.model;

/**
 * A reference to a type as it appears in a declaration, e.g. a field or
 * parameter type.
 *
 * @param displayName   the type as written in source, e.g. {@code List<Order>}
 * @param qualifiedName the erased fully qualified name of the outermost type, e.g.
 *                      {@code java.util.List}; the keyword for primitives; {@code null} when unresolved
 * @param targetId      entity ID of the referenced type, or {@code null} if not a resolved reference type
 * @param status        resolution outcome
 */
public record TypeRef(String displayName, String qualifiedName, String targetId, ResolutionStatus status) {

    /** Primitive types, {@code void}, and type variables: resolution is not applicable. */
    public static TypeRef notApplicable(String displayName) {
        return new TypeRef(displayName, displayName, null, ResolutionStatus.NOT_APPLICABLE);
    }

    public static TypeRef unresolved(String displayName) {
        return new TypeRef(displayName, null, null, ResolutionStatus.UNRESOLVED);
    }

    public static TypeRef resolved(String displayName, String qualifiedName, String targetId) {
        return new TypeRef(displayName, qualifiedName, targetId, ResolutionStatus.RESOLVED);
    }

    public boolean isResolved() {
        return status == ResolutionStatus.RESOLVED;
    }
}
