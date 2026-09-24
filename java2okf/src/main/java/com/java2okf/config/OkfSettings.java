package com.java2okf.config;

/**
 * {@code okf:} section of {@code java2okf.yaml}. These switches only affect how
 * the knowledge model is materialised; they never reduce what is analysed.
 */
public class OkfSettings {

    private String version = "0.2";
    private boolean generateIndexes = true;
    private boolean generateLogs = true;
    private boolean generateClassDocuments = true;
    private boolean generateMethodDocuments = true;
    private boolean includeProvenance = true;

    /** Disable for byte-for-byte reproducible bundles. */
    private boolean includeGenerationTimestamp = true;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isGenerateIndexes() {
        return generateIndexes;
    }

    public void setGenerateIndexes(boolean generateIndexes) {
        this.generateIndexes = generateIndexes;
    }

    public boolean isGenerateLogs() {
        return generateLogs;
    }

    public void setGenerateLogs(boolean generateLogs) {
        this.generateLogs = generateLogs;
    }

    public boolean isGenerateClassDocuments() {
        return generateClassDocuments;
    }

    public void setGenerateClassDocuments(boolean generateClassDocuments) {
        this.generateClassDocuments = generateClassDocuments;
    }

    public boolean isGenerateMethodDocuments() {
        return generateMethodDocuments;
    }

    public void setGenerateMethodDocuments(boolean generateMethodDocuments) {
        this.generateMethodDocuments = generateMethodDocuments;
    }

    public boolean isIncludeProvenance() {
        return includeProvenance;
    }

    public void setIncludeProvenance(boolean includeProvenance) {
        this.includeProvenance = includeProvenance;
    }

    public boolean isIncludeGenerationTimestamp() {
        return includeGenerationTimestamp;
    }

    public void setIncludeGenerationTimestamp(boolean includeGenerationTimestamp) {
        this.includeGenerationTimestamp = includeGenerationTimestamp;
    }
}
