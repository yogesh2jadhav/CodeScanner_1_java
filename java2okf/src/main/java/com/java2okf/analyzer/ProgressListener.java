package com.java2okf.analyzer;

/**
 * Receives major pipeline phase transitions, e.g. {@code [2/7] Parsing Java files...}.
 */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (step, total, message) -> { };

    void phase(int step, int total, String message);
}
