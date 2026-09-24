package com.java2okf.cli;

import com.java2okf.config.ConfigValidator;
import com.java2okf.util.ToolVersion;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * {@code java2okf version}: prints tool, OKF, and runtime versions.
 */
@Command(name = "version", mixinStandardHelpOptions = true,
        description = "Print Java2OKF, supported OKF, and Java runtime versions.")
public class VersionCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        out.println(ToolVersion.TOOL_NAME + " " + ToolVersion.version());
        out.println("OKF version : " + String.join(", ", ConfigValidator.SUPPORTED_OKF_VERSIONS));
        out.println("Java runtime: " + Runtime.version());
        out.flush();
        return ExitCodes.OK;
    }
}
