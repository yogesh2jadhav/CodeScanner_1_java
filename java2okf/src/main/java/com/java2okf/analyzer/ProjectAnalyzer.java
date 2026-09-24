package com.java2okf.analyzer;

import com.java2okf.config.Java2OkfConfig;
import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.graph.KnowledgeGraphBuilder;
import com.java2okf.model.AnalysisIssue;
import com.java2okf.model.AnalysisStage;
import com.java2okf.model.FileStatistics;
import com.java2okf.model.IssueSeverity;
import com.java2okf.model.JavaProject;
import com.java2okf.model.ResolutionStatus;
import com.java2okf.parser.ParseOutcome;
import com.java2okf.resolver.ResolutionToolkit;
import com.java2okf.scanner.SourceFileInfo;
import com.java2okf.scanner.SourceFileInventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Runs parsing, symbol resolution, per-file analysis, model assembly, and graph
 * construction for a scanned project.
 *
 * <h2>Concurrency</h2>
 * <p>Files are split into fixed partitions (file index modulo worker count).
 * Each partition is processed by exactly one task that owns a private
 * {@link ResolutionToolkit}, because neither JavaParser nor JavaSymbolSolver is
 * thread-safe. Results are merged by {@link KnowledgeModelBuilder}, which sorts
 * everything, so the output does not depend on the worker count or scheduling.</p>
 *
 * <h2>Memory</h2>
 * <p>ASTs are never retained for the whole project: the parse phase only checks
 * syntax, and the analysis phase re-parses each file with symbol resolution
 * enabled and drops the AST as soon as the file's facts are extracted.</p>
 */
public class ProjectAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectAnalyzer.class);

    private final Java2OkfConfig config;
    private final ProgressListener progress;

    public ProjectAnalyzer(Java2OkfConfig config, ProgressListener progress) {
        this.config = config;
        this.progress = progress;
    }

    /** Analyses the inventory; steps 2–5 of the 7-step pipeline. */
    public KnowledgeGraph analyze(String projectName, SourceFileInventory inventory, int totalSteps) {
        int workers = Math.max(1, Math.min(config.getPerformance().effectiveThreadCount(), Math.max(1, inventory.fileCount())));
        List<List<SourceFileInfo>> partitions = partition(inventory.files(), workers);
        List<Path> classpath = config.getAnalysis().getClasspath().stream().map(Path::of).toList();
        boolean resolve = config.getAnalysis().isResolveSymbols();
        LOG.debug("Using {} analysis worker(s)", workers);

        // One toolkit per partition, created inside the owning task and reused by both phases.
        ResolutionToolkit[] toolkits = new ResolutionToolkit[partitions.size()];
        ExecutorService executor = Executors.newFixedThreadPool(workers, namedThreads());
        try {
            progress.phase(2, totalSteps, "Parsing Java files...");
            List<ParseOutcome> outcomes = runPartitions(executor, partitions, index -> {
                toolkits[index] = ResolutionToolkit.create(resolve, inventory.sourceRoots(), classpath);
                return partitions.get(index).stream().map(f -> toolkits[index].parser().parse(f)).toList();
            });
            List<AnalysisIssue> parseIssues = new ArrayList<>();
            Set<SourceFileInfo> failedFiles = new HashSet<>();
            for (ParseOutcome outcome : outcomes) {
                if (!outcome.isSuccess()) {
                    String reason = String.join("; ", outcome.problems());
                    LOG.warn("Unable to parse: {} Reason: {}", outcome.file().relativePath(), reason);
                    parseIssues.add(new AnalysisIssue(outcome.file().relativePath(), AnalysisStage.PARSING,
                            IssueSeverity.ERROR, null, reason));
                    failedFiles.add(outcome.file());
                }
            }
            // Keep the partition assignment so each file stays with the toolkit that parsed it.
            List<List<SourceFileInfo>> parsedPartitions = partitions.stream()
                    .map(files -> files.stream().filter(f -> !failedFiles.contains(f)).toList())
                    .toList();
            int failed = parseIssues.size();
            FileStatistics statistics = new FileStatistics(inventory.fileCount(), inventory.fileCount() - failed, failed);
            LOG.info("Files: total {}, parsed {}, failed {}", statistics.total(), statistics.parsed(), statistics.failed());

            progress.phase(3, totalSteps, resolve ? "Resolving symbols..." : "Analyzing sources (symbol resolution disabled)...");
            SourceFileAnalyzer fileAnalyzer = new SourceFileAnalyzer(config.getAnalysis());
            AtomicInteger lateFailures = new AtomicInteger();
            List<FileAnalysisResult> fragments = runPartitions(executor, parsedPartitions, index -> {
                List<FileAnalysisResult> results = new ArrayList<>();
                for (SourceFileInfo file : parsedPartitions.get(index)) {
                    ParseOutcome outcome = toolkits[index].parser().parse(file);
                    if (outcome.parsed().isEmpty()) {
                        // Cannot normally happen (same input, same parser); reported rather than ignored.
                        lateFailures.incrementAndGet();
                        FileAnalysisResult failure = new FileAnalysisResult(file.relativePath());
                        failure.addIssue(new AnalysisIssue(file.relativePath(), AnalysisStage.PARSING, IssueSeverity.ERROR,
                                null, String.join("; ", outcome.problems())));
                        results.add(failure);
                        continue;
                    }
                    results.add(fileAnalyzer.analyze(outcome.parsed().get(), toolkits[index].resolver()));
                }
                return results;
            });
            if (lateFailures.get() > 0) {
                statistics = new FileStatistics(statistics.total(), statistics.parsed() - lateFailures.get(),
                        statistics.failed() + lateFailures.get());
            }

            progress.phase(4, totalSteps, "Building knowledge model...");
            JavaProject project = new KnowledgeModelBuilder().build(projectName, fragments, parseIssues, statistics);
            LOG.info("Types discovered: {} ({} packages)", project.types().size(), project.packages().size());
            LOG.info("Methods discovered: {} (+ {} constructors), fields: {}", project.methods().size(),
                    project.constructors().size(), project.fields().size());

            progress.phase(5, totalSteps, "Building relationship graph...");
            KnowledgeGraph graph = new KnowledgeGraphBuilder().build(project);
            long unresolved = graph.relationships().stream().filter(r -> !r.isResolved()
                    && r.status() != ResolutionStatus.NOT_APPLICABLE).count();
            LOG.info("Relationships discovered: {} ({} unresolved or ambiguous)", graph.relationships().size(), unresolved);
            long resolutionIssues = project.issues().stream().filter(i -> i.stage() == AnalysisStage.SYMBOL_RESOLUTION).count();
            if (resolutionIssues > 0) {
                LOG.warn("{} references could not be resolved; see _metadata/errors.json", resolutionIssues);
            }
            return graph;
        } finally {
            executor.shutdownNow();
        }
    }

    private static <T> List<T> runPartitions(ExecutorService executor, List<List<SourceFileInfo>> partitions,
                                             Function<Integer, List<T>> task) {
        List<Future<List<T>>> futures = new ArrayList<>();
        for (int index = 0; index < partitions.size(); index++) {
            int partition = index;
            futures.add(executor.submit(() -> task.apply(partition)));
        }
        List<T> results = new ArrayList<>();
        try {
            for (Future<List<T>> future : futures) {
                results.addAll(future.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Analysis interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Analysis worker failed: " + e.getCause().getMessage(), e.getCause());
        }
        return results;
    }

    /** Deterministic round-robin partitioning of the (already sorted) file list. */
    static List<List<SourceFileInfo>> partition(List<SourceFileInfo> files, int workers) {
        List<List<SourceFileInfo>> partitions = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            partitions.add(new ArrayList<>());
        }
        for (int i = 0; i < files.size(); i++) {
            partitions.get(i % workers).add(files.get(i));
        }
        return partitions;
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "java2okf-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
