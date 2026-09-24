package com.java2okf.model;

import java.util.Locale;

/**
 * Java access level. {@link #PACKAGE_PRIVATE} is the absence of a modifier.
 */
public enum Visibility {
    PUBLIC,
    PROTECTED,
    PACKAGE_PRIVATE,
    PRIVATE;

    /** Lower-case label used in generated documents, e.g. {@code package-private}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
