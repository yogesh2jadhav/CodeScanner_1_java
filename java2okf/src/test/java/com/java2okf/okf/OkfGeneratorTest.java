package com.java2okf.okf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.java2okf.config.OkfSettings;
import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.graph.KnowledgeGraphBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.java2okf.testutil.AnalysisFixture.analyzeResolved;
import static com.java2okf.testutil.TestProjects.writeJava;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkfGeneratorTest {

    @TempDir
    static Path project;

    private static KnowledgeGraph graph;
    private static List<BundleFile> files;

    private static final Provenance FIXED = new Provenance(true, "java2okf/test", "2026-09-24T10:00:00Z");
    private static final Pattern LINK = Pattern.compile("\\[[^\\]]*]\\(([^)]+)\\)");

    @BeforeAll
    static void generate() {
        writeJava(project, "app.model", "Item", "public class Item { public int weight() { return 1; } }");
        writeJava(project, "app.model", "Kind", "public enum Kind { A, B }");
        writeJava(project, "app.model", "Tag", "public record Tag(String name) {}");
        writeJava(project, "app.api", "Shipper", "public interface Shipper { void ship(app.model.Item item); }");
        writeJava(project, "app.core", "Warehouse", """
                import app.model.Item;
                import app.api.Shipper;
                import com.missing.Scanner;
                public class Warehouse implements Shipper {
                    private Scanner scanner;
                    public void ship(Item item) { item.weight(); load(item); load(item, 2); scanner.scan(item); }
                    void load(Item item) {}
                    void load(Item item, int times) {}
                }
                """);
        graph = new KnowledgeGraphBuilder().build(analyzeResolved(project));
        files = new OkfGenerator().generate(graph, new OkfSettings(), FIXED, MetadataGenerator.RunTiming.none(), true);
    }

    private static Optional<BundleFile> file(String path) {
        return files.stream().filter(f -> f.path().equals(path)).findFirst();
    }

    private static BundleFile methodFile(String prefix) {
        return files.stream().filter(f -> f.path().startsWith("methods/" + prefix + "-")).findFirst().orElseThrow();
    }

    private static Map<String, Object> frontmatter(BundleFile file) throws Exception {
        String text = file.content();
        assertTrue(text.startsWith("---\n"), file.path());
        String yaml = text.substring(4, text.indexOf("\n---\n", 4) + 1);
        return new ObjectMapper(new YAMLFactory()).readValue(yaml, new TypeReference<>() { });
    }

    private static List<String> links(BundleFile file) {
        Matcher matcher = LINK.matcher(file.content());
        List<String> result = new java.util.ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    @Test
    void generatesExpectedLayout() {
        for (String path : List.of("index.md", "log.md", "_metadata/analysis.json", "_metadata/errors.json",
                "packages/index.md", "packages/app.core.md", "classes/index.md", "classes/app.core.Warehouse.md",
                "interfaces/app.api.Shipper.md", "enums/app.model.Kind.md", "records/app.model.Tag.md", "methods/index.md")) {
            assertTrue(file(path).isPresent(), "missing " + path);
        }
        assertTrue(files.stream().anyMatch(f -> f.path().matches("methods/app\\.core\\.Warehouse\\.load-[0-9a-f]{6}\\.md")));
    }

    @Test
    void everyDocumentHasValidFrontmatterWithType() throws Exception {
        for (BundleFile file : files) {
            if (!file.path().endsWith(".md")) {
                continue;
            }
            Map<String, Object> frontmatter = frontmatter(file);
            assertNotNull(frontmatter.get("type"), file.path());
            assertNotNull(frontmatter.get("title"), file.path());
            @SuppressWarnings("unchecked")
            Map<String, Object> generated = (Map<String, Object>) frontmatter.get("generated");
            assertEquals("java2okf/test", generated.get("by"));
        }
    }

    @Test
    void conceptTypesMatchDirectories() throws Exception {
        assertEquals("JavaClass", frontmatter(file("classes/app.core.Warehouse.md").orElseThrow()).get("type"));
        assertEquals("JavaInterface", frontmatter(file("interfaces/app.api.Shipper.md").orElseThrow()).get("type"));
        assertEquals("JavaEnum", frontmatter(file("enums/app.model.Kind.md").orElseThrow()).get("type"));
        assertEquals("JavaRecord", frontmatter(file("records/app.model.Tag.md").orElseThrow()).get("type"));
        assertEquals("JavaPackage", frontmatter(file("packages/app.core.md").orElseThrow()).get("type"));
        Map<String, Object> method = frontmatter(methodFile("app.core.Warehouse.ship"));
        assertEquals("JavaMethod", method.get("type"));
        assertEquals("java-method:app.core.Warehouse.ship(app.model.Item)", method.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> java = (Map<String, Object>) method.get("java");
        assertEquals("app.core.Warehouse", java.get("declaringClass"));
        assertEquals("ship(app.model.Item)", java.get("signature"));
        assertEquals("void", java.get("returnType"));
    }

    @Test
    void classDocumentLinksToPackageInterfacesAndMethods() {
        String text = file("classes/app.core.Warehouse.md").orElseThrow().content();
        assertTrue(text.contains("[app.core](../packages/app.core.md)"));
        assertTrue(text.contains("[Shipper](../interfaces/app.api.Shipper.md)"));
        assertTrue(text.contains("[ship(Item)](../methods/app.core.Warehouse.ship-"));
        assertTrue(text.contains("[load(Item, int)](../methods/app.core.Warehouse.load-"), "overloads are listed separately");
        assertTrue(text.contains("`Scanner` — UNRESOLVED"), "unresolved field type is marked");
        assertTrue(text.contains("- [Item](./app.model.Item.md) — "), "dependencies link to documents");
    }

    @Test
    void methodDocumentLinksForwardAndBackward() {
        String ship = methodFile("app.core.Warehouse.ship").content();
        assertTrue(ship.contains("[Item.weight()](./app.model.Item.weight-"), "same-directory links use ./");
        assertTrue(ship.contains("[Warehouse.load(Item)](./app.core.Warehouse.load-"));
        assertTrue(ship.contains("[Warehouse.load(Item, int)](./app.core.Warehouse.load-"));
        assertTrue(ship.contains("`scanner.scan(..)` — UNRESOLVED"), "unresolved calls stay explicit");
        assertTrue(ship.contains("[Shipper.ship(Item)](./app.api.Shipper.ship-"), "overrides are linked");

        String weight = methodFile("app.model.Item.weight").content();
        assertTrue(weight.contains("## Called By"));
        assertTrue(weight.contains("[Warehouse.ship(Item)](./app.core.Warehouse.ship-"), "reverse Called By link");

        String interfaceMethod = methodFile("app.api.Shipper.ship").content();
        assertTrue(interfaceMethod.contains("## Overridden By"));
    }

    @Test
    void allRelativeLinksResolveWithinTheBundle() {
        java.util.Set<String> paths = new java.util.HashSet<>();
        files.forEach(f -> paths.add(f.path()));
        for (BundleFile file : files) {
            if (!file.path().endsWith(".md")) {
                continue;
            }
            for (String link : links(file)) {
                assertFalse(link.startsWith("/"), "absolute link in " + file.path());
                String target = Path.of(file.path()).resolveSibling(link).normalize().toString().replace('\\', '/');
                assertTrue(paths.contains(target), file.path() + " -> " + link + " (" + target + ")");
            }
        }
    }

    @Test
    void outputIsDeterministic() {
        List<BundleFile> again = new OkfGenerator().generate(new KnowledgeGraphBuilder().build(analyzeResolved(project)),
                new OkfSettings(), FIXED, MetadataGenerator.RunTiming.none(), true);
        assertEquals(files, again);
    }

    @Test
    void timestampsAndProvenanceCanBeDisabled() throws Exception {
        Provenance noTimestamp = new Provenance(true, "java2okf/test", null);
        List<BundleFile> reproducible = new OkfGenerator().generate(graph, new OkfSettings(), noTimestamp,
                MetadataGenerator.RunTiming.none(), true);
        @SuppressWarnings("unchecked")
        Map<String, Object> generated = (Map<String, Object>) frontmatter(reproducible.get(reproducible.size() - 1)).get("generated");
        assertFalse(generated.containsKey("at"));

        List<BundleFile> noProvenance = new OkfGenerator().generate(graph, new OkfSettings(),
                new Provenance(false, "x", null), MetadataGenerator.RunTiming.none(), true);
        assertFalse(frontmatter(noProvenance.stream().filter(f -> f.path().equals("index.md")).findFirst().orElseThrow())
                .containsKey("generated"));
    }

    @Test
    void disablingMethodDocumentsKeepsSummariesInClassDocuments() {
        OkfSettings settings = new OkfSettings();
        settings.setGenerateMethodDocuments(false);
        List<BundleFile> generated = new OkfGenerator().generate(graph, settings, FIXED, MetadataGenerator.RunTiming.none(), true);
        assertTrue(generated.stream().noneMatch(f -> f.path().startsWith("methods/")));
        String warehouse = generated.stream().filter(f -> f.path().equals("classes/app.core.Warehouse.md")).findFirst()
                .orElseThrow().content();
        assertTrue(warehouse.contains("  - Calls: "), "method relationships are summarised in the class document");
        assertTrue(warehouse.contains("`ship(Item)`"));
    }

    @Test
    void layoutProducesPortableCollisionFreeNames() {
        assertEquals("../methods/a.md", DocumentLayout.relativeLink("classes/x.md", "methods/a.md"));
        assertEquals("./b.md", DocumentLayout.relativeLink("classes/x.md", "classes/b.md"));
        assertEquals("classes/b.md", DocumentLayout.relativeLink("index.md", "classes/b.md"));
        assertEquals("../index.md", DocumentLayout.relativeLink("classes/x.md", "index.md"));
        assertEquals("a_b_c", DocumentLayout.sanitize("a<b>c"));
        assertEquals(DocumentLayout.hash("x", 6), DocumentLayout.hash("x", 6));
        assertEquals("OrderRepository.save(Order)", DocumentLayout.label("java-method:a.OrderRepository.save(a.Order)"));
        assertEquals("Order(Customer, int)", DocumentLayout.label("java-constructor:a.Order(a.Customer,int)"));
    }

    @Test
    void caseInsensitiveCollisionsAreDisambiguated(@TempDir Path other) {
        // Nested type p.A.B and top-level type p.a.B map to names that differ only in case.
        writeJava(other, "p", "A", "public class A { public static class B {} }");
        writeJava(other, "p.a", "B", "public class B {}");
        KnowledgeGraph g = new KnowledgeGraphBuilder().build(analyzeResolved(other));
        DocumentLayout layout = DocumentLayout.plan(g.project(), new OkfSettings());
        String upper = layout.pathOf("java-class:p.A.B").orElseThrow();
        String lower = layout.pathOf("java-class:p.a.B").orElseThrow();
        assertFalse(upper.equalsIgnoreCase(lower), upper + " vs " + lower);
    }

    @Test
    void metadataCountsEntities() throws Exception {
        Map<String, Object> analysis = new ObjectMapper().readValue(file("_metadata/analysis.json").orElseThrow().content(),
                new TypeReference<>() { });
        assertEquals(2, analysis.get("classes"));
        assertEquals(1, analysis.get("interfaces"));
        assertEquals(1, analysis.get("enums"));
        assertEquals(1, analysis.get("records"));
        assertEquals("0.2", analysis.get("okfVersion"));
        assertFalse(analysis.containsKey("startedAt"), "no timestamps when timing is disabled");
        assertTrue(((Number) analysis.get("unresolvedRelationships")).intValue() > 0);
    }
}
