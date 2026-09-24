package com.java2okf.config;

/**
 * {@code logging:} section of {@code java2okf.yaml}.
 */
public class LoggingSettings {

    private String level = "INFO";

    /** Log file path; blank disables file logging. */
    private String file = "./logs/java2okf.log";

    private boolean console = true;

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public boolean isConsole() {
        return console;
    }

    public void setConsole(boolean console) {
        this.console = console;
    }
}
