package com.java2okf.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static com.java2okf.testutil.TestProjects.write;
import static com.java2okf.testutil.TestProjects.writeJava;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliTest {

    @TempDir
    Path temp;

    private record Run(int exitCode, String out, String err) {
    }

    private Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = Java2OkfCommand.newCommandLine();
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));
        int exit = commandLine.execute(args);
        return new Run(exit, out.toString(), err.toString());
    }

    private Path sampleProject() {
        Path source = temp.resolve("project");
        writeJava(source, "demo", "Greeter", "public class Greeter { public String greet(String n) { return format(n); } String format(String n) { return n; } }");
        return source;
    }

    @Test
    void helpListsAllCommands() {
        Run run = run("--help");
        assertEquals(0, run.exitCode());
        for (String command : new String[] {"analyze", "validate", "stats", "version"}) {
            assertTrue(run.out().contains(command), command);
        }
    }

    @Test
    void everyCommandHasHelp() {
        for (String command : new String[] {"analyze", "validate", "stats", "version"}) {
            Run run = run(command, "--help");
            assertEquals(0, run.exitCode(), command);
            assertTrue(run.out().contains("Usage: java2okf " + command), command);
        }
        assertTrue(run("analyze", "--help").out().contains("--source"));
    }

    @Test
    void versionPrintsToolAndOkfVersion() {
        Run run = run("version");
        assertEquals(0, run.exitCode());
        assertTrue(run.out().contains("Java2OKF"));
        assertTrue(run.out().contains("OKF version : 0.2"));
    }

    @Test
    void analyzeValidateAndStats() {
        Path source = sampleProject();
        Path output = temp.resolve("bundle");
        Run analyze = run("analyze", "--source", source.toString(), "--output", output.toString(),
                "--log-file", "", "--log-level", "WARN", "--no-timestamps");
        assertEquals(0, analyze.exitCode(), analyze.out() + analyze.err());
        assertTrue(analyze.out().contains("STATUS: PASS"));
        assertTrue(Files.isRegularFile(output.resolve("classes/demo.Greeter.md")));

        Run validate = run("validate", "--bundle", output.toString());
        assertEquals(0, validate.exitCode());
        assertTrue(validate.out().contains("Broken links:"));

        Run stats = run("stats", "--bundle", output.toString());
        assertEquals(0, stats.exitCode(), stats.err());
        assertTrue(stats.out().contains("Java2OKF Statistics"));
        // (?m) with $ accepts both \n and \r\n, so the check also holds on Windows.
        assertTrue(Pattern.compile("(?m)^Classes\\s+: 1$").matcher(stats.out()).find(), stats.out());
        assertTrue(Pattern.compile("(?m)^Methods\\s+: 2$").matcher(stats.out()).find(), stats.out());
    }

    @Test
    void cliOverridesConfigurationFile() {
        Path source = sampleProject();
        Path configured = temp.resolve("configured-output");
        Path overridden = temp.resolve("cli-output");
        Path config = write(temp, "java2okf.yaml", """
                project:
                  name: from-config
                  sourceRoot: %s
                output:
                  directory: %s
                okf:
                  generateMethodDocuments: false
                logging:
                  file: ""
                  level: WARN
                """.formatted(source.toString().replace("\\", "/"), configured.toString().replace("\\", "/")));

        Run run = run("analyze", "--config", config.toString(), "--output", overridden.toString());
        assertEquals(0, run.exitCode(), run.out() + run.err());
        assertTrue(Files.isDirectory(overridden), "CLI --output wins");
        assertFalse(Files.exists(configured));
        assertFalse(Files.exists(overridden.resolve("methods")), "config value applies where the CLI is silent");
        assertTrue(run.out().contains("from-config"));
    }

    @Test
    void invalidConfigurationExitsWithCode2() {
        Path config = write(temp, "bad.yaml", "okf:\n  nonsense: true\n");
        Run run = run("analyze", "--config", config.toString(), "--source", ".", "--output", temp.resolve("o").toString());
        assertEquals(ExitCodes.CONFIG_ERROR, run.exitCode());
        assertTrue(run.err().contains("nonsense"));
        assertEquals(ExitCodes.CONFIG_ERROR, run("analyze", "--log-file", "").exitCode(), "source is required");
    }

    @Test
    void validateFailsOnBrokenBundle() {
        Path bundle = temp.resolve("broken");
        write(bundle, "index.md", "# Index\n\n- [Missing](classes/x.md)\n");
        Run run = run("validate", "--bundle", bundle.toString());
        assertEquals(ExitCodes.VALIDATION_FAILED, run.exitCode());
        assertTrue(run.out().contains("STATUS: FAIL"));
        assertEquals(ExitCodes.CONFIG_ERROR, run("validate", "--bundle", temp.resolve("nope").toString()).exitCode());
        assertEquals(ExitCodes.CONFIG_ERROR, run("stats", "--bundle", bundle.toString()).exitCode(), "stats needs analysis.json");
    }

    @Test
    void refusesToWriteIntoTheSourceDirectory() {
        Path source = sampleProject();
        Run run = run("analyze", "--source", source.toString(), "--output", source.toString(), "--log-file", "",
                "--log-level", "OFF");
        assertEquals(ExitCodes.CONFIG_ERROR, run.exitCode());
    }
}
