package com.java2okf.okf;

import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.graph.TypeDependency;
import com.java2okf.model.JavaClass;
import com.java2okf.model.JavaEnum;
import com.java2okf.model.JavaExecutable;
import com.java2okf.model.JavaField;
import com.java2okf.model.JavaMethod;
import com.java2okf.model.JavaRecord;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.JavaType;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.TypeKind;
import com.java2okf.model.TypeRef;
import com.java2okf.model.Visibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.java2okf.okf.MarkdownBuilder.code;

/**
 * Generates documents for classes, interfaces, enums, records, and annotation
 * types. Class documents give the overview (structure, inheritance,
 * dependencies, callers); per-method detail lives in method documents.
 */
public class ClassDocumentGenerator {

    public OkfDocument generate(GenerationContext ctx, JavaType type) {
        String path = ctx.layout().pathOf(type.id()).orElseThrow();
        LinkRenderer links = ctx.links(path);
        KnowledgeGraph graph = ctx.graph();
        MarkdownBuilder md = new MarkdownBuilder();

        md.heading(1, type.simpleName());
        md.heading(2, "Source");
        md.paragraph(code(type.location().file()) + " (lines " + type.location().lineStart() + "–" + type.location().lineEnd() + ")");
        md.heading(2, "Package");
        md.paragraph(links.linkTo(ctx.project().packages().get(type.packageName()).id(),
                ctx.project().packages().get(type.packageName()).displayName()).orElseThrow());
        md.heading(2, "Declaration");
        md.codeBlock("java", declaration(type));

        if (type.header().enclosingTypeId() != null) {
            md.bulletSection("Enclosing Type", List.of(links.entity(type.header().enclosingTypeId())));
        }
        md.bulletSection("Annotations", targets(links, graph.outgoing(type.id(), RelationshipType.ANNOTATED_WITH)));

        boolean isInterface = type.kind() == TypeKind.INTERFACE;
        md.bulletSection(isInterface ? "Extends" : "Inheritance",
                graph.outgoing(type.id(), RelationshipType.EXTENDS).stream()
                        .map(r -> (isInterface ? "" : "Extends ") + links.target(r)).toList());
        md.bulletSection("Implements", targets(links, graph.outgoing(type.id(), RelationshipType.IMPLEMENTS)));
        md.bulletSection(isInterface ? "Extended / Implemented By" : "Subtypes", sources(links,
                concat(graph.incoming(type.id(), RelationshipType.EXTENDS), graph.incoming(type.id(), RelationshipType.IMPLEMENTS))));

        if (type instanceof JavaEnum javaEnum) {
            md.bulletSection("Enum Constants", javaEnum.constants().stream().map(MarkdownBuilder::code).toList());
        }
        if (type instanceof JavaRecord record) {
            md.bulletSection("Record Components", record.components().stream()
                    .map(c -> code(c.name()) + " — " + links.type(c.type())).toList());
        }

        md.bulletSection("Fields", type.fields().stream().map(f -> field(ctx, links, f)).toList());
        executables(ctx, links, md, "Constructors", type.constructors());
        executables(ctx, links, md, "Methods", type.methods());
        md.bulletSection("Nested Types", type.nestedTypeIds().stream().map(links::entity).toList());

        // Code outside methods (field initialisers, initializer blocks, enum constant bodies).
        md.bulletSection("Initializer Calls", withLines(links, graph.outgoing(type.id(), RelationshipType.CALLS)));
        md.bulletSection("Initializer Instantiations", withLines(links, graph.outgoing(type.id(), RelationshipType.INSTANTIATES)));

        md.bulletSection("Imports", targets(links, graph.outgoing(type.id(), RelationshipType.IMPORTS)));
        md.bulletSection("Dependencies", graph.dependenciesOf(type.id()).stream()
                .sorted(Comparator.comparing((TypeDependency d) -> !d.internal()).thenComparing(TypeDependency::qualifiedName))
                .map(d -> dependency(links, d)).toList());
        md.bulletSection("Used By", graph.dependentsOf(type.id()).stream().map(links::entity).toList());
        md.bulletSection("Called By", calledBy(links, graph.callsIntoType(type.id())));

        md.heading(2, "Analysis Notes");
        md.paragraph("This document contains facts extracted through static analysis. "
                + "Unresolved relationships are explicitly marked as `" + OkfMarkers.UNRESOLVED + "`; "
                + "`" + OkfMarkers.EXTERNAL + "` marks resolved targets outside the analysed sources.");

        OkfFrontmatter frontmatter = OkfFrontmatter.of(OkfDocumentType.forKind(type.kind()), type.id(), type.simpleName())
                .resource(type.location().file())
                .tags(tags(type))
                .provenance(ctx.provenance())
                .java("qualifiedName", type.qualifiedName())
                .java("package", type.packageName())
                .java("kind", type.kind().name().toLowerCase(Locale.ROOT))
                .java("visibility", type.header().visibility().label())
                .java("modifiers", modifiers(type))
                .java("typeParameters", type.header().typeParameters())
                .java("lines", type.location().lineStart() + "-" + type.location().lineEnd());
        return new OkfDocument(path, frontmatter.build(), md.build());
    }

    private static List<String> tags(JavaType type) {
        List<String> tags = new ArrayList<>(List.of("java", type.kind().name().toLowerCase(Locale.ROOT)));
        if (!type.packageName().isEmpty()) {
            tags.add(type.packageName());
        }
        return tags;
    }

    private static List<String> modifiers(JavaType type) {
        List<String> modifiers = new ArrayList<>();
        if (type.header().isAbstract()) {
            modifiers.add("abstract");
        }
        if (type.header().isStatic()) {
            modifiers.add("static");
        }
        if (type.header().isFinal()) {
            modifiers.add("final");
        }
        if (type.header().isSealed()) {
            modifiers.add("sealed");
        }
        return modifiers;
    }

    /** Reconstructs the declaration head from the model, e.g. {@code public class A<T> extends B implements C}. */
    static String declaration(JavaType type) {
        StringBuilder out = new StringBuilder();
        if (type.header().visibility() != Visibility.PACKAGE_PRIVATE) {
            out.append(type.header().visibility().label()).append(' ');
        }
        modifiers(type).forEach(m -> out.append(m).append(' '));
        out.append(switch (type.kind()) {
            case CLASS -> "class";
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case RECORD -> "record";
            case ANNOTATION -> "@interface";
        }).append(' ').append(type.simpleName());
        if (!type.header().typeParameters().isEmpty()) {
            out.append('<').append(String.join(", ", type.header().typeParameters())).append('>');
        }
        if (type instanceof JavaRecord record) {
            out.append('(').append(record.components().stream()
                    .map(c -> c.type().displayName() + " " + c.name()).collect(Collectors.joining(", "))).append(')');
        }
        if (type instanceof JavaClass javaClass && javaClass.superClass() != null) {
            out.append(" extends ").append(javaClass.superClass().displayName());
        }
        if (!type.interfaces().isEmpty()) {
            out.append(type.kind() == TypeKind.INTERFACE ? " extends " : " implements ")
                    .append(type.interfaces().stream().map(TypeRef::displayName).collect(Collectors.joining(", ")));
        }
        return out.toString();
    }

    private String field(GenerationContext ctx, LinkRenderer links, JavaField field) {
        StringBuilder out = new StringBuilder(code(field.name())).append(" — ").append(links.type(field.type()));
        // Generic arguments such as List<Order> are dependencies worth linking.
        List<String> arguments = ctx.graph().outgoing(field.id(), RelationshipType.USES_TYPE).stream()
                .filter(r -> field.type().targetId() == null || !field.type().targetId().equals(r.targetId()))
                .filter(r -> !(r.targetId() == null && field.type().displayName().equals(r.targetName())))
                .map(links::target)
                .toList();
        if (!arguments.isEmpty()) {
            out.append(" (uses ").append(String.join(", ", arguments)).append(')');
        }
        List<String> modifiers = new ArrayList<>();
        modifiers.add(field.visibility().label());
        if (field.isStatic()) {
            modifiers.add("static");
        }
        if (field.isFinal()) {
            modifiers.add("final");
        }
        if (field.isVolatile()) {
            modifiers.add("volatile");
        }
        if (field.isTransient()) {
            modifiers.add("transient");
        }
        out.append(" · ").append(String.join(" ", modifiers));
        return out.toString();
    }

    private void executables(GenerationContext ctx, LinkRenderer links, MarkdownBuilder md, String heading,
                             List<? extends JavaExecutable> executables) {
        if (executables.isEmpty()) {
            return;
        }
        md.heading(2, heading);
        for (JavaExecutable executable : executables) {
            String text = executable.name() + "(" + executable.parameters().stream()
                    .map(p -> p.type().displayName()).collect(Collectors.joining(", ")) + ")";
            String line = links.linkTo(executable.id(), text).orElse(code(text));
            if (executable instanceof JavaMethod method) {
                line += " → " + code(method.returnType().displayName());
            }
            md.bullet(line);
            if (!ctx.settings().isGenerateMethodDocuments()) {
                // Without method documents the class document carries the method relationships.
                summarize(ctx, links, md, executable);
            }
        }
        md.endList();
    }

    private void summarize(GenerationContext ctx, LinkRenderer links, MarkdownBuilder md, JavaExecutable executable) {
        KnowledgeGraph graph = ctx.graph();
        List<String> calls = targets(links, graph.outgoing(executable.id(), RelationshipType.CALLS));
        List<String> callers = sources(links, graph.incoming(executable.id(), RelationshipType.CALLS));
        if (!calls.isEmpty()) {
            md.subBullet("Calls: " + String.join(", ", calls));
        }
        if (!callers.isEmpty()) {
            md.subBullet("Called by: " + String.join(", ", callers));
        }
    }

    private String dependency(LinkRenderer links, TypeDependency dependency) {
        String target = links.linkTo(dependency.targetTypeId(), DocumentLayout.label(dependency.targetTypeId()))
                .orElse(code(dependency.qualifiedName()) + " " + (dependency.internal() ? OkfMarkers.NO_DOCUMENT : OkfMarkers.EXTERNAL));
        return target + " — " + dependency.categories().stream().map(RelationshipLabels::category).collect(Collectors.joining(", "));
    }

    private List<String> calledBy(LinkRenderer links, List<JavaRelationship> calls) {
        Set<String> lines = new LinkedHashSet<>();
        for (JavaRelationship call : calls) {
            lines.add(links.entity(call.sourceId()) + " → " + code(DocumentLayout.label(call.targetId())));
        }
        return new ArrayList<>(lines);
    }

    static List<String> targets(LinkRenderer links, List<JavaRelationship> relationships) {
        return links.displayOrder(relationships).stream().map(links::target).distinct().toList();
    }

    static List<String> withLines(LinkRenderer links, List<JavaRelationship> relationships) {
        return links.displayOrder(relationships).stream().map(r -> links.target(r) + LinkRenderer.lines(r)).distinct().toList();
    }

    static List<String> sources(LinkRenderer links, List<JavaRelationship> relationships) {
        return relationships.stream().map(JavaRelationship::sourceId).distinct().sorted().map(links::entity).toList();
    }

    private static List<JavaRelationship> concat(List<JavaRelationship> a, List<JavaRelationship> b) {
        List<JavaRelationship> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
