package com.java2okf.config;

import java.util.List;

/**
 * Values supplied on the command line. A {@code null} component means "not
 * specified", which keeps the value from the configuration file (or default).
 * This is what gives CLI arguments the highest precedence:
 * defaults → configuration file → CLI.
 */
public record ConfigOverrides(
        String source,
        String output,
        String projectName,
        Boolean cleanBeforeGenerate,
        Boolean includeTests,
        Boolean generateMethodDocuments,
        Boolean includeGenerationTimestamp,
        Integer threadCount,
        String logLevel,
        String logFile,
        List<String> classpath) {

    /** An override set that changes nothing. */
    public static ConfigOverrides none() {
        return new ConfigOverrides(null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Applies every specified override to {@code config} in place and returns it. */
    public Java2OkfConfig applyTo(Java2OkfConfig config) {
        if (source != null) {
            config.getProject().setSourceRoot(source);
        }
        if (output != null) {
            config.getOutput().setDirectory(output);
        }
        if (projectName != null) {
            config.getProject().setName(projectName);
        }
        if (cleanBeforeGenerate != null) {
            config.getOutput().setCleanBeforeGenerate(cleanBeforeGenerate);
        }
        if (includeTests != null) {
            config.getAnalysis().setIncludeTests(includeTests);
        }
        if (generateMethodDocuments != null) {
            config.getOkf().setGenerateMethodDocuments(generateMethodDocuments);
        }
        if (includeGenerationTimestamp != null) {
            config.getOkf().setIncludeGenerationTimestamp(includeGenerationTimestamp);
        }
        if (threadCount != null) {
            config.getPerformance().setThreadCount(threadCount);
            // An explicit thread count of 1 is the natural way to ask for serial analysis.
            if (threadCount == 1) {
                config.getPerformance().setParallelAnalysis(false);
            }
        }
        if (logLevel != null) {
            config.getLogging().setLevel(logLevel);
        }
        if (logFile != null) {
            config.getLogging().setFile(logFile);
        }
        if (classpath != null && !classpath.isEmpty()) {
            config.getAnalysis().setClasspath(classpath);
        }
        return config;
    }
}
