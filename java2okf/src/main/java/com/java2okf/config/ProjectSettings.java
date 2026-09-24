package com.java2okf.config;

/**
 * {@code project:} section of {@code java2okf.yaml}.
 */
public class ProjectSettings {

    /** Human-readable project name. Defaults to the source directory name when absent. */
    private String name;

    /** Directory that is scanned recursively for Java sources. */
    private String sourceRoot;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }
}
