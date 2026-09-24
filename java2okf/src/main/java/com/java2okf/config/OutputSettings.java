package com.java2okf.config;

/**
 * {@code output:} section of {@code java2okf.yaml}.
 */
public class OutputSettings {

    private String directory = "./output";

    /**
     * When true, the directories and files that Java2OKF owns inside the output
     * directory are removed before generation. Foreign files are never touched.
     */
    private boolean cleanBeforeGenerate = false;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public boolean isCleanBeforeGenerate() {
        return cleanBeforeGenerate;
    }

    public void setCleanBeforeGenerate(boolean cleanBeforeGenerate) {
        this.cleanBeforeGenerate = cleanBeforeGenerate;
    }
}
