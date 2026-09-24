package com.java2okf;

import com.java2okf.cli.Java2OkfCommand;

/**
 * Entry point of the Java2OKF command-line tool.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(Java2OkfCommand.newCommandLine().execute(args));
    }
}
