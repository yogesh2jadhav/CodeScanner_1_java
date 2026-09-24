package com.java2okf.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Provides the tool name and version. The version comes from a resource file
 * filtered by Maven so that it cannot drift from {@code pom.xml}.
 */
public final class ToolVersion {

    public static final String TOOL_NAME = "Java2OKF";

    /** Identifier used in OKF provenance ({@code generated.by}). */
    public static final String TOOL_ID = "java2okf";

    private static final String VERSION = loadVersion();

    private ToolVersion() {
    }

    public static String version() {
        return VERSION;
    }

    /** Returns e.g. {@code java2okf/1.0.0}. */
    public static String generatorId() {
        return TOOL_ID + "/" + VERSION;
    }

    private static String loadVersion() {
        try (InputStream in = ToolVersion.class.getResourceAsStream("/java2okf-version.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            String value = properties.getProperty("version", "unknown");
            // When running from an IDE without Maven resource filtering the placeholder survives.
            return value.startsWith("${") ? "unknown" : value;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read version resource", e);
        }
    }
}
