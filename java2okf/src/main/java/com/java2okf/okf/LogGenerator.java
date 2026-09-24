package com.java2okf.okf;

import com.java2okf.util.ToolVersion;

/**
 * Generates {@code log.md}, the human-readable record of how the bundle was produced.
 */
public class LogGenerator {

    public OkfDocument generate(GenerationContext ctx, AnalysisMetadata metadata) {
        MarkdownBuilder md = new MarkdownBuilder();
        md.heading(1, "Generation Log");
        String when = metadata.completedAt() != null ? metadata.completedAt() : "Analysis run";
        md.heading(2, when + " — " + ToolVersion.TOOL_NAME + " " + ToolVersion.version());
        md.bullet("Project: " + MarkdownBuilder.code(metadata.project()));
        md.bullet("OKF version: " + metadata.okfVersion());
        md.bullet("Symbol resolution: " + (metadata.symbolResolution() ? "enabled" : "disabled"));
        md.bullet("Source files: " + metadata.sourceFiles() + " (parsed " + metadata.parsedFiles()
                + ", failed " + metadata.failedFiles() + ")");
        md.bullet("Types: " + (metadata.classes() + metadata.interfaces() + metadata.enums() + metadata.records()
                + metadata.annotations()) + " in " + metadata.packages() + " packages");
        md.bullet("Methods: " + metadata.methods() + ", constructors: " + metadata.constructors()
                + ", fields: " + metadata.fields());
        md.bullet("Relationships: " + metadata.relationships() + " (resolved " + metadata.resolvedRelationships()
                + ", unresolved " + metadata.unresolvedRelationships() + ", ambiguous " + metadata.ambiguousRelationships()
                + ", not applicable " + metadata.notApplicableRelationships() + ")");
        md.bullet("Analysis issues: " + metadata.issues() + " — see "
                + MarkdownBuilder.link("errors.json", MetadataGenerator.ERRORS_JSON));
        md.bullet("Run metadata: " + MarkdownBuilder.link("analysis.json", MetadataGenerator.ANALYSIS_JSON));
        md.endList();
        md.paragraph("All facts were extracted deterministically by static analysis; no language model was used. "
                + "The bundle is only reported as valid after the built-in validator has checked it.");
        OkfFrontmatter frontmatter = OkfFrontmatter.of(OkfDocumentType.LOG, null, "Generation Log").provenance(ctx.provenance());
        return new OkfDocument(DocumentLayout.LOG, frontmatter.build(), md.build());
    }
}
