package com.java2okf.analyzer;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.java2okf.model.DependencyCategory;
import com.java2okf.model.EntityIds;
import com.java2okf.model.JavaAnnotation;
import com.java2okf.model.JavaConstructor;
import com.java2okf.model.JavaMethod;
import com.java2okf.model.JavaParameter;
import com.java2okf.model.JavaType;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;
import com.java2okf.model.TypeKind;
import com.java2okf.model.TypeRef;
import com.java2okf.model.Visibility;
import com.java2okf.resolver.ExecutableSignature;
import com.java2okf.resolver.SourceText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts methods, constructors, and annotation members, together with their
 * parameter, return, exception, and annotation relationships.
 */
class MethodAnalyzer {

    private final AnnotationAnalyzer annotationAnalyzer;

    MethodAnalyzer(AnnotationAnalyzer annotationAnalyzer) {
        this.annotationAnalyzer = annotationAnalyzer;
    }

    List<AnalyzedExecutable> analyze(FileAnalysisContext ctx, TypeDeclaration<?> declaration, JavaType type) {
        List<AnalyzedExecutable> result = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        boolean interfaceMember = type.kind() == TypeKind.INTERFACE || type.kind() == TypeKind.ANNOTATION;
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof MethodDeclaration method) {
                result.add(analyzeMethod(ctx, method, type, interfaceMember, usedIds));
            } else if (member instanceof AnnotationMemberDeclaration annotationMember) {
                result.add(analyzeAnnotationMember(ctx, annotationMember, type, usedIds));
            } else if (member instanceof ConstructorDeclaration constructor && ctx.settings().isAnalyzeConstructors()) {
                result.add(analyzeConstructor(ctx, constructor, constructor.getParameters(),
                        constructor.getDeclarationAsString(true, true, true), constructor, constructor.getThrownExceptions(),
                        type, usedIds));
            } else if (member instanceof CompactConstructorDeclaration compact && ctx.settings().isAnalyzeConstructors()
                    && declaration instanceof RecordDeclaration record) {
                // A compact canonical constructor implicitly takes the record components as parameters.
                String text = compact.getAccessSpecifier().asString() + " " + compact.getNameAsString()
                        + "(" + record.getParameters().stream().map(Node::toString).collect(Collectors.joining(", ")) + ")";
                result.add(analyzeConstructor(ctx, compact, record.getParameters(), text.trim(), compact,
                        compact.getThrownExceptions(), type, usedIds));
            }
        }
        return result;
    }

    private AnalyzedExecutable analyzeMethod(FileAnalysisContext ctx, MethodDeclaration method, JavaType type,
                                             boolean interfaceMember, Set<String> usedIds) {
        ExecutableSignature signature = ctx.resolver().signatureOf(method.getParameters());
        String id = uniqueId(ctx, method, EntityIds.methodId(type.qualifiedName(),
                EntityIds.signature(method.getNameAsString(), signature.erasedParameterTypes())), usedIds);

        boolean isStatic = method.isStatic();
        boolean isDefault = method.isDefault();
        boolean isPrivate = method.isPrivate();
        // Interface methods without a body are implicitly abstract.
        boolean isAbstract = method.isAbstract()
                || (interfaceMember && method.getBody().isEmpty() && !isStatic && !isDefault && !isPrivate);

        List<JavaAnnotation> annotations = annotationAnalyzer.analyze(ctx, method, id, Map.of());
        List<JavaParameter> parameters = analyzeParameters(ctx, id, method.getParameters());
        List<TypeRef> thrown = analyzeThrows(ctx, id, method.getThrownExceptions());
        TypeRef returnType = ctx.resolver().resolveType(method.getType());
        recordTypeRelationships(ctx, id, RelationshipType.RETURNS_TYPE, method.getType(), method.getType(),
                DependencyCategory.RETURN_TYPE_DEPENDENCY, Map.of());

        JavaMethod model = new JavaMethod(
                id, type.id(), method.getNameAsString(),
                EntityIds.signature(method.getNameAsString(), signature.erasedParameterTypes()),
                SourceText.singleLine(method.getDeclarationAsString(true, true, true)),
                returnType,
                Modifiers.visibility(method, interfaceMember),
                isStatic, method.isFinal(), isAbstract, method.isSynchronized(), isDefault,
                method.getTypeParameters().stream().map(Node::toString).toList(),
                parameters, thrown, annotations, ctx.location(method), signature.status());
        type.addMethod(model);
        ctx.relateDeclared(type.id(), RelationshipType.DECLARES, id, EntityIds.localPart(id), method);
        return new AnalyzedExecutable(method, model, signature);
    }

    private AnalyzedExecutable analyzeAnnotationMember(FileAnalysisContext ctx, AnnotationMemberDeclaration member,
                                                       JavaType type, Set<String> usedIds) {
        ExecutableSignature signature = new ExecutableSignature(List.of(), ResolutionStatus.RESOLVED);
        String id = uniqueId(ctx, member, EntityIds.methodId(type.qualifiedName(),
                EntityIds.signature(member.getNameAsString(), List.of())), usedIds);
        List<JavaAnnotation> annotations = annotationAnalyzer.analyze(ctx, member, id, Map.of());
        TypeRef returnType = ctx.resolver().resolveType(member.getType());
        recordTypeRelationships(ctx, id, RelationshipType.RETURNS_TYPE, member.getType(), member.getType(),
                DependencyCategory.RETURN_TYPE_DEPENDENCY, Map.of());
        String declaration = member.getType().asString() + " " + member.getNameAsString() + "()"
                + member.getDefaultValue().map(v -> " default " + SourceText.singleLine(v.toString())).orElse("");
        JavaMethod model = new JavaMethod(id, type.id(), member.getNameAsString(),
                EntityIds.signature(member.getNameAsString(), List.of()), declaration, returnType,
                Visibility.PUBLIC, false, false, true, false, false, List.of(), List.of(), List.of(), annotations,
                ctx.location(member), ResolutionStatus.RESOLVED);
        type.addMethod(model);
        ctx.relateDeclared(type.id(), RelationshipType.DECLARES, id, EntityIds.localPart(id), member);
        return new AnalyzedExecutable(member, model, signature);
    }

    private AnalyzedExecutable analyzeConstructor(FileAnalysisContext ctx, BodyDeclaration<?> declaration,
                                                  NodeList<Parameter> parameterNodes, String text,
                                                  NodeWithModifiers<?> modifiers,
                                                  NodeList<ReferenceType> thrownNodes, JavaType type,
                                                  Set<String> usedIds) {
        ExecutableSignature signature = ctx.resolver().signatureOf(parameterNodes);
        String id = uniqueId(ctx, declaration, EntityIds.constructorId(type.qualifiedName(),
                signature.erasedParameterTypes()), usedIds);
        List<JavaAnnotation> annotations = annotationAnalyzer.analyze(ctx,
                (NodeWithAnnotations<?>) declaration, id, Map.of());
        List<JavaParameter> parameters = analyzeParameters(ctx, id, parameterNodes);
        List<TypeRef> thrown = analyzeThrows(ctx, id, thrownNodes);
        // Enum constructors are implicitly private.
        Visibility visibility = type.kind() == TypeKind.ENUM ? Visibility.PRIVATE : Modifiers.visibility(modifiers, false);
        JavaConstructor model = new JavaConstructor(id, type.id(), type.simpleName(),
                EntityIds.signature(type.simpleName(), signature.erasedParameterTypes()),
                SourceText.singleLine(text), visibility, parameters, thrown, annotations,
                ctx.location(declaration), signature.status());
        type.addConstructor(model);
        ctx.relateDeclared(type.id(), RelationshipType.DECLARES, id, EntityIds.localPart(id), declaration);
        return new AnalyzedExecutable(declaration, model, signature);
    }

    private List<JavaParameter> analyzeParameters(FileAnalysisContext ctx, String ownerId, List<Parameter> nodes) {
        List<JavaParameter> parameters = new ArrayList<>();
        for (Parameter parameter : nodes) {
            Map<String, String> details = Map.of("parameter", parameter.getNameAsString());
            List<JavaAnnotation> annotations = annotationAnalyzer.analyze(ctx, parameter, ownerId, details);
            TypeRef resolved = ctx.resolver().resolveType(parameter.getType());
            TypeRef type = parameter.isVarArgs()
                    ? new TypeRef(resolved.displayName() + "...", resolved.qualifiedName(), resolved.targetId(), resolved.status())
                    : resolved;
            parameters.add(new JavaParameter(parameter.getNameAsString(), type, parameter.isVarArgs(),
                    parameter.hasModifier(Modifier.Keyword.FINAL), annotations));
            recordTypeRelationships(ctx, ownerId, RelationshipType.HAS_PARAMETER, parameter.getType(), parameter,
                    DependencyCategory.PARAMETER_DEPENDENCY, details);
        }
        return parameters;
    }

    private List<TypeRef> analyzeThrows(FileAnalysisContext ctx, String ownerId, List<ReferenceType> thrownNodes) {
        List<TypeRef> thrown = new ArrayList<>();
        for (ReferenceType exception : thrownNodes) {
            thrown.add(ctx.resolver().resolveType(exception));
            recordTypeRelationships(ctx, ownerId, RelationshipType.THROWS, exception, exception,
                    DependencyCategory.EXCEPTION_DEPENDENCY, Map.of());
        }
        return thrown;
    }

    /** Records one relationship per class type mentioned in {@code type} (generic arguments included). */
    private void recordTypeRelationships(FileAnalysisContext ctx, String ownerId, RelationshipType relationshipType,
                                         Type type, Node at, DependencyCategory category, Map<String, String> details) {
        if (!ctx.settings().isAnalyzeDependencies()) {
            return;
        }
        for (TypeRef referenced : ctx.resolver().referencedTypes(type)) {
            ctx.relate(ownerId, relationshipType, referenced, at, category, details);
        }
    }

    /**
     * Guarantees unique IDs within a type. Collisions can only occur when
     * unresolved parameter types have identical simple names (e.g. {@code a.Foo}
     * and {@code b.Foo}); the source line then disambiguates deterministically.
     */
    private String uniqueId(FileAnalysisContext ctx, Node declaration, String id, Set<String> usedIds) {
        if (usedIds.add(id)) {
            return id;
        }
        String disambiguated = id + "@L" + ctx.location(declaration).lineStart();
        ctx.analysisIssue(declaration, "Duplicate signature " + EntityIds.localPart(id)
                + " (unresolved parameter types); using ID " + disambiguated);
        usedIds.add(disambiguated);
        return disambiguated;
    }
}
