package com.java2okf.cli;

import com.java2okf.AnalysisPipeline;
import com.java2okf.analyzer.ProgressListener;
import com.java2okf.config.ConfigException;
import com.java2okf.config.ConfigLoader;
import com.java2okf.config.ConfigOverrides;
import com.java2okf.config.ConfigValidator;
import com.java2okf.config.Java2OkfConfig;
import com.java2okf.model.JavaProject;
import com.java2okf.util.LoggingConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code java2okf analyze}: runs the full pipeline and validates the result.
 */
@Command(name = "analyze", mixinStandardHelpOptions = true, sortOptions = false,
        description = {
                "Analyse a Java project and generate an OKF knowledge bundle.",
                "Values are taken from built-in defaults, then --config, then the options below (highest precedence)."
        },
        footer = {"", "Exit codes: 0 = success and bundle valid, 1 = bundle failed validation,",
                "            2 = invalid configuration or arguments, 3 = unexpected error."})
public class AnalyzeCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyzeCommand.class);

    @Spec
    private CommandSpec spec;

    @Option(names = {"-c", "--config"}, paramLabel = "FILE", description = "YAML configuration file (e.g. config/java2okf.yaml).")
    private Path config;

    @Option(names = {"-s", "--source"}, paramLabel = "DIR", description = "Java project directory to scan (project.sourceRoot).")
    private String source;

    @Option(names = {"-o", "--output"}, paramLabel = "DIR", description = "Bundle output directory (output.directory).")
    private String output;

    @Option(names = "--project-name", paramLabel = "NAME", description = "Project name (project.name). Default: source directory name.")
    private String projectName;

    @Option(names = "--clean", negatable = true, description = "Remove previously generated bundle entries first (output.cleanBeforeGenerate).")
    private Boolean clean;

    @Option(names = "--include-tests", negatable = true, description = "Also analyse test sources such as src/test (analysis.includeTests).")
    private Boolean includeTests;

    @Option(names = "--method-docs", negatable = true, description = "Generate one document per method (okf.generateMethodDocuments). Use --no-method-docs to disable.")
    private Boolean methodDocs;

    @Option(names = "--timestamps", negatable = true, description = "Write generation timestamps (okf.includeGenerationTimestamp). Use --no-timestamps for reproducible output.")
    private Boolean timestamps;

    @Option(names = "--threads", paramLabel = "N", description = "Analysis worker threads; 0 = automatic, 1 = serial (performance.threadCount).")
    private Integer threads;

    @Option(names = "--classpath", paramLabel = "JAR", split = ",", description = "Library jars used for symbol resolution only (analysis.classpath).")
    private List<String> classpath;

    @Option(names = "--log-level", paramLabel = "LEVEL", description = "TRACE, DEBUG, INFO, WARN, or ERROR (logging.level).")
    private String logLevel;

    @Option(names = "--log-file", paramLabel = "FILE", description = "Log file; empty string disables file logging (logging.file).")
    private String logFile;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        Java2OkfConfig configuration;
        try {
            configuration = new ConfigLoader().load(config);
            new ConfigOverrides(source, output, projectName, clean, includeTests, methodDocs, timestamps, threads,
                    logLevel, logFile, classpath).applyTo(configuration);
            new ConfigValidator().validateForAnalysis(configuration);
        } catch (ConfigException e) {
            err.println("Configuration error: " + e.getMessage());
            err.flush();
            return ExitCodes.CONFIG_ERROR;
        }
        LoggingConfigurer.configure(configuration.getLogging());

        try {
            ProgressListener progress = (step, total, message) -> LOG.info("[{}/{}] {}", step, total, message);
            AnalysisPipeline.Result result = new AnalysisPipeline().run(configuration, progress);
            printSummary(out, result);
            if (!result.isSuccessful()) {
                LOG.error("Generated bundle failed validation; see the report above");
                return ExitCodes.VALIDATION_FAILED;
            }
            LOG.info("Analysis completed successfully");
            return ExitCodes.OK;
        } catch (IllegalArgumentException e) {
            LOG.error("{}", e.getMessage());
            return ExitCodes.CONFIG_ERROR;
        } catch (RuntimeException e) {
            LOG.error("Analysis failed: {}", e.getMessage(), e);
            return ExitCodes.INTERNAL_ERROR;
        }
    }

    private static void printSummary(PrintWriter out, AnalysisPipeline.Result result) {
        JavaProject project = result.graph().project();
        out.println();
        out.println("Java2OKF analysis of '" + project.name() + "'");
        out.println();
        out.println("Files:");
        out.println("  total: " + project.fileStatistics().total());
        out.println("  parsed: " + project.fileStatistics().parsed());
        out.println("  failed: " + project.fileStatistics().failed());
        out.println("Types: " + project.types().size() + ", methods: " + project.methods().size()
                + ", constructors: " + project.constructors().size() + ", fields: " + project.fields().size());
        out.println("Relationships: " + result.graph().relationships().size());
        out.println("Analysis issues: " + project.issues().size() + " (see _metadata/errors.json)");
        out.println("Bundle: " + result.outputDirectory() + " (" + result.files().size() + " files)");
        out.println();
        out.print(result.validation().format(20));
        out.flush();
    }
}
