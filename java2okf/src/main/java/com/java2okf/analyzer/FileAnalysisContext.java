package com.java2okf.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.java2okf.config.AnalysisSettings;
import com.java2okf.model.AnalysisIssue;
import com.java2okf.model.AnalysisStage;
import com.java2okf.model.DependencyCategory;
import com.java2okf.model.IssueSeverity;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.JavaSourceLocation;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;
import com.java2okf.model.TypeRef;
import com.java2okf.resolver.MemberResolution;
import com.java2okf.resolver.SymbolResolver;
import com.java2okf.scanner.SourceFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * Per-file state shared by the analysers: the AST, the resolver, settings, and
 * the result being built. Also centralises how relationships are recorded so
 * that the "never fabricate a target" rule is enforced in one place.
 */
public final class FileAnalysisContext {

    private static final Logger LOG = LoggerFactory.getLogger(FileAnalysisContext.class);

    private final SourceFileInfo file;
    private final CompilationUnit unit;
    private final SymbolResolver resolver;
    private final AnalysisSettings settings;
    private final FileAnalysisResult result;

    public FileAnalysisContext(SourceFileInfo file, CompilationUnit unit, SymbolResolver resolver,
                               AnalysisSettings settings) {
        this.file = file;
        this.unit = unit;
        this.resolver = resolver;
        this.settings = settings;
        this.result = new FileAnalysisResult(file.relativePath());
    }

    public SourceFileInfo file() {
        return file;
    }

    public CompilationUnit unit() {
        return unit;
    }

    public SymbolResolver resolver() {
        return resolver;
    }

    public AnalysisSettings settings() {
        return settings;
    }

    public FileAnalysisResult result() {
        return result;
    }

    public String packageName() {
        return unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
    }

    public JavaSourceLocation location(Node node) {
        return node.getRange()
                .map(r -> new JavaSourceLocation(file.relativePath(), r.begin.line, r.end.line))
                .orElse(new JavaSourceLocation(file.relativePath(), 0, 0));
    }

    /**
     * Records a relationship to a type. Type variables and primitives
     * ({@link ResolutionStatus#NOT_APPLICABLE} without target) are not dependencies
     * and are skipped.
     */
    public void relate(String sourceId, RelationshipType type, TypeRef target, Node at,
                       DependencyCategory category, Map<String, String> details) {
        if (target.status() == ResolutionStatus.NOT_APPLICABLE && target.targetId() == null) {
            return;
        }
        String targetName = target.isResolved() ? target.qualifiedName() : target.displayName();
        String targetId = target.status() == ResolutionStatus.UNRESOLVED || target.status() == ResolutionStatus.AMBIGUOUS
                ? null : target.targetId();
        result.addRelationship(JavaRelationship.of(sourceId, type, targetId, targetName, target.status(),
                location(at), category, details));
    }

    /** Records a relationship to a method, constructor, or field. */
    public void relate(String sourceId, RelationshipType type, MemberResolution target, Node at,
                       DependencyCategory category, Map<String, String> details) {
        Map<String, String> allDetails = new TreeMap<>(details);
        if (target.reason() != null && resolver.isEnabled()) {
            allDetails.put("reason", target.reason());
        }
        if (target.ownerTypeId() != null) {
            // Lets the graph attribute dependencies on library members to the right type.
            allDetails.put(JavaRelationship.DETAIL_DECLARING_TYPE, target.ownerTypeId());
        }
        result.addRelationship(JavaRelationship.of(sourceId, type, target.targetId(), target.targetName(),
                target.status(), location(at), category, allDetails));
    }

    /** Records a structural fact whose target is known by construction (e.g. a type declares its method). */
    public void relateDeclared(String sourceId, RelationshipType type, String targetId, String targetName, Node at) {
        result.addRelationship(JavaRelationship.of(sourceId, type, targetId, targetName, ResolutionStatus.RESOLVED,
                location(at), null, Map.of()));
    }

    /**
     * Records a symbol-resolution problem. Nothing is recorded when resolution is
     * disabled, because then every reference is unresolved by design.
     */
    public void resolutionIssue(Node at, String message) {
        if (!resolver.isEnabled()) {
            return;
        }
        LOG.debug("{}:{} {}", file.relativePath(), location(at).lineStart(), message);
        result.addIssue(new AnalysisIssue(file.relativePath(), AnalysisStage.SYMBOL_RESOLUTION,
                IssueSeverity.WARNING, location(at).lineStart(), message));
    }

    public void analysisIssue(Node at, String message) {
        result.addIssue(new AnalysisIssue(file.relativePath(), AnalysisStage.ANALYSIS, IssueSeverity.WARNING,
                at == null ? null : location(at).lineStart(), message));
    }
}
