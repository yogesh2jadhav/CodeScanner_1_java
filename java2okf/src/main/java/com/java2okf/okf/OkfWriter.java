package com.java2okf.okf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes bundle files to disk.
 *
 * <p>Cleaning is deliberately conservative: only the directories and files that
 * Java2OKF generates are removed, and only when the output directory is not the
 * analysed project (or one of its parents) and either looks like a previous
 * bundle or contains none of the generated names yet.</p>
 */
public class OkfWriter {

    private static final Logger LOG = LoggerFactory.getLogger(OkfWriter.class);

    /** Entries of the output directory owned by Java2OKF. */
    static final List<String> OWNED_ENTRIES = List.of("packages", "classes", "interfaces", "enums", "records",
            "annotations", "methods", DocumentLayout.METADATA_DIR, DocumentLayout.INDEX, DocumentLayout.LOG);

    /**
     * Writes {@code files} below {@code outputDirectory}.
     *
     * @param clean         remove previously generated entries first
     * @param sourceDirectory analysed project directory, used for safety checks
     */
    public void write(Path outputDirectory, List<BundleFile> files, boolean clean, Path sourceDirectory) {
        Path output = outputDirectory.toAbsolutePath().normalize();
        Path source = sourceDirectory.toAbsolutePath().normalize();
        if (source.startsWith(output)) {
            throw new IllegalArgumentException("Output directory " + output
                    + " must not be the source directory or one of its parents");
        }
        try {
            Files.createDirectories(output);
            if (clean) {
                clean(output);
            } else if (OWNED_ENTRIES.stream().anyMatch(e -> Files.exists(output.resolve(e)))) {
                LOG.warn("Output directory already contains a bundle; documents that no longer exist in the source "
                        + "will remain. Use --clean (output.cleanBeforeGenerate) to remove them.");
            }
            for (BundleFile file : files) {
                Path target = output.resolve(file.path()).normalize();
                if (!target.startsWith(output)) {
                    throw new IllegalStateException("Refusing to write outside the output directory: " + file.path());
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write bundle to " + output, e);
        }
        LOG.debug("Wrote {} files to {}", files.size(), output);
    }

    private void clean(Path output) throws IOException {
        boolean previousBundle = Files.isRegularFile(output.resolve(MetadataGenerator.ANALYSIS_JSON));
        boolean anyOwned = OWNED_ENTRIES.stream().anyMatch(e -> Files.exists(output.resolve(e)));
        if (anyOwned && !previousBundle) {
            throw new IllegalArgumentException("Refusing to clean " + output + ": it contains entries named like "
                    + "bundle directories but no " + MetadataGenerator.ANALYSIS_JSON + " from a previous run");
        }
        for (String entry : OWNED_ENTRIES) {
            deleteRecursively(output.resolve(entry));
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
