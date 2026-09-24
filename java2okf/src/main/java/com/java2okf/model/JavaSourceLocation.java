package com.java2okf.model;

/**
 * Location of a declaration or reference in source.
 *
 * @param file      project-relative path with {@code /} separators
 * @param lineStart first line (1-based)
 * @param lineEnd   last line (1-based, inclusive)
 */
public record JavaSourceLocation(String file, int lineStart, int lineEnd) {

    /** Returns {@code File.java:12-40} or {@code File.java:12} for single-line spans. */
    public String shortLabel() {
        String name = file.substring(file.lastIndexOf('/') + 1);
        return lineStart == lineEnd ? name + ":" + lineStart : name + ":" + lineStart + "-" + lineEnd;
    }
}
