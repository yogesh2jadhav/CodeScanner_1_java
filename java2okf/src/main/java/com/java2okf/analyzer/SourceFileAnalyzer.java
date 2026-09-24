package com.java2okf.analyzer;

import com.java2okf.config.AnalysisSettings;
import com.java2okf.model.AnalysisIssue;
import com.java2okf.model.AnalysisStage;
import com.java2okf.model.IssueSeverity;
import com.java2okf.parser.ParsedSourceFile;
import com.java2okf.resolver.SourceText;
import com.java2okf.resolver.SymbolResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyses one parsed file into a {@link FileAnalysisResult}.
 */
public class SourceFileAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(SourceFileAnalyzer.class);

    private final AnalysisSettings settings;
    private final ClassAnalyzer classAnalyzer = new ClassAnalyzer();

    public SourceFileAnalyzer(AnalysisSettings settings) {
        this.settings = settings;
    }

    /**
     * Never throws: an unexpected failure discards the facts of this file (they
     * could be incomplete and therefore misleading) and records an error issue.
     */
    public FileAnalysisResult analyze(ParsedSourceFile parsed, SymbolResolver resolver) {
        FileAnalysisContext ctx = new FileAnalysisContext(parsed.file(), parsed.unit(), resolver, settings);
        try {
            classAnalyzer.analyze(ctx);
        } catch (RuntimeException | StackOverflowError e) {
            LOG.error("Failed to analyze source file: {} ({})", parsed.file().relativePath(), SourceText.reason(e));
            LOG.debug("Analysis failure details", e);
            ctx.result().discardFacts();
            ctx.result().addIssue(new AnalysisIssue(parsed.file().relativePath(), AnalysisStage.ANALYSIS,
                    IssueSeverity.ERROR, null, "Analysis failed: " + SourceText.reason(e)));
        }
        return ctx.result();
    }
}
