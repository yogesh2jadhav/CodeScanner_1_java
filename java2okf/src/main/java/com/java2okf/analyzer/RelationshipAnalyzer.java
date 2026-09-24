package com.java2okf.analyzer;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.java2okf.model.DependencyCategory;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;
import com.java2okf.model.TypeRef;
import com.java2okf.resolver.MemberResolution;

import java.util.List;
import java.util.Map;

/**
 * Records behavioural relationships found in executable code: method and
 * constructor calls ({@code CALLS}), object creation ({@code INSTANTIATES}),
 * method references and field accesses ({@code REFERENCES}); and file-level
 * {@code IMPORTS}.
 */
class RelationshipAnalyzer implements BodyAnalyzer {

    @Override
    public void visit(FileAnalysisContext ctx, String ownerId, Node node) {
        if (node instanceof MethodCallExpr call) {
            if (ctx.settings().isAnalyzeMethodCalls()) {
                recordCall(ctx, ownerId, call);
            }
        } else if (node instanceof ObjectCreationExpr creation) {
            recordCreation(ctx, ownerId, creation);
        } else if (node instanceof ExplicitConstructorInvocationStmt invocation) {
            if (ctx.settings().isAnalyzeMethodCalls() && ctx.settings().isAnalyzeConstructors()) {
                MemberResolution target = ctx.resolver().resolveConstructor(invocation);
                ctx.relate(ownerId, RelationshipType.CALLS, target, invocation, DependencyCategory.CALL_DEPENDENCY,
                        Map.of("kind", invocation.isThis() ? "this" : "super"));
                if (!target.isResolved()) {
                    ctx.resolutionIssue(invocation, "Unable to resolve constructor call: " + target.targetName());
                }
            }
        } else if (node instanceof MethodReferenceExpr reference) {
            if (ctx.settings().isAnalyzeMethodCalls()) {
                recordMethodReference(ctx, ownerId, reference);
            }
        } else if (node instanceof NameExpr || node instanceof FieldAccessExpr) {
            if (ctx.settings().isAnalyzeFields()) {
                ctx.resolver().resolveFieldAccess((Expression) node)
                        .ifPresent(field -> ctx.relate(ownerId, RelationshipType.REFERENCES, field, node,
                                DependencyCategory.TYPE_REFERENCE_DEPENDENCY, Map.of("kind", "field")));
            }
        }
    }

    private void recordCall(FileAnalysisContext ctx, String ownerId, MethodCallExpr call) {
        // JavaParser identifies the invocation syntactically. The symbol solver is
        // required to find the declaring type and to choose among overloads; when
        // it cannot, the call is kept as UNRESOLVED with its source text.
        MemberResolution target = ctx.resolver().resolveMethodCall(call);
        ctx.relate(ownerId, RelationshipType.CALLS, target, call, DependencyCategory.CALL_DEPENDENCY, Map.of());
        if (!target.isResolved()) {
            String prefix = target.status() == ResolutionStatus.AMBIGUOUS
                    ? "Ambiguous method call: " : "Unable to resolve method call: ";
            ctx.resolutionIssue(call, prefix + target.targetName()
                    + (target.reason() == null ? "" : " (" + target.reason() + ")"));
        }
    }

    private void recordCreation(FileAnalysisContext ctx, String ownerId, ObjectCreationExpr creation) {
        if (ctx.settings().isAnalyzeObjectCreation()) {
            TypeRef type = ctx.resolver().resolveType(creation.getType());
            Map<String, String> details = creation.getAnonymousClassBody().isPresent()
                    ? Map.of("anonymousClass", "true") : Map.of();
            ctx.relate(ownerId, RelationshipType.INSTANTIATES, type, creation, DependencyCategory.CALL_DEPENDENCY, details);
            if (!type.isResolved()) {
                ctx.resolutionIssue(creation, "Unable to resolve instantiated type: " + creation.getType().asString());
            }
        }
        // The constructor itself is recorded as a call only when it is proven; an
        // unresolved 'new' is already represented by the INSTANTIATES relationship.
        if (ctx.settings().isAnalyzeMethodCalls() && ctx.settings().isAnalyzeConstructors()) {
            MemberResolution constructor = ctx.resolver().resolveConstructor(creation);
            if (constructor.isResolved()) {
                ctx.relate(ownerId, RelationshipType.CALLS, constructor, creation, DependencyCategory.CALL_DEPENDENCY,
                        Map.of("kind", "new"));
            }
        }
    }

    private void recordMethodReference(FileAnalysisContext ctx, String ownerId, MethodReferenceExpr reference) {
        if (reference.getIdentifier().equals("new")) {
            // Foo::new creates Foo instances when invoked.
            if (ctx.settings().isAnalyzeObjectCreation() && reference.getScope() instanceof TypeExpr typeExpr) {
                ctx.relate(ownerId, RelationshipType.INSTANTIATES, ctx.resolver().resolveType(typeExpr.getType()),
                        reference, DependencyCategory.CALL_DEPENDENCY, Map.of("kind", "constructorReference"));
            }
            return;
        }
        MemberResolution target = ctx.resolver().resolveMethodReference(reference);
        ctx.relate(ownerId, RelationshipType.REFERENCES, target, reference,
                DependencyCategory.TYPE_REFERENCE_DEPENDENCY, Map.of("kind", "methodReference"));
    }

    /** Records the imports of the file for each of its top-level types. */
    void analyzeImports(FileAnalysisContext ctx, List<String> topLevelTypeIds) {
        if (!ctx.settings().isAnalyzeDependencies() || topLevelTypeIds.isEmpty()) {
            return;
        }
        for (ImportDeclaration importDeclaration : ctx.unit().getImports()) {
            TypeRef target = ctx.resolver().resolveImport(importDeclaration);
            Map<String, String> details = importDeclaration.isStatic() ? Map.of("static", "true") : Map.of();
            for (String typeId : topLevelTypeIds) {
                // Imports are not dependencies on their own (they may be unused), so no category.
                ctx.relate(typeId, RelationshipType.IMPORTS, target, importDeclaration, null, details);
            }
        }
    }
}
