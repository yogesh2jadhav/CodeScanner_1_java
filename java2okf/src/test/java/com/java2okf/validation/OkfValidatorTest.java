package com.java2okf.validation;

import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.graph.KnowledgeGraphBuilder;
import com.java2okf.model.FileStatistics;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.java2okf.testutil.TestProjects.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkfValidatorTest {

    @TempDir
    Path bundle;

    private final OkfValidator validator = new OkfValidator();

    private static String classDoc(String id, String body) {
        return "---\ntype: JavaClass\nid: \"" + id + "\"\ntitle: T\n---\n\n" + body;
    }

    private void validBase() {
        write(bundle, "index.md", "# Index\n\n- [A](classes/a.A.md)\n");
        write(bundle, "_metadata/analysis.json", "{}");
        write(bundle, "classes/a.A.md", classDoc("java-class:a.A", "# A\n\n## Dependencies\n\n- [B](./a.B.md) — field\n"));
        write(bundle, "classes/a.B.md", classDoc("java-class:a.B", "# B\n"));
    }

    private boolean hasProblem(ValidationReport report, String code) {
        return report.problems().stream().anyMatch(p -> p.code().equals(code));
    }

    @Test
    void validBundlePasses() {
        validBase();
        ValidationReport report = validator.validate(bundle);
        assertTrue(report.passed(), report.format(20));
        assertEquals(3, report.documents());
        assertEquals(3, report.validDocuments());
    }

    @Test
    void detectsBrokenLinks() {
        validBase();
        write(bundle, "classes/a.C.md", classDoc("java-class:a.C", "# C\n\n## Calls\n\n- [Gone](../methods/gone.md)\n"));
        ValidationReport report = validator.validate(bundle);
        assertFalse(report.passed());
        assertEquals(1, report.brokenLinks());
        assertTrue(hasProblem(report, "BROKEN_LINK"));
    }

    @Test
    void linksInsideCodeAreIgnoredButAbsoluteAndEscapingLinksAreRejected() {
        validBase();
        write(bundle, "classes/a.C.md", classDoc("java-class:a.C", """
                # C

                `[not](a/link.md)`

                ```
                [also not](missing.md)
                ```

                [abs](/etc/passwd)
                [up](../../outside.md)
                [web](https://example.com)
                """));
        ValidationReport report = validator.validate(bundle);
        assertTrue(hasProblem(report, "ABSOLUTE_LINK"));
        assertTrue(hasProblem(report, "LINK_OUTSIDE_BUNDLE"));
        assertEquals(1, report.brokenLinks(), "only the escaping link counts as broken; code and web links are ignored");
    }

    @Test
    void detectsMissingType() {
        validBase();
        write(bundle, "classes/a.C.md", "---\nid: java-class:a.C\ntitle: C\n---\n\n# C\n");
        ValidationReport report = validator.validate(bundle);
        assertEquals(1, report.missingTypeFields());
        assertFalse(report.passed());
    }

    @Test
    void detectsMalformedYamlAndMissingFrontmatter() {
        validBase();
        write(bundle, "classes/a.C.md", "---\ntype: [JavaClass\n---\n\n# C\n");
        write(bundle, "classes/a.D.md", "# no frontmatter\n");
        ValidationReport report = validator.validate(bundle);
        assertEquals(2, report.invalidFrontmatter());
        assertTrue(hasProblem(report, "INVALID_FRONTMATTER"));
        assertTrue(hasProblem(report, "MISSING_FRONTMATTER"));
    }

    @Test
    void reservedIndexMayOmitFrontmatter() {
        validBase();
        write(bundle, "classes/index.md", "# Classes\n\n- [A](./a.A.md)\n");
        assertTrue(validator.validate(bundle).passed());
    }

    @Test
    void detectsDuplicateIds() {
        validBase();
        write(bundle, "classes/a.Copy.md", classDoc("java-class:a.A", "# Copy\n"));
        ValidationReport report = validator.validate(bundle);
        assertEquals(1, report.duplicateIds());
        assertFalse(report.passed());
    }

    @Test
    void detectsIndistinguishableOverloads() {
        validBase();
        String method = "---\ntype: JavaMethod\nid: \"%s\"\ntitle: m\njava:\n  declaringClass: a.A\n  signature: \"m(int)\"\n---\n\n# m\n";
        write(bundle, "methods/a.A.m-111111.md", method.formatted("java-method:a.A.m(int)"));
        write(bundle, "methods/a.A.m-222222.md", method.formatted("java-method:a.A.m(int)@L9"));
        assertTrue(hasProblem(validator.validate(bundle), "INDISTINGUISHABLE_OVERLOAD"));
    }

    @Test
    void detectsConceptInWrongLocation() {
        validBase();
        write(bundle, "methods/a.E.md", classDoc("java-class:a.E", "# E\n"));
        assertTrue(hasProblem(validator.validate(bundle), "INVALID_LOCATION"));
    }

    @Test
    void countsUnresolvedAndRejectsUnmarkedRelationships() {
        validBase();
        write(bundle, "classes/a.C.md", classDoc("java-class:a.C", """
                # C

                ## Calls

                - `repo.save(..)` — UNRESOLVED
                - `pick(..)` — AMBIGUOUS
                - `java.util.List.add(java.lang.Object)` (external)
                - `mystery()`

                ## Fields

                - `count` — `int` · private
                """));
        ValidationReport report = validator.validate(bundle);
        assertEquals(2, report.unresolvedJavaReferences());
        assertEquals(1, report.unmarkedRelationships(), "plain `mystery()` is neither linked nor marked");
        assertFalse(report.passed());
    }

    @Test
    void detectsUnterminatedCodeBlockAndNonUtf8() throws IOException {
        validBase();
        write(bundle, "classes/a.C.md", classDoc("java-class:a.C", "# C\n\n```java\nclass C {}\n"));
        Files.write(bundle.resolve("classes/a.D.md"), new byte[] {'-', '-', '-', '\n', (byte) 0xC3, (byte) 0x28});
        ValidationReport report = validator.validate(bundle);
        assertTrue(hasProblem(report, "UNTERMINATED_CODE_BLOCK"));
        assertTrue(hasProblem(report, "NOT_UTF8"));
    }

    @Test
    void reportFormatShowsStatus() {
        validBase();
        String text = validator.validate(bundle).format(10);
        assertTrue(text.contains("OKF Validation"));
        assertTrue(text.contains("STATUS: PASS"));
    }

    @Test
    void modelValidatorAcceptsUnresolvedAndRejectsUnknownSources() {
        JavaRelationship unresolved = JavaRelationship.of("java-class:ghost.Missing", RelationshipType.CALLS, null, "x()",
                ResolutionStatus.UNRESOLVED, null, null, Map.of());
        JavaProject project = new JavaProject("t", List.of(), List.of(), List.of(unresolved), List.of(), new FileStatistics(0, 0, 0));
        KnowledgeGraph graph = new KnowledgeGraphBuilder().build(project);
        ValidationReport report = new KnowledgeModelValidator().validate(graph);
        assertTrue(report.problems().stream().anyMatch(p -> p.code().equals("UNKNOWN_SOURCE")));
        assertTrue(report.problems().stream().noneMatch(p -> p.code().equals("FABRICATED_TARGET")));
    }
}
