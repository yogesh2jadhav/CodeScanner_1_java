package com.java2okf.analyzer;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.java2okf.model.DependencyCategory;
import com.java2okf.model.JavaAnnotation;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.TypeRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts annotation usages and records {@code ANNOTATED_WITH} relationships.
 */
class AnnotationAnalyzer {

    /**
     * Resolves the annotations of {@code node}.
     *
     * @param ownerId entity that the {@code ANNOTATED_WITH} relationships start from
     * @param details extra facts for the relationship (e.g. the annotated parameter)
     */
    List<JavaAnnotation> analyze(FileAnalysisContext ctx, NodeWithAnnotations<?> node, String ownerId,
                                 Map<String, String> details) {
        List<JavaAnnotation> annotations = new ArrayList<>();
        for (AnnotationExpr expr : node.getAnnotations()) {
            JavaAnnotation annotation = ctx.resolver().resolveAnnotation(expr);
            annotations.add(annotation);
            if (ctx.settings().isAnalyzeAnnotations()) {
                TypeRef target = new TypeRef("@" + annotation.name(), annotation.qualifiedName(),
                        annotation.targetId(), annotation.status());
                ctx.relate(ownerId, RelationshipType.ANNOTATED_WITH, target, expr,
                        DependencyCategory.ANNOTATION_DEPENDENCY, details);
            }
        }
        return annotations;
    }
}
