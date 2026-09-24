package com.java2okf.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.java2okf.model.DependencyCategory;
import com.java2okf.model.JavaType;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.TypeKind;
import com.java2okf.model.TypeRef;
import com.java2okf.resolver.MemberResolution;

import java.util.List;
import java.util.Map;

/**
 * Records {@code EXTENDS}, {@code IMPLEMENTS}, and {@code OVERRIDES_METHOD}
 * relationships.
 */
class InheritanceAnalyzer {

    void analyze(FileAnalysisContext ctx, TypeDeclaration<?> declaration, JavaType type,
                 List<ClassOrInterfaceType> extendedTypes, List<ClassOrInterfaceType> implementedTypes,
                 List<AnalyzedExecutable> executables) {
        if (!ctx.settings().isAnalyzeInheritance()) {
            return;
        }
        for (ClassOrInterfaceType extended : extendedTypes) {
            relateSuperType(ctx, type, RelationshipType.EXTENDS, extended);
        }
        // Interfaces "extend" other interfaces; classes, enums, and records "implement" them.
        RelationshipType interfaceRelation = type.kind() == TypeKind.INTERFACE
                ? RelationshipType.EXTENDS : RelationshipType.IMPLEMENTS;
        for (ClassOrInterfaceType implemented : implementedTypes) {
            relateSuperType(ctx, type, interfaceRelation, implemented);
        }
        for (AnalyzedExecutable executable : executables) {
            if (executable.declaration() instanceof MethodDeclaration method) {
                analyzeOverrides(ctx, declaration, method, executable);
            }
        }
    }

    private void relateSuperType(FileAnalysisContext ctx, JavaType type, RelationshipType relation,
                                 ClassOrInterfaceType superType) {
        TypeRef resolved = ctx.resolver().resolveType(superType);
        ctx.relate(type.id(), relation, resolved, superType, DependencyCategory.INHERITANCE_DEPENDENCY, Map.of());
        if (!resolved.isResolved()) {
            ctx.resolutionIssue(superType, "Unable to resolve supertype " + superType.asString() + " of " + type.qualifiedName());
        }
        // Generic arguments of a supertype (implements Repository<Order>) are dependencies too.
        if (ctx.settings().isAnalyzeDependencies()) {
            for (TypeRef argument : ctx.resolver().referencedTypes(superType)) {
                if (!sameTarget(argument, resolved)) {
                    ctx.relate(type.id(), RelationshipType.USES_TYPE, argument, superType,
                            DependencyCategory.INHERITANCE_DEPENDENCY, Map.of("typeArgumentOf", superType.getNameWithScope()));
                }
            }
        }
    }

    private static boolean sameTarget(TypeRef a, TypeRef b) {
        return a.targetId() != null ? a.targetId().equals(b.targetId()) : a.displayName().equals(b.displayName());
    }

    private void analyzeOverrides(FileAnalysisContext ctx, TypeDeclaration<?> declaration, MethodDeclaration method,
                                  AnalyzedExecutable executable) {
        if (method.isStatic() || method.isPrivate()) {
            return;
        }
        boolean annotatedOverride = method.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals("Override") || a.getNameAsString().equals("java.lang.Override"));
        List<MemberResolution> overridden = ctx.resolver().overriddenMethods(declaration, method,
                executable.signature().erasedParameterTypes());
        boolean foundAny = false;
        for (MemberResolution target : overridden) {
            if (target.isResolved()) {
                foundAny = true;
                ctx.relate(executable.model().id(), RelationshipType.OVERRIDES_METHOD, target, method, null, Map.of());
            }
        }
        // @Override proves that *some* supertype method is overridden. If it could not be
        // identified, keep the fact explicitly unresolved rather than dropping it.
        if (!foundAny && annotatedOverride) {
            String reason = overridden.stream().map(MemberResolution::reason).filter(r -> r != null).findFirst()
                    .orElse("overridden method not found in resolvable supertypes");
            ctx.relate(executable.model().id(), RelationshipType.OVERRIDES_METHOD,
                    MemberResolution.unresolved(executable.model().signature(), reason), method, null, Map.of());
            ctx.resolutionIssue(method, "Unable to resolve method overridden by " + executable.model().signature());
        }
    }
}
