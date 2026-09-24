package com.java2okf.scanner;

import com.java2okf.config.AnalysisSettings;
import com.java2okf.config.ScannerSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Recursively discovers Java source files below a project directory.
 *
 * <p>The scanner only reads files; it never runs build tools. Symbolic links are
 * not followed, which prevents cycles and keeps the scan inside the project.</p>
 */
public class ProjectScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectScanner.class);

    /** Directory names produced by annotation processors and code generators. */
    static final Set<String> GENERATED_DIRECTORY_NAMES = Set.of("generated", "generated-sources", "generated-test-sources");

    private final ScannerSettings scannerSettings;
    private final AnalysisSettings analysisSettings;

    public ProjectScanner(ScannerSettings scannerSettings, AnalysisSettings analysisSettings) {
        this.scannerSettings = scannerSettings;
        this.analysisSettings = analysisSettings;
    }

    /**
     * Scans {@code projectDirectory}.
     *
     * @throws IllegalArgumentException if the directory does not exist
     * @throws UncheckedIOException     if the directory tree cannot be walked
     */
    public SourceFileInventory scan(Path projectDirectory) {
        Path root = projectDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist or is not a directory: " + projectDirectory);
        }
        Set<String> excluded = effectiveExcludes();
        List<String> extensions = scannerSettings.getExtensions().stream()
                .map(e -> e.toLowerCase(Locale.ROOT))
                .toList();
        LOG.debug("Scanning {} (excluded directories: {}, extensions: {})", root, excluded, extensions);

        Collector collector = new Collector(root, excluded, extensions);
        try {
            Files.walkFileTree(root, collector);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to scan " + root, e);
        }

        List<SourceFileInfo> files = new ArrayList<>(collector.files);
        files.sort(null);
        Set<Path> roots = new TreeSet<>();
        files.forEach(f -> roots.add(f.sourceRoot()));
        LOG.debug("Detected source roots: {}", roots);
        return new SourceFileInventory(root, files, new ArrayList<>(roots), collector.skippedDirectories);
    }

    private Set<String> effectiveExcludes() {
        Set<String> excluded = new TreeSet<>(scannerSettings.getExcludeDirectories());
        if (analysisSettings.isIncludeGeneratedSources()) {
            // Generated sources were requested explicitly, so their conventional
            // directory names must not be filtered by the default exclusion list.
            excluded.removeAll(GENERATED_DIRECTORY_NAMES);
        } else {
            excluded.addAll(GENERATED_DIRECTORY_NAMES);
        }
        return excluded;
    }

    /**
     * Returns true for Maven/Gradle test source directories such as
     * {@code src/test}, {@code src/testFixtures} or {@code src/integrationTest}.
     */
    static boolean isTestSourceDirectory(Path dir) {
        Path name = dir.getFileName();
        Path parent = dir.getParent();
        if (name == null || parent == null || parent.getFileName() == null) {
            return false;
        }
        String dirName = name.toString();
        return parent.getFileName().toString().equals("src")
                && (dirName.startsWith("test") || dirName.endsWith("Test") || dirName.endsWith("Tests"));
    }

    private final class Collector extends SimpleFileVisitor<Path> {
        private final Path root;
        private final Set<String> excluded;
        private final List<String> extensions;
        private final List<SourceFileInfo> files = new ArrayList<>();
        private int skippedDirectories;

        Collector(Path root, Set<String> excluded, List<String> extensions) {
            this.root = root;
            this.excluded = excluded;
            this.extensions = extensions;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (dir.equals(root)) {
                return FileVisitResult.CONTINUE;
            }
            String name = dir.getFileName().toString();
            if (excluded.contains(name)) {
                LOG.debug("Skipping excluded directory {}", root.relativize(dir));
                skippedDirectories++;
                return FileVisitResult.SKIP_SUBTREE;
            }
            if (!analysisSettings.isIncludeTests() && isTestSourceDirectory(dir)) {
                LOG.debug("Skipping test source directory {}", root.relativize(dir));
                skippedDirectories++;
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile() && hasSupportedExtension(file)) {
                files.add(describe(file, attrs));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            // One unreadable entry must not abort the scan of the whole project.
            LOG.warn("Unable to read {}: {}", root.relativize(file), exc.getMessage());
            return FileVisitResult.CONTINUE;
        }

        private boolean hasSupportedExtension(Path file) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            return extensions.stream().anyMatch(name::endsWith);
        }

        private SourceFileInfo describe(Path file, BasicFileAttributes attrs) {
            Path absolute = file.toAbsolutePath().normalize();
            String relative = toSlashPath(root.relativize(absolute));
            String packageName = readPackage(absolute, relative);
            Path parent = absolute.getParent();
            Path sourceRoot = deriveSourceRoot(parent, packageName, relative);
            return new SourceFileInfo(
                    absolute,
                    relative,
                    packageName.replace('.', '/'),
                    packageName,
                    absolute.getFileName().toString(),
                    attrs.size(),
                    attrs.lastModifiedTime().toMillis(),
                    sourceRoot);
        }

        private String readPackage(Path file, String relative) {
            try {
                Optional<String> declared = PackageDeclarationReader.read(file);
                return declared.orElse("");
            } catch (IOException e) {
                LOG.warn("Unable to read package declaration of {}: {}", relative, e.getMessage());
                return "";
            }
        }

        /**
         * A file in package {@code a.b} is expected under {@code <root>/a/b}. When the
         * directory layout disagrees, the file's own directory is used as root so the
         * symbol solver can at least resolve siblings.
         */
        private Path deriveSourceRoot(Path parent, String packageName, String relative) {
            if (packageName.isEmpty()) {
                return parent;
            }
            Path candidate = parent;
            String[] segments = packageName.split("\\.");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (candidate == null || candidate.getFileName() == null
                        || !candidate.getFileName().toString().equals(segments[i])) {
                    LOG.warn("Package '{}' does not match directory layout of {}", packageName, relative);
                    return parent;
                }
                candidate = candidate.getParent();
            }
            return candidate == null ? parent : candidate;
        }
    }

    static String toSlashPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
