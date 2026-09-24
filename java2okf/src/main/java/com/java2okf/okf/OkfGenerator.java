package com.java2okf.okf;

import com.java2okf.config.OkfSettings;
import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.model.JavaExecutable;
import com.java2okf.model.JavaPackage;
import com.java2okf.model.JavaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Materialises the knowledge graph as an OKF v0.2 Markdown bundle (in memory).
 * Writing to disk is done separately by {@link OkfWriter}.
 */
public class OkfGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(OkfGenerator.class);

    private final ClassDocumentGenerator classGenerator = new ClassDocumentGenerator();
    private final MethodDocumentGenerator methodGenerator = new MethodDocumentGenerator();
    private final PackageDocumentGenerator packageGenerator = new PackageDocumentGenerator();
    private final IndexGenerator indexGenerator = new IndexGenerator();
    private final LogGenerator logGenerator = new LogGenerator();
    private final MetadataGenerator metadataGenerator = new MetadataGenerator();
    private final FrontmatterSerializer serializer = new FrontmatterSerializer();

    /**
     * Generates all bundle files, sorted by path.
     *
     * @param timing           run timing for metadata; use {@link MetadataGenerator.RunTiming#none()} for reproducible output
     * @param symbolResolution whether symbol resolution was enabled (recorded in metadata)
     */
    public List<BundleFile> generate(KnowledgeGraph graph, OkfSettings settings, Provenance provenance,
                                     MetadataGenerator.RunTiming timing, boolean symbolResolution) {
        DocumentLayout layout = DocumentLayout.plan(graph.project(), settings);
        GenerationContext ctx = new GenerationContext(graph, layout, provenance, settings);
        List<OkfDocument> documents = new ArrayList<>();

        for (JavaPackage javaPackage : graph.project().packages().values()) {
            documents.add(packageGenerator.generate(ctx, javaPackage));
        }
        for (JavaType type : graph.project().types().values()) {
            if (settings.isGenerateClassDocuments()) {
                documents.add(classGenerator.generate(ctx, type));
            }
            if (settings.isGenerateMethodDocuments()) {
                for (JavaExecutable executable : type.constructors()) {
                    documents.add(methodGenerator.generate(ctx, type, executable));
                }
                for (JavaExecutable executable : type.methods()) {
                    documents.add(methodGenerator.generate(ctx, type, executable));
                }
            }
        }
        if (settings.isGenerateIndexes()) {
            documents.addAll(indexGenerator.generate(ctx));
        }

        int documentCount = documents.size() + (settings.isGenerateLogs() ? 1 : 0);
        AnalysisMetadata metadata = metadataGenerator.metadata(graph, timing, settings.getVersion(), documentCount,
                symbolResolution);
        if (settings.isGenerateLogs()) {
            documents.add(logGenerator.generate(ctx, metadata));
        }

        List<BundleFile> files = new ArrayList<>();
        documents.forEach(d -> files.add(d.render(serializer)));
        files.add(metadataGenerator.analysisFile(metadata));
        files.add(metadataGenerator.errorsFile(graph.project().issues()));
        files.sort(null);
        LOG.debug("Generated {} documents", documentCount);
        return files;
    }
}
