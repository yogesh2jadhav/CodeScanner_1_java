package com.java2okf.model;

import java.util.List;

/**
 * A package that contains at least one analysed type.
 *
 * @param id      entity ID, e.g. {@code java-package:com.example}
 * @param name    package name; {@code (default)} is never used, the default package has an empty name
 * @param typeIds IDs of all types (including nested ones) in this package, sorted
 */
public record JavaPackage(String id, String name, List<String> typeIds) {

    public JavaPackage {
        typeIds = List.copyOf(typeIds);
    }

    /** Name suitable for display; the default package has no name in Java. */
    public String displayName() {
        return name.isEmpty() ? "(default package)" : name;
    }
}
