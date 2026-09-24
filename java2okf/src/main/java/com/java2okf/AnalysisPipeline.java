package com.java2okf;

import com.java2okf.analyzer.ProgressListener;
import com.java2okf.analyzer.ProjectAnalyzer;
import com.java2okf.config.Java2OkfConfig;
import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.okf.BundleFile;
import com.java2okf.okf.MetadataGenerator;
import com.java2okf.okf.OkfGenerator;
import com.java2okf.okf.OkfWriter;
import com.java2okf.okf.Provenance;
import com.java2okf.scanner.ProjectScanner;
import com.java2okf.scanner.SourceFileInventory;
import com.java2okf.util.ToolVersion;
import com.java2okf.validation.KnowledgeModelValidator;
import com.java2okf.validation.OkfValidator;
import com.java2okf.validation.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The complete Java2OKF pipeline:
 * scan → parse → resolve → model → graph → generate → validate.
 *
 * <p>Markdown is only generated after the whole analysis is complete, and the
 * run is only reported successful after the written bundle has been validated.</p>
 */
public class AnalysisPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisPipeline.class);
    private static final int STEPS = 7;

    private final Clock clock;

    public AnalysisPipeline() {
        this(Clock.systemUTC());
    }

    /** Visible for tests that need a fixed clock. */
    public AnalysisPipeline(Clock clock) {
        this.clock = clock;
    }

    /** Result of a pipeline run. */
    public record Result(KnowledgeGraph graph, List<BundleFile> files, Path outputDirectory, ValidationReport validation) {
        public boolean isSuccessful() {
            return validation.passed();
        }
    }

    /**
     * Runs the pipeline with a fully merged and validated configuration.
     */
    public Result run(Java2OkfConfig config, ProgressListener progress) {
        Instant started = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Path source = Path.of(config.getProject().getSourceRoot()).toAbsolutePath().normalize();
        Path output = Path.of(config.getOutput().getDirectory()).toAbsolutePath().normalize();
        String projectName = config.getProject().getName() != null && !config.getProject().getName().isBlank()
                ? config.getProject().getName()
                : String.valueOf(source.getFileName());

        LOG.info("Starting {} analysis", ToolVersion.TOOL_NAME);
        LOG.info("Source root: {}", source);

        progress.phase(1, STEPS, "Scanning source files...");
        SourceFileInventory inventory = new ProjectScanner(config.getScanner(), config.getAnalysis()).scan(source);
        LOG.info("Java files discovered: {}", inventory.fileCount());

        KnowledgeGraph graph = new ProjectAnalyzer(config, progress).analyze(projectName, inventory, STEPS);

        progress.phase(6, STEPS, "Generating OKF documents...");
        boolean timestamps = config.getOkf().isIncludeGenerationTimestamp();
        Instant completed = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        MetadataGenerator.RunTiming timing = timestamps
                ? new MetadataGenerator.RunTiming(started.toString(), completed.toString(),
                Duration.between(started, completed).toMillis())
                : MetadataGenerator.RunTiming.none();
        Provenance provenance = new Provenance(config.getOkf().isIncludeProvenance(), ToolVersion.generatorId(),
                timestamps ? started.toString() : null);
        List<BundleFile> files = new OkfGenerator().generate(graph, config.getOkf(), provenance, timing,
                config.getAnalysis().isResolveSymbols());
        new OkfWriter().write(output, files, config.getOutput().isCleanBeforeGenerate(), source);
        LOG.info("OKF generation completed: {} files written to {}", files.size(), output);

        progress.phase(7, STEPS, "Validating bundle...");
        ValidationReport report = new OkfValidator().validate(output);
        report = report.merge(new KnowledgeModelValidator().validate(graph));
        return new Result(graph, files, output, report);
    }
}
