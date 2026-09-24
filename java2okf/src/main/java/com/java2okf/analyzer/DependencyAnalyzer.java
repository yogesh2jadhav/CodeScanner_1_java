package com.java2okf.analyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.RecordPatternExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.Type;
import com.java2okf.model.DependencyCategory;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.TypeRef;

import java.util.Map;

/**
 * Records type dependencies that arise inside executable code: local variable
 * types ({@code USES_TYPE}) and casts, {@code instanceof}, patterns, class
 * literals, and array creation ({@code REFERENCES}).
 *
 * <p>Declaration-level dependencies (fields, parameters, return types, thrown
 * exceptions, supertypes) are recorded by the declaration analysers.</p>
 */
class DependencyAnalyzer implements BodyAnalyzer {

    @Override
    public void visit(FileAnalysisContext ctx, String ownerId, Node node) {
        if (node instanceof VariableDeclarationExpr declaration) {
            if (ctx.settings().isAnalyzeLocalVariables()) {
                declaration.getVariables().forEach(v -> recordLocalVariable(ctx, ownerId, v));
            }
            return;
        }
        if (node instanceof Parameter parameter) {
            // Parameters inside bodies are lambda, catch, or local/anonymous class parameters.
            if (ctx.settings().isAnalyzeLocalVariables() && !parameter.getType().isUnknownType()) {
                recordTypes(ctx, ownerId, RelationshipType.USES_TYPE, parameter.getType(), parameter,
                        DependencyCategory.LOCAL_VARIABLE_DEPENDENCY, Map.of("variable", parameter.getNameAsString()));
            }
            return;
        }
        if (!ctx.settings().isAnalyzeDependencies()) {
            return;
        }
        if (node instanceof CastExpr cast) {
            recordReference(ctx, ownerId, cast.getType(), cast, "cast");
        } else if (node instanceof InstanceOfExpr instanceOf && instanceOf.getPattern().isEmpty()) {
            // With a pattern, the TypePatternExpr/RecordPatternExpr node records the type.
            recordReference(ctx, ownerId, instanceOf.getType(), instanceOf, "instanceof");
        } else if (node instanceof TypePatternExpr pattern) {
            recordReference(ctx, ownerId, pattern.getType(), pattern, "pattern");
        } else if (node instanceof RecordPatternExpr pattern) {
            recordReference(ctx, ownerId, pattern.getType(), pattern, "pattern");
        } else if (node instanceof ClassExpr classExpr) {
            recordReference(ctx, ownerId, classExpr.getType(), classExpr, "classLiteral");
        } else if (node instanceof ArrayCreationExpr arrayCreation) {
            recordReference(ctx, ownerId, arrayCreation.getElementType(), arrayCreation, "arrayCreation");
        }
    }

    private void recordLocalVariable(FileAnalysisContext ctx, String ownerId, VariableDeclarator variable) {
        Map<String, String> details = Map.of("variable", variable.getNameAsString());
        if (variable.getType().isVarType()) {
            // 'var' has no syntactic type; only the solver can infer it from the initializer.
            TypeRef inferred = ctx.resolver().resolveType(variable.getType());
            if (inferred.isResolved()) {
                ctx.relate(ownerId, RelationshipType.USES_TYPE, inferred, variable,
                        DependencyCategory.LOCAL_VARIABLE_DEPENDENCY, details);
            }
            return;
        }
        recordTypes(ctx, ownerId, RelationshipType.USES_TYPE, variable.getType(), variable,
                DependencyCategory.LOCAL_VARIABLE_DEPENDENCY, details);
    }

    private void recordReference(FileAnalysisContext ctx, String ownerId, Type type, Node at, String kind) {
        recordTypes(ctx, ownerId, RelationshipType.REFERENCES, type, at,
                DependencyCategory.TYPE_REFERENCE_DEPENDENCY, Map.of("kind", kind));
    }

    private void recordTypes(FileAnalysisContext ctx, String ownerId, RelationshipType relationshipType, Type type,
                             Node at, DependencyCategory category, Map<String, String> details) {
        for (TypeRef referenced : ctx.resolver().referencedTypes(type)) {
            ctx.relate(ownerId, relationshipType, referenced, at, category, details);
        }
    }
}
