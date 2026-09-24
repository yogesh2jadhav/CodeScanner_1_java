package com.java2okf.testutil;

import com.java2okf.analyzer.FileAnalysisResult;
import com.java2okf.analyzer.KnowledgeModelBuilder;
import com.java2okf.analyzer.SourceFileAnalyzer;
import com.java2okf.config.AnalysisSettings;
import com.java2okf.config.ScannerSettings;
import com.java2okf.model.FileStatistics;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.RelationshipType;
import com.java2okf.parser.JavaParserEngine;
import com.java2okf.parser.ParseOutcome;
import com.java2okf.resolver.DisabledSymbolResolver;
import com.java2okf.resolver.ResolutionToolkit;
import com.java2okf.resolver.SymbolResolver;
import com.java2okf.scanner.ProjectScanner;
import com.java2okf.scanner.SourceFileInfo;
import com.java2okf.scanner.SourceFileInventory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Runs scanner → parser → analysers → model builder on a test project.
 */
public final class AnalysisFixture {

    private AnalysisFixture() {
    }

    /** Analyses without symbol resolution. */
    public static JavaProject analyzeUnresolved(Path project) {
        return analyze(project, new AnalysisSettings(), inventory -> new JavaParserEngine(), inventory -> new DisabledSymbolResolver());
    }

    /** Analyses with JavaSymbolSolver, using the detected source roots. */
    public static JavaProject analyzeResolved(Path project) {
        return analyzeResolved(project, new AnalysisSettings());
    }

    public static JavaProject analyzeResolved(Path project, AnalysisSettings settings) {
        ResolutionToolkit[] toolkit = new ResolutionToolkit[1];
        return analyze(project, settings,
                inventory -> {
                    toolkit[0] = ResolutionToolkit.create(true, inventory.sourceRoots(), List.of());
                    return toolkit[0].parser();
                },
                inventory -> toolkit[0].resolver());
    }

    /**
     * Generic pipeline used by tests; the factories receive the inventory so that
     * resolver-backed variants can configure source roots.
     */
    public static JavaProject analyze(Path project, AnalysisSettings settings,
                                      Function<SourceFileInventory, JavaParserEngine> parserFactory,
                                      Function<SourceFileInventory, SymbolResolver> resolverFactory) {
        SourceFileInventory inventory = new ProjectScanner(new ScannerSettings(), settings).scan(project);
        JavaParserEngine parser = parserFactory.apply(inventory);
        SymbolResolver resolver = resolverFactory.apply(inventory);
        SourceFileAnalyzer analyzer = new SourceFileAnalyzer(settings);
        List<FileAnalysisResult> results = new ArrayList<>();
        int failed = 0;
        for (SourceFileInfo file : inventory.files()) {
            ParseOutcome outcome = parser.parse(file);
            if (outcome.isSuccess()) {
                results.add(analyzer.analyze(outcome.parsed().orElseThrow(), resolver));
            } else {
                failed++;
            }
        }
        return new KnowledgeModelBuilder().build("test", results, List.of(),
                new FileStatistics(inventory.fileCount(), inventory.fileCount() - failed, failed));
    }

    public static List<JavaRelationship> relationships(JavaProject project, String sourceId, RelationshipType type) {
        return project.relationships().stream()
                .filter(r -> r.sourceId().equals(sourceId) && r.type() == type)
                .toList();
    }

    public static List<String> targetNames(JavaProject project, String sourceId, RelationshipType type) {
        return relationships(project, sourceId, type).stream().map(JavaRelationship::targetName).toList();
    }
}
