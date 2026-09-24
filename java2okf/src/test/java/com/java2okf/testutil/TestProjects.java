package com.java2okf.testutil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helpers for writing small throw-away Java projects in tests.
 */
public final class TestProjects {

    private TestProjects() {
    }

    /** Writes {@code content} to {@code root/relativePath}, creating parent directories. */
    public static Path write(Path root, String relativePath, String content) {
        try {
            Path file = root.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes a Java source under {@code src/main/java}, deriving the path from package and type name. */
    public static Path writeJava(Path root, String packageName, String typeName, String body) {
        String dir = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
        String header = packageName.isEmpty() ? "" : "package " + packageName + ";\n\n";
        return write(root, "src/main/java/" + dir + typeName + ".java", header + body);
    }
}
