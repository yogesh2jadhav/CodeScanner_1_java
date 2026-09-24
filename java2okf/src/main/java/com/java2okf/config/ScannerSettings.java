package com.java2okf.config;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code scanner:} section of {@code java2okf.yaml}.
 */
public class ScannerSettings {

    /** Default directory names that never contain hand-written sources worth analysing. */
    public static final List<String> DEFAULT_EXCLUDES = List.of("target", "build", "out", "generated", ".git");

    private List<String> extensions = new ArrayList<>(List.of(".java"));

    /** Directory names (not paths) that are skipped wherever they appear in the tree. */
    private List<String> excludeDirectories = new ArrayList<>(DEFAULT_EXCLUDES);

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions == null ? new ArrayList<>() : new ArrayList<>(extensions);
    }

    public List<String> getExcludeDirectories() {
        return excludeDirectories;
    }

    public void setExcludeDirectories(List<String> excludeDirectories) {
        this.excludeDirectories = excludeDirectories == null ? new ArrayList<>() : new ArrayList<>(excludeDirectories);
    }
}
