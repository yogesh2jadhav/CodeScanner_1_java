package com.java2okf.okf;

import java.util.List;

/**
 * Textual markers used when a relationship target cannot be linked. The
 * validator relies on the same constants to check that every plain-text
 * relationship is explicitly qualified.
 */
public final class OkfMarkers {

    public static final String UNRESOLVED = "UNRESOLVED";
    public static final String AMBIGUOUS = "AMBIGUOUS";
    /** Resolved to a type or member outside the analysed sources (JDK, libraries). */
    public static final String EXTERNAL = "(external)";
    /** Resolved to a compiler-generated member of an analysed type, e.g. {@code values()}. */
    public static final String IMPLICIT = "(implicit)";
    /** Resolved to an analysed entity whose document was disabled by configuration. */
    public static final String NO_DOCUMENT = "(no document)";

    /** Every marker that qualifies an unlinked relationship. */
    public static final List<String> ALL = List.of(UNRESOLVED, AMBIGUOUS, EXTERNAL, IMPLICIT, NO_DOCUMENT);

    private OkfMarkers() {
    }
}
