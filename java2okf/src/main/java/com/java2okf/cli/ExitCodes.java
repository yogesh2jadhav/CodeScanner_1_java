package com.java2okf.cli;

/**
 * Process exit codes shared by all commands.
 */
public final class ExitCodes {

    /** Command completed and, where applicable, the bundle validated. */
    public static final int OK = 0;

    /** Command ran but the result failed validation. */
    public static final int VALIDATION_FAILED = 1;

    /** Invalid configuration or command-line usage. */
    public static final int CONFIG_ERROR = 2;

    /** Unexpected failure. */
    public static final int INTERNAL_ERROR = 3;

    private ExitCodes() {
    }
}
