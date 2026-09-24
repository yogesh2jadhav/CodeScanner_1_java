package com.java2okf.okf;

import com.java2okf.config.OkfSettings;
import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.model.JavaProject;

/**
 * Everything a document generator needs. Generators only see the knowledge
 * model and graph, never JavaParser AST classes.
 */
public record GenerationContext(KnowledgeGraph graph, DocumentLayout layout, Provenance provenance, OkfSettings settings) {

    public JavaProject project() {
        return graph.project();
    }

    public LinkRenderer links(String fromPath) {
        return new LinkRenderer(layout, graph, fromPath);
    }
}
