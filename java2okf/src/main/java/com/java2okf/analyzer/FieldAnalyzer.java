package com.java2okf.analyzer;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.Type;
import com.java2okf.model.DependencyCategory;
import com.java2okf.model.EntityIds;
import com.java2okf.model.JavaAnnotation;
import com.java2okf.model.JavaField;
import com.java2okf.model.JavaType;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.TypeKind;
import com.java2okf.model.TypeRef;
import com.java2okf.model.Visibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts fields (including record components) and their type dependencies.
 */
class FieldAnalyzer {

    private final AnnotationAnalyzer annotationAnalyzer;

    FieldAnalyzer(AnnotationAnalyzer annotationAnalyzer) {
        this.annotationAnalyzer = annotationAnalyzer;
    }

    /**
     * Adds the fields of {@code declaration} to {@code type}.
     *
     * @return field initialisers, attributed to the declaring type, for body analysis
     */
    List<CodeBlock> analyze(FileAnalysisContext ctx, TypeDeclaration<?> declaration, JavaType type) {
        List<CodeBlock> initializers = new ArrayList<>();
        if (!ctx.settings().isAnalyzeFields()) {
            return initializers;
        }
        if (declaration instanceof RecordDeclaration record) {
            record.getParameters().forEach(component -> analyzeRecordComponent(ctx, component, type));
        }
        boolean interfaceMember = type.kind() == TypeKind.INTERFACE || type.kind() == TypeKind.ANNOTATION;
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (!(member instanceof FieldDeclaration field)) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                analyzeVariable(ctx, field, variable, type, interfaceMember);
                variable.getInitializer().ifPresent(init -> initializers.add(new CodeBlock(type.id(), init)));
            }
        }
        return initializers;
    }

    private void analyzeVariable(FileAnalysisContext ctx, FieldDeclaration field, VariableDeclarator variable,
                                 JavaType type, boolean interfaceMember) {
        String id = EntityIds.fieldId(type.qualifiedName(), variable.getNameAsString());
        List<JavaAnnotation> annotations = annotationAnalyzer.analyze(ctx, field, id, Map.of());
        // Interface fields are implicitly public static final.
        JavaField javaField = new JavaField(
                id,
                type.id(),
                variable.getNameAsString(),
                ctx.resolver().resolveType(variable.getType()),
                Modifiers.visibility(field, interfaceMember),
                interfaceMember || field.isStatic(),
                interfaceMember || field.isFinal(),
                field.hasModifier(Modifier.Keyword.VOLATILE),
                field.hasModifier(Modifier.Keyword.TRANSIENT),
                annotations,
                ctx.location(variable));
        type.addField(javaField);
        ctx.relateDeclared(type.id(), RelationshipType.HAS_FIELD, id, type.qualifiedName() + "." + javaField.name(), variable);
        recordTypeDependencies(ctx, id, variable.getType(), variable);
    }

    private void analyzeRecordComponent(FileAnalysisContext ctx, Parameter component, JavaType type) {
        String id = EntityIds.fieldId(type.qualifiedName(), component.getNameAsString());
        List<JavaAnnotation> annotations = annotationAnalyzer.analyze(ctx, component, id, Map.of());
        TypeRef fieldType = ctx.resolver().resolveType(component.getType());
        // The compiler generates a private final field for every record component.
        type.addField(new JavaField(id, type.id(), component.getNameAsString(), fieldType, Visibility.PRIVATE,
                false, true, false, false, annotations, ctx.location(component)));
        ctx.relateDeclared(type.id(), RelationshipType.HAS_FIELD, id, type.qualifiedName() + "." + component.getNameAsString(), component);
        recordTypeDependencies(ctx, id, component.getType(), component);
    }

    private void recordTypeDependencies(FileAnalysisContext ctx, String fieldId,
                                        Type fieldType,
                                        Node at) {
        if (!ctx.settings().isAnalyzeDependencies()) {
            return;
        }
        for (TypeRef referenced : ctx.resolver().referencedTypes(fieldType)) {
            ctx.relate(fieldId, RelationshipType.USES_TYPE, referenced, at, DependencyCategory.FIELD_DEPENDENCY, Map.of());
        }
    }
}
