package com.java2okf.cli;

import com.java2okf.util.ToolVersion;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import picocli.CommandLine;

/**
 * Top-level {@code java2okf} command. It only dispatches to subcommands.
 */
@Command(
        name = "java2okf",
        mixinStandardHelpOptions = true,
        versionProvider = Java2OkfCommand.VersionProvider.class,
        description = "Deterministic Java static analysis that produces an OKF v0.2 knowledge bundle.",
        subcommands = {
                AnalyzeCommand.class,
                ValidateCommand.class,
                StatsCommand.class,
                VersionCommand.class,
                CommandLine.HelpCommand.class
        })
public class Java2OkfCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    /** Creates a fully configured command line; shared by {@code Main} and tests. */
    public static CommandLine newCommandLine() {
        CommandLine commandLine = new CommandLine(new Java2OkfCommand());
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        return commandLine;
    }

    @Override
    public void run() {
        // Invoked without a subcommand: show usage instead of silently doing nothing.
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /** Supplies {@code --version} output from the Maven-filtered version resource. */
    public static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {ToolVersion.TOOL_NAME + " " + ToolVersion.version()};
        }
    }
}
