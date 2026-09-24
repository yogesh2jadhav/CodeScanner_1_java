package com.java2okf.okf;

import com.java2okf.model.JavaPackage;
import com.java2okf.model.JavaType;
import com.java2okf.model.TypeKind;

import java.util.List;

/**
 * Generates one document per package, listing its types and the internal
 * packages it depends on (and is depended on by).
 */
public class PackageDocumentGenerator {

    public OkfDocument generate(GenerationContext ctx, JavaPackage javaPackage) {
        String path = ctx.layout().pathOf(javaPackage.id()).orElseThrow();
        LinkRenderer links = ctx.links(path);
        MarkdownBuilder md = new MarkdownBuilder();
        md.heading(1, javaPackage.displayName());

        List<JavaType> types = javaPackage.typeIds().stream().map(id -> ctx.project().types().get(id)).toList();
        section(md, links, "Classes", types, TypeKind.CLASS, javaPackage);
        section(md, links, "Interfaces", types, TypeKind.INTERFACE, javaPackage);
        section(md, links, "Enums", types, TypeKind.ENUM, javaPackage);
        section(md, links, "Records", types, TypeKind.RECORD, javaPackage);
        section(md, links, "Annotations", types, TypeKind.ANNOTATION, javaPackage);

        md.bulletSection("Dependencies", ctx.graph().packageDependenciesOf(javaPackage.name()).stream()
                .map(name -> links.entity(ctx.project().packages().get(name).id())).toList());
        md.bulletSection("Dependents", ctx.graph().packageDependentsOf(javaPackage.name()).stream()
                .map(name -> links.entity(ctx.project().packages().get(name).id())).toList());

        md.heading(2, "Analysis Notes");
        md.paragraph("Package dependencies are aggregated from type-level dependencies between analysed packages. "
                + "Dependencies on libraries and the JDK are listed in the individual type documents.");

        OkfFrontmatter frontmatter = OkfFrontmatter.of(OkfDocumentType.JAVA_PACKAGE, javaPackage.id(), javaPackage.displayName())
                .tags(List.of("java", "package"))
                .provenance(ctx.provenance())
                .java("package", javaPackage.name())
                .java("types", String.valueOf(javaPackage.typeIds().size()));
        return new OkfDocument(path, frontmatter.build(), md.build());
    }

    private void section(MarkdownBuilder md, LinkRenderer links, String heading, List<JavaType> types, TypeKind kind,
                         JavaPackage javaPackage) {
        md.bulletSection(heading, types.stream()
                .filter(t -> t.kind() == kind)
                .map(t -> links.linkTo(t.id(), nameWithinPackage(t, javaPackage))
                        .orElse(MarkdownBuilder.code(t.qualifiedName()) + " " + OkfMarkers.NO_DOCUMENT))
                .toList());
    }

    /** {@code Outer.Inner} for nested types, the simple name otherwise. */
    static String nameWithinPackage(JavaType type, JavaPackage javaPackage) {
        return javaPackage.name().isEmpty() ? type.qualifiedName() : type.qualifiedName().substring(javaPackage.name().length() + 1);
    }
}
