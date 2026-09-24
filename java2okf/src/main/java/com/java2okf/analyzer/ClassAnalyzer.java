package com.java2okf.analyzer;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.java2okf.model.EntityIds;
import com.java2okf.model.JavaAnnotation;
import com.java2okf.model.JavaAnnotationType;
import com.java2okf.model.JavaClass;
import com.java2okf.model.JavaEnum;
import com.java2okf.model.JavaInterface;
import com.java2okf.model.JavaParameter;
import com.java2okf.model.JavaRecord;
import com.java2okf.model.JavaType;
import com.java2okf.model.JavaTypeHeader;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.TypeKind;
import com.java2okf.model.TypeRef;
import com.java2okf.model.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Entry point of per-file structure analysis. Builds a {@link JavaType} for
 * every top-level and member type, then delegates members, inheritance, and
 * code bodies to the specialised analysers.
 *
 * <p>Local and anonymous classes are not modelled as types; their code is
 * attributed to the enclosing member so that none of their calls are lost.</p>
 */
public class ClassAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ClassAnalyzer.class);

    private final AnnotationAnalyzer annotationAnalyzer = new AnnotationAnalyzer();
    private final FieldAnalyzer fieldAnalyzer = new FieldAnalyzer(annotationAnalyzer);
    private final MethodAnalyzer methodAnalyzer = new MethodAnalyzer(annotationAnalyzer);
    private final InheritanceAnalyzer inheritanceAnalyzer = new InheritanceAnalyzer();
    private final List<BodyAnalyzer> bodyAnalyzers = List.of(new DependencyAnalyzer(), new RelationshipAnalyzer());
    private final RelationshipAnalyzer importAnalyzer = new RelationshipAnalyzer();

    /** Analyses every type in the file and records the file's imports. */
    public void analyze(FileAnalysisContext ctx) {
        List<String> topLevelIds = new ArrayList<>();
        for (TypeDeclaration<?> declaration : ctx.unit().getTypes()) {
            analyzeType(ctx, declaration, null).ifPresent(type -> topLevelIds.add(type.id()));
        }
        importAnalyzer.analyzeImports(ctx, topLevelIds);
    }

    private Optional<JavaType> analyzeType(FileAnalysisContext ctx, TypeDeclaration<?> declaration, JavaType enclosing) {
        Optional<String> qualifiedName = declaration.getFullyQualifiedName();
        if (qualifiedName.isEmpty()) {
            return Optional.empty();
        }
        TypeKind kind = kindOf(declaration);
        String id = EntityIds.typeId(kind, qualifiedName.get());
        LOG.debug("Analyzing {}: {}", kind.name().toLowerCase(), qualifiedName.get());

        List<JavaAnnotation> annotations = annotationAnalyzer.analyze(ctx, declaration, id, Map.of());
        JavaTypeHeader header = header(ctx, declaration, kind, id, qualifiedName.get(), annotations, enclosing);

        List<ClassOrInterfaceType> extendedTypes = new ArrayList<>();
        List<ClassOrInterfaceType> implementedTypes = new ArrayList<>();
        JavaType type = createType(ctx, declaration, header, extendedTypes, implementedTypes);
        ctx.result().addType(type);
        ctx.relateDeclared(EntityIds.packageId(header.packageName()), RelationshipType.CONTAINS, id,
                qualifiedName.get(), declaration);
        if (enclosing != null) {
            enclosing.addNestedTypeId(id);
            ctx.relateDeclared(enclosing.id(), RelationshipType.DECLARES, id, qualifiedName.get(), declaration);
        }

        List<CodeBlock> blocks = new ArrayList<>(fieldAnalyzer.analyze(ctx, declaration, type));
        List<AnalyzedExecutable> executables = methodAnalyzer.analyze(ctx, declaration, type);
        inheritanceAnalyzer.analyze(ctx, declaration, type, extendedTypes, implementedTypes, executables);

        for (AnalyzedExecutable executable : executables) {
            blocks.add(new CodeBlock(executable.model().id(), executable.declaration()));
        }
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof InitializerDeclaration initializer) {
                blocks.add(new CodeBlock(id, initializer.getBody()));
            }
        }
        if (declaration instanceof EnumDeclaration enumDeclaration) {
            for (EnumConstantDeclaration constant : enumDeclaration.getEntries()) {
                blocks.add(new CodeBlock(id, constant));
            }
        }
        blocks.forEach(block -> analyzeBlock(ctx, block));

        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                analyzeType(ctx, nested, type);
            }
        }
        return Optional.of(type);
    }

    /**
     * Walks one code block once, handing every node to all body analysers.
     * Member type declarations are never part of a block (see {@link #analyzeType}),
     * so nested types are not attributed to their enclosing type.
     */
    private void analyzeBlock(FileAnalysisContext ctx, CodeBlock block) {
        Node root = block.node();
        if (root instanceof CallableDeclaration<?> callable) {
            // Parameters and annotations of the declaration itself were handled by MethodAnalyzer.
            callable.getChildNodes().stream()
                    .filter(child -> child instanceof BlockStmt)
                    .forEach(body -> walk(ctx, block.ownerId(), body));
        } else if (root instanceof CompactConstructorDeclaration compact) {
            walk(ctx, block.ownerId(), compact.getBody());
        } else if (root instanceof AnnotationMemberDeclaration) {
            // Annotation members have no executable body.
        } else {
            walk(ctx, block.ownerId(), root);
        }
    }

    private void walk(FileAnalysisContext ctx, String ownerId, Node root) {
        root.walk(node -> {
            for (BodyAnalyzer analyzer : bodyAnalyzers) {
                analyzer.visit(ctx, ownerId, node);
            }
        });
    }

    private JavaTypeHeader header(FileAnalysisContext ctx, TypeDeclaration<?> declaration, TypeKind kind, String id,
                                  String qualifiedName, List<JavaAnnotation> annotations, JavaType enclosing) {
        boolean enclosedByInterface = enclosing != null
                && (enclosing.kind() == TypeKind.INTERFACE || enclosing.kind() == TypeKind.ANNOTATION);
        // Nested enums, records, interfaces, and annotations are implicitly static,
        // and so is every type declared inside an interface.
        boolean implicitlyStatic = enclosing != null && (kind != TypeKind.CLASS || enclosedByInterface);
        List<String> typeParameters = declaration instanceof NodeWithTypeParameters<?> generic
                ? generic.getTypeParameters().stream().map(Node::toString).toList()
                : List.of();
        Visibility visibility = Modifiers.visibility(declaration, enclosedByInterface);
        return new JavaTypeHeader(
                id,
                qualifiedName,
                declaration.getNameAsString(),
                ctx.packageName(),
                kind,
                ctx.location(declaration),
                visibility,
                declaration.hasModifier(Modifier.Keyword.ABSTRACT),
                declaration.hasModifier(Modifier.Keyword.FINAL),
                implicitlyStatic || declaration.isStatic(),
                declaration.hasModifier(Modifier.Keyword.SEALED),
                annotations,
                enclosing == null ? null : enclosing.id(),
                typeParameters);
    }

    private JavaType createType(FileAnalysisContext ctx, TypeDeclaration<?> declaration, JavaTypeHeader header,
                                List<ClassOrInterfaceType> extendedTypes, List<ClassOrInterfaceType> implementedTypes) {
        if (declaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
            extendedTypes.addAll(classOrInterface.getExtendedTypes());
            implementedTypes.addAll(classOrInterface.getImplementedTypes());
            if (classOrInterface.isInterface()) {
                return new JavaInterface(header, resolveAll(ctx, extendedTypes));
            }
            TypeRef superClass = extendedTypes.isEmpty() ? null : ctx.resolver().resolveType(extendedTypes.get(0));
            return new JavaClass(header, superClass, resolveAll(ctx, implementedTypes));
        }
        if (declaration instanceof EnumDeclaration enumDeclaration) {
            implementedTypes.addAll(enumDeclaration.getImplementedTypes());
            List<String> constants = enumDeclaration.getEntries().stream().map(e -> e.getNameAsString()).toList();
            return new JavaEnum(header, resolveAll(ctx, implementedTypes), constants);
        }
        if (declaration instanceof RecordDeclaration recordDeclaration) {
            implementedTypes.addAll(recordDeclaration.getImplementedTypes());
            List<JavaParameter> components = recordDeclaration.getParameters().stream()
                    .map(p -> new JavaParameter(p.getNameAsString(), ctx.resolver().resolveType(p.getType()),
                            p.isVarArgs(), false, List.of()))
                    .toList();
            return new JavaRecord(header, resolveAll(ctx, implementedTypes), components);
        }
        if (declaration instanceof AnnotationDeclaration) {
            return new JavaAnnotationType(header);
        }
        throw new IllegalStateException("Unsupported type declaration: " + declaration.getClass().getSimpleName());
    }

    private static List<TypeRef> resolveAll(FileAnalysisContext ctx, List<ClassOrInterfaceType> types) {
        return types.stream().map(t -> ctx.resolver().resolveType(t)).toList();
    }

    static TypeKind kindOf(TypeDeclaration<?> declaration) {
        if (declaration instanceof ClassOrInterfaceDeclaration c) {
            return c.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;
        }
        if (declaration instanceof EnumDeclaration) {
            return TypeKind.ENUM;
        }
        if (declaration instanceof RecordDeclaration) {
            return TypeKind.RECORD;
        }
        if (declaration instanceof AnnotationDeclaration) {
            return TypeKind.ANNOTATION;
        }
        throw new IllegalStateException("Unsupported type declaration: " + declaration.getClass().getSimpleName());
    }
}
