package com.java2okf.model;

/**
 * Counts of processed source files.
 */
public record FileStatistics(int total, int parsed, int failed) {
}
