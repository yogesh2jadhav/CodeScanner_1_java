package com.java2okf.scanner;

import java.nio.file.Path;
import java.util.List;

/**
 * Result of scanning a project directory.
 *
 * @param projectDirectory   the scanned directory (absolute, normalised)
 * @param files              discovered files, sorted by relative path
 * @param sourceRoots        detected source roots, sorted; used to configure symbol resolution
 * @param skippedDirectories number of directories skipped because of exclusion rules
 */
public record SourceFileInventory(
        Path projectDirectory,
        List<SourceFileInfo> files,
        List<Path> sourceRoots,
        int skippedDirectories) {

    public SourceFileInventory {
        files = List.copyOf(files);
        sourceRoots = List.copyOf(sourceRoots);
    }

    public int fileCount() {
        return files.size();
    }
}
