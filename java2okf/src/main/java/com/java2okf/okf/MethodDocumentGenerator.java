package com.java2okf.okf;

import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.model.JavaConstructor;
import com.java2okf.model.JavaExecutable;
import com.java2okf.model.JavaMethod;
import com.java2okf.model.JavaParameter;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.JavaType;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.java2okf.okf.MarkdownBuilder.code;

/**
 * Generates one document per method and constructor with the exact signature,
 * source location, and every method-level relationship in both directions.
 */
public class MethodDocumentGenerator {

    private static final List<RelationshipType> USAGE_TYPES = List.of(
            RelationshipType.HAS_PARAMETER, RelationshipType.RETURNS_TYPE, RelationshipType.THROWS,
            RelationshipType.USES_TYPE, RelationshipType.REFERENCES);

    public OkfDocument generate(GenerationContext ctx, JavaType type, JavaExecutable executable) {
        String path = ctx.layout().pathOf(executable.id()).orElseThrow();
        LinkRenderer links = ctx.links(path);
        KnowledgeGraph graph = ctx.graph();
        String id = executable.id();
        boolean constructor = executable instanceof JavaConstructor;
        MarkdownBuilder md = new MarkdownBuilder();

        md.heading(1, executable.name());
        md.heading(2, "Declared By");
        md.paragraph(links.entity(type.id()));
        md.heading(2, "Signature");
        md.codeBlock("java", executable.declaration());
        md.heading(2, "Source");
        md.paragraph(code(executable.location().shortLabel()));

        md.bulletSection("Annotations", ClassDocumentGenerator.targets(links,
                graph.outgoing(id, RelationshipType.ANNOTATED_WITH).stream()
                        .filter(r -> !r.details().containsKey("parameter")).toList()));

        if (!executable.parameters().isEmpty()) {
            md.heading(2, "Parameters");
            List<List<String>> rows = new ArrayList<>();
            for (JavaParameter parameter : executable.parameters()) {
                rows.add(List.of(code(parameter.name()), links.type(parameter.type())));
            }
            md.table(List.of("Name", "Type"), rows);
        }
        if (executable instanceof JavaMethod method) {
            md.heading(2, "Returns");
            md.paragraph(links.type(method.returnType()));
        }
        md.bulletSection("Throws", executable.throwsTypes().stream().map(links::type).toList());

        md.bulletSection("Overrides", ClassDocumentGenerator.targets(links, graph.outgoing(id, RelationshipType.OVERRIDES_METHOD)));
        md.bulletSection("Overridden By", ClassDocumentGenerator.sources(links, graph.incoming(id, RelationshipType.OVERRIDES_METHOD)));

        List<JavaRelationship> calls = graph.outgoing(id, RelationshipType.CALLS);
        md.bulletSection("Calls", ClassDocumentGenerator.withLines(links, calls));
        md.bulletSection("Called By", callers(links, graph.incoming(id, RelationshipType.CALLS)));
        md.bulletSection("Uses", usages(links, graph, id));
        md.bulletSection("Instantiates", ClassDocumentGenerator.withLines(links, graph.outgoing(id, RelationshipType.INSTANTIATES)));
        md.bulletSection("References", ClassDocumentGenerator.withLines(links, graph.outgoing(id, RelationshipType.REFERENCES).stream()
                .filter(MethodDocumentGenerator::isMemberReference).toList()));
        md.bulletSection("Referenced By", ClassDocumentGenerator.sources(links, graph.incoming(id, RelationshipType.REFERENCES)));

        md.heading(2, "Resolution Status");
        md.paragraph(resolutionSummary(graph, executable));

        String kind = constructor ? "constructor" : "method";
        OkfFrontmatter frontmatter = OkfFrontmatter.of(
                        constructor ? OkfDocumentType.JAVA_CONSTRUCTOR : OkfDocumentType.JAVA_METHOD, id, executable.name())
                .resource(executable.location().file())
                .tags(type.packageName().isEmpty() ? List.of("java", kind) : List.of("java", kind, type.packageName()))
                .provenance(ctx.provenance())
                .java("declaringClass", type.qualifiedName())
                .java("signature", executable.signature())
                .java("returnType", executable instanceof JavaMethod m ? m.returnType().displayName() : null)
                .java("visibility", executable.visibility().label())
                .java("modifiers", modifiers(executable))
                .java("lines", executable.location().lineStart() + "-" + executable.location().lineEnd())
                .java("signatureStatus", executable.signatureStatus() == ResolutionStatus.RESOLVED
                        ? null : executable.signatureStatus().name());
        return new OkfDocument(path, frontmatter.build(), md.build());
    }

    private static boolean isMemberReference(JavaRelationship relationship) {
        String kind = relationship.details().get("kind");
        return "field".equals(kind) || "methodReference".equals(kind);
    }

    private List<String> callers(LinkRenderer links, List<JavaRelationship> incoming) {
        return incoming.stream()
                .map(r -> links.entity(r.sourceId()) + LinkRenderer.lines(r).replace(" — line", " — at line"))
                .distinct()
                .toList();
    }

    /** One line per used type, listing every way the method uses it. */
    private List<String> usages(LinkRenderer links, KnowledgeGraph graph, String id) {
        Map<String, Set<String>> byTarget = new LinkedHashMap<>();
        Map<String, String> rendered = new LinkedHashMap<>();
        List<JavaRelationship> relevant = links.displayOrder(graph.outgoing(id).stream()
                .filter(r -> USAGE_TYPES.contains(r.type()))
                .filter(r -> !isMemberReference(r))
                .toList());
        for (JavaRelationship relationship : relevant) {
            String key = relationship.targetKey();
            rendered.putIfAbsent(key, links.target(relationship));
            byTarget.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(RelationshipLabels.usage(relationship));
        }
        return byTarget.entrySet().stream()
                .map(e -> rendered.get(e.getKey()) + " — " + String.join(", ", e.getValue()))
                .toList();
    }

    private String resolutionSummary(KnowledgeGraph graph, JavaExecutable executable) {
        List<JavaRelationship> outgoing = graph.outgoing(executable.id());
        long unresolved = outgoing.stream().filter(r -> r.status() == ResolutionStatus.UNRESOLVED).count();
        long ambiguous = outgoing.stream().filter(r -> r.status() == ResolutionStatus.AMBIGUOUS).count();
        StringBuilder out = new StringBuilder("All statically resolvable relationships are recorded. ");
        out.append(outgoing.size()).append(" outgoing relationships: ")
                .append(outgoing.size() - unresolved - ambiguous).append(" resolved or not applicable, ")
                .append(unresolved).append(" marked `").append(OkfMarkers.UNRESOLVED).append("`, ")
                .append(ambiguous).append(" marked `").append(OkfMarkers.AMBIGUOUS).append("`.");
        if (executable.signatureStatus() != ResolutionStatus.RESOLVED) {
            out.append(" Some parameter types could not be resolved, so the signature uses source names and"
                    + " calls to this ").append(executable instanceof JavaConstructor ? "constructor" : "method")
                    .append(" may not be linked.");
        }
        return out.toString();
    }

    private static List<String> modifiers(JavaExecutable executable) {
        if (!(executable instanceof JavaMethod method)) {
            return List.of();
        }
        return Stream.of(
                        method.isAbstract() ? "abstract" : null,
                        method.isDefault() ? "default" : null,
                        method.isStatic() ? "static" : null,
                        method.isFinal() ? "final" : null,
                        method.isSynchronized() ? "synchronized" : null)
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }
}
