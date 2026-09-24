package com.java2okf.config;

/**
 * {@code performance:} section of {@code java2okf.yaml}.
 */
public class PerformanceSettings {

    /** Upper bound for the automatic worker count; every worker owns its own symbol-solver caches. */
    static final int MAX_AUTO_THREADS = 4;

    private boolean parallelAnalysis = true;

    /** {@code 0} selects a value automatically. */
    private int threadCount = 0;

    public boolean isParallelAnalysis() {
        return parallelAnalysis;
    }

    public void setParallelAnalysis(boolean parallelAnalysis) {
        this.parallelAnalysis = parallelAnalysis;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Returns the number of analysis workers to use. Workers are bounded because
     * each one keeps a private copy of the symbol-solver caches (see
     * {@code AnalysisWorker}), so memory grows linearly with the worker count.
     */
    public int effectiveThreadCount() {
        if (!parallelAnalysis) {
            return 1;
        }
        if (threadCount > 0) {
            return threadCount;
        }
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), MAX_AUTO_THREADS));
    }
}
