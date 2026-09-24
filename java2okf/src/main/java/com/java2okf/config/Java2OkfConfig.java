package com.java2okf.config;

/**
 * Root of the Java2OKF configuration tree. A freshly constructed instance holds
 * the built-in defaults; {@link ConfigLoader} overlays a YAML file and
 * {@link ConfigOverrides} overlays CLI arguments on top of it.
 */
public class Java2OkfConfig {

    private ProjectSettings project = new ProjectSettings();
    private OutputSettings output = new OutputSettings();
    private AnalysisSettings analysis = new AnalysisSettings();
    private ScannerSettings scanner = new ScannerSettings();
    private OkfSettings okf = new OkfSettings();
    private LoggingSettings logging = new LoggingSettings();
    private PerformanceSettings performance = new PerformanceSettings();

    /** Returns a configuration consisting only of built-in defaults. */
    public static Java2OkfConfig defaults() {
        return new Java2OkfConfig();
    }

    public ProjectSettings getProject() {
        return project;
    }

    public void setProject(ProjectSettings project) {
        this.project = project == null ? new ProjectSettings() : project;
    }

    public OutputSettings getOutput() {
        return output;
    }

    public void setOutput(OutputSettings output) {
        this.output = output == null ? new OutputSettings() : output;
    }

    public AnalysisSettings getAnalysis() {
        return analysis;
    }

    public void setAnalysis(AnalysisSettings analysis) {
        this.analysis = analysis == null ? new AnalysisSettings() : analysis;
    }

    public ScannerSettings getScanner() {
        return scanner;
    }

    public void setScanner(ScannerSettings scanner) {
        this.scanner = scanner == null ? new ScannerSettings() : scanner;
    }

    public OkfSettings getOkf() {
        return okf;
    }

    public void setOkf(OkfSettings okf) {
        this.okf = okf == null ? new OkfSettings() : okf;
    }

    public LoggingSettings getLogging() {
        return logging;
    }

    public void setLogging(LoggingSettings logging) {
        this.logging = logging == null ? new LoggingSettings() : logging;
    }

    public PerformanceSettings getPerformance() {
        return performance;
    }

    public void setPerformance(PerformanceSettings performance) {
        this.performance = performance == null ? new PerformanceSettings() : performance;
    }
}
