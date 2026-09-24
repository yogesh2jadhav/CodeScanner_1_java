package com.java2okf.model;

/**
 * An annotation usage on a declaration.
 *
 * @param name          annotation name as written, e.g. {@code Override} or {@code javax.inject.Inject}
 * @param qualifiedName fully qualified name when resolved, otherwise {@code null}
 * @param targetId      entity ID of the annotation type when resolved, otherwise {@code null}
 * @param status        resolution outcome
 */
public record JavaAnnotation(String name, String qualifiedName, String targetId, ResolutionStatus status) {
}
