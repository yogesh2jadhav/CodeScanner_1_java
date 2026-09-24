package com.java2okf.scanner;

import java.nio.file.Path;

/**
 * Immutable description of one discovered source file.
 *
 * @param absolutePath normalised absolute path; used only for reading, never written to output
 * @param relativePath path relative to the scanned project directory, always with {@code /} separators
 * @param packagePath  package as a path (e.g. {@code com/example}); empty for the default package
 * @param packageName  package as declared in the file (e.g. {@code com.example}); empty for the default package
 * @param fileName     file name including extension
 * @param fileSize     size in bytes
 * @param lastModified last modification time in epoch milliseconds
 * @param sourceRoot   directory that corresponds to the default package for this file
 */
public record SourceFileInfo(
        Path absolutePath,
        String relativePath,
        String packagePath,
        String packageName,
        String fileName,
        long fileSize,
        long lastModified,
        Path sourceRoot) implements Comparable<SourceFileInfo> {

    /** Files are ordered by relative path, which gives a machine-independent deterministic order. */
    @Override
    public int compareTo(SourceFileInfo other) {
        return relativePath.compareTo(other.relativePath);
    }
}
