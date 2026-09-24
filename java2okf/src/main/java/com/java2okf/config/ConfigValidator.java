package com.java2okf.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Set;

/**
 * Checks a fully merged configuration before analysis starts, so that invalid
 * settings are reported up-front rather than halfway through a long run.
 */
public final class ConfigValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigValidator.class);

    /** OKF format versions this generator knows how to produce. */
    public static final Set<String> SUPPORTED_OKF_VERSIONS = Set.of("0.2");

    private static final Set<String> LOG_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF");

    /**
     * Validates settings required by the {@code analyze} command.
     *
     * @throws ConfigException describing the first problem found
     */
    public void validateForAnalysis(Java2OkfConfig config) {
        String sourceRoot = config.getProject().getSourceRoot();
        if (sourceRoot == null || sourceRoot.isBlank()) {
            throw new ConfigException("No source directory configured. Use --source or set project.sourceRoot.");
        }
        String output = config.getOutput().getDirectory();
        if (output == null || output.isBlank()) {
            throw new ConfigException("No output directory configured. Use --output or set output.directory.");
        }
        if (config.getScanner().getExtensions().isEmpty()) {
            throw new ConfigException("scanner.extensions must contain at least one extension (e.g. .java).");
        }
        for (String extension : config.getScanner().getExtensions()) {
            if (extension == null || !extension.startsWith(".")) {
                throw new ConfigException("Invalid scanner extension '" + extension + "': extensions must start with '.'");
            }
        }
        if (config.getPerformance().getThreadCount() < 0) {
            throw new ConfigException("performance.threadCount must be >= 0 (0 = automatic).");
        }
        if (!SUPPORTED_OKF_VERSIONS.contains(config.getOkf().getVersion())) {
            throw new ConfigException("Unsupported okf.version '" + config.getOkf().getVersion()
                    + "'. Supported: " + SUPPORTED_OKF_VERSIONS);
        }
        validateLogLevel(config.getLogging().getLevel());
        if (!config.getOkf().isGenerateClassDocuments() && config.getOkf().isGenerateMethodDocuments()) {
            LOG.warn("okf.generateClassDocuments is false: types referenced by method documents are rendered as plain text");
        }
    }

    /** Validates a logging level name. */
    public void validateLogLevel(String level) {
        if (level == null || !LOG_LEVELS.contains(level.toUpperCase(Locale.ROOT))) {
            throw new ConfigException("Invalid logging level '" + level + "'. Use one of " + LOG_LEVELS);
        }
    }
}
