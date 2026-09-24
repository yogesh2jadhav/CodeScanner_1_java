package com.java2okf.cli;

import com.java2okf.validation.OkfValidator;
import com.java2okf.validation.ValidationReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code java2okf validate}: validates an existing bundle.
 */
@Command(name = "validate", mixinStandardHelpOptions = true,
        description = "Validate an OKF bundle: UTF-8, frontmatter, types, links, locations, IDs, and relationship markers.",
        footer = {"", "Exit codes: 0 = PASS, 1 = FAIL, 2 = bundle directory not found."})
public class ValidateCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-b", "--bundle"}, required = true, paramLabel = "DIR", description = "Bundle directory to validate.")
    private Path bundle;

    @Option(names = "--max-problems", paramLabel = "N", defaultValue = "50", description = "Maximum problems to list (default: ${DEFAULT-VALUE}).")
    private int maxProblems;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        if (!Files.isDirectory(bundle)) {
            spec.commandLine().getErr().println("Bundle directory not found: " + bundle);
            spec.commandLine().getErr().flush();
            return ExitCodes.CONFIG_ERROR;
        }
        ValidationReport report = new OkfValidator().validate(bundle);
        out.print(report.format(maxProblems));
        out.flush();
        return report.passed() ? ExitCodes.OK : ExitCodes.VALIDATION_FAILED;
    }
}
