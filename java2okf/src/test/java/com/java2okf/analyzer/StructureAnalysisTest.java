package com.java2okf.analyzer;

import com.java2okf.model.JavaClass;
import com.java2okf.model.JavaConstructor;
import com.java2okf.model.JavaEnum;
import com.java2okf.model.JavaMethod;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRecord;
import com.java2okf.model.JavaType;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;
import com.java2okf.model.TypeKind;
import com.java2okf.model.Visibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.java2okf.testutil.AnalysisFixture.analyzeUnresolved;
import static com.java2okf.testutil.AnalysisFixture.relationships;
import static com.java2okf.testutil.TestProjects.writeJava;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structure extraction that does not depend on symbol resolution.
 */
class StructureAnalysisTest {

    @TempDir
    Path project;

    @Test
    void identifiesAllTypeKinds() {
        writeJava(project, "p", "C", "public abstract class C {}");
        writeJava(project, "p", "I", "public interface I {}");
        writeJava(project, "p", "E", "public enum E { A, B }");
        writeJava(project, "p", "R", "public record R(int x) {}");
        writeJava(project, "p", "A", "public @interface A {}");

        JavaProject model = analyzeUnresolved(project);

        assertEquals(TypeKind.CLASS, model.type("java-class:p.C").orElseThrow().kind());
        assertTrue(model.type("java-class:p.C").orElseThrow().header().isAbstract());
        assertEquals(TypeKind.INTERFACE, model.type("java-interface:p.I").orElseThrow().kind());
        assertEquals(List.of("A", "B"), ((JavaEnum) model.type("java-enum:p.E").orElseThrow()).constants());
        assertEquals(1, ((JavaRecord) model.type("java-record:p.R").orElseThrow()).components().size());
        assertEquals(TypeKind.ANNOTATION, model.type("java-annotation:p.A").orElseThrow().kind());
        assertEquals(1, model.packages().size());
        assertEquals(5, model.packages().get("p").typeIds().size());
    }

    @Test
    void modelsNestedAndInnerClasses() {
        writeJava(project, "p", "Outer", """
                public class Outer {
                    static class Nested {}
                    class Inner {}
                    interface Callback {}
                    enum Mode { ON }
                }
                """);
        JavaProject model = analyzeUnresolved(project);

        JavaType outer = model.type("java-class:p.Outer").orElseThrow();
        assertEquals(List.of("java-class:p.Outer.Inner", "java-class:p.Outer.Nested",
                "java-enum:p.Outer.Mode", "java-interface:p.Outer.Callback"), outer.nestedTypeIds());
        assertTrue(model.type("java-class:p.Outer.Nested").orElseThrow().header().isStatic());
        assertFalse(model.type("java-class:p.Outer.Inner").orElseThrow().header().isStatic());
        assertTrue(model.type("java-enum:p.Outer.Mode").orElseThrow().header().isStatic(), "nested enums are implicitly static");
        assertEquals("java-class:p.Outer", model.type("java-class:p.Outer.Inner").orElseThrow().header().enclosingTypeId());
        assertEquals(4, relationships(model, "java-class:p.Outer", RelationshipType.DECLARES).size());
    }

    @Test
    void capturesMethodDetails() {
        writeJava(project, "p", "S", """
                public class S {
                    public static synchronized int count(int a, long... rest) throws java.io.IOException { return a; }
                    protected final void touch() {}
                    private <T> T id(T value) { return value; }
                }
                """);
        JavaProject model = analyzeUnresolved(project);

        JavaMethod count = model.methods().get("java-method:p.S.count(int,long[])");
        assertTrue(count.isStatic());
        assertTrue(count.isSynchronized());
        assertEquals("int", count.returnType().displayName());
        assertEquals(ResolutionStatus.NOT_APPLICABLE, count.returnType().status());
        assertEquals(List.of("a", "rest"), count.parameters().stream().map(p -> p.name()).toList());
        assertTrue(count.parameters().get(1).varArgs());
        assertEquals("long...", count.parameters().get(1).type().displayName());
        assertEquals(1, count.throwsTypes().size());
        assertEquals(ResolutionStatus.RESOLVED, count.signatureStatus(), "primitive-only signatures are fully known");
        assertEquals(4, count.location().lineStart(), "line numbers include the generated package header");

        JavaMethod touch = model.methods().get("java-method:p.S.touch()");
        assertEquals(Visibility.PROTECTED, touch.visibility());
        assertTrue(touch.isFinal());

        JavaMethod id = model.methods().values().stream().filter(m -> m.name().equals("id")).findFirst().orElseThrow();
        assertEquals(List.of("T"), id.typeParameters());
        assertEquals(Visibility.PRIVATE, id.visibility());
    }

    @Test
    void distinguishesOverloads() {
        writeJava(project, "p", "O", """
                public class O {
                    void m() {}
                    void m(int a) {}
                    void m(int a, int b) {}
                    void m(double a) {}
                }
                """);
        JavaProject model = analyzeUnresolved(project);
        List<String> ids = model.type("java-class:p.O").orElseThrow().methods().stream().map(JavaMethod::id).toList();
        assertEquals(List.of("java-method:p.O.m()", "java-method:p.O.m(double)", "java-method:p.O.m(int)",
                "java-method:p.O.m(int,int)"), ids);
    }

    @Test
    void capturesConstructorsIncludingCompactRecordConstructors() {
        writeJava(project, "p", "K", """
                public class K {
                    public K() {}
                    K(int x) { this(); }
                }
                """);
        writeJava(project, "p", "R", "public record R(int x, int y) { public R { } }");
        writeJava(project, "p", "E", "public enum E { A(1); E(int v) {} }");

        JavaProject model = analyzeUnresolved(project);

        List<JavaConstructor> constructors = model.type("java-class:p.K").orElseThrow().constructors();
        assertEquals(List.of("java-constructor:p.K()", "java-constructor:p.K(int)"),
                constructors.stream().map(JavaConstructor::id).toList());
        assertEquals(Visibility.PACKAGE_PRIVATE, constructors.get(1).visibility());
        assertTrue(model.constructors().containsKey("java-constructor:p.R(int,int)"));
        assertEquals(Visibility.PRIVATE, model.constructors().get("java-constructor:p.E(int)").visibility(),
                "enum constructors are implicitly private");
    }

    @Test
    void interfaceMembersHaveImplicitModifiers() {
        writeJava(project, "p", "I", """
                public interface I {
                    int LIMIT = 3;
                    void run();
                    default void walk() {}
                    static void create() {}
                }
                """);
        JavaProject model = analyzeUnresolved(project);
        JavaMethod run = model.methods().get("java-method:p.I.run()");
        assertTrue(run.isAbstract());
        assertEquals(Visibility.PUBLIC, run.visibility());
        assertFalse(model.methods().get("java-method:p.I.walk()").isAbstract());
        assertTrue(model.methods().get("java-method:p.I.walk()").isDefault());
        assertTrue(model.fields().get("java-field:p.I.LIMIT").isStatic());
        assertTrue(model.fields().get("java-field:p.I.LIMIT").isFinal());
    }

    @Test
    void capturesFieldsAndRecordComponents() {
        writeJava(project, "p", "F", """
                public class F {
                    private static final int A = 1, B = 2;
                    protected transient volatile String name;
                }
                """);
        writeJava(project, "p", "R", "public record R(String label) {}");
        JavaProject model = analyzeUnresolved(project);

        JavaType f = model.type("java-class:p.F").orElseThrow();
        assertEquals(List.of("A", "B", "name"), f.fields().stream().map(x -> x.name()).toList());
        assertTrue(model.fields().get("java-field:p.F.name").isVolatile());
        assertTrue(model.fields().get("java-field:p.F.name").isTransient());
        assertEquals(3, relationships(model, "java-class:p.F", RelationshipType.HAS_FIELD).size());
        assertTrue(model.fields().containsKey("java-field:p.R.label"));
    }

    @Test
    void capturesAnnotations() {
        writeJava(project, "p", "N", """
                @Deprecated
                public class N {
                    @SuppressWarnings("x") int f;
                    @Override public String toString() { return ""; }
                    void m(@Deprecated int a) {}
                }
                """);
        JavaProject model = analyzeUnresolved(project);
        assertEquals("Deprecated", model.type("java-class:p.N").orElseThrow().header().annotations().get(0).name());
        assertEquals(1, model.fields().get("java-field:p.N.f").annotations().size());
        assertEquals(1, model.methods().get("java-method:p.N.m(int)").parameters().get(0).annotations().size());
        assertEquals(1, relationships(model, "java-class:p.N", RelationshipType.ANNOTATED_WITH).size());
        assertEquals(1, relationships(model, "java-method:p.N.m(int)", RelationshipType.ANNOTATED_WITH).size());
    }

    @Test
    void recordsCallsAsUnresolvedWhenResolutionIsDisabled() {
        writeJava(project, "p", "U", """
                public class U {
                    void a() { b(); helper.run(1); }
                    void b() {}
                    Object helper;
                }
                """);
        JavaProject model = analyzeUnresolved(project);
        var calls = relationships(model, "java-method:p.U.a()", RelationshipType.CALLS);
        assertEquals(2, calls.size());
        calls.forEach(c -> {
            assertEquals(ResolutionStatus.UNRESOLVED, c.status());
            assertNull(c.targetId(), "unresolved calls never carry a fabricated target");
        });
        assertEquals(List.of("b()", "helper.run(..)"), calls.stream().map(c -> c.targetName()).sorted().toList());
        assertTrue(model.issues().isEmpty(), "disabled resolution does not flood the issue list");
    }

    @Test
    void classWithoutExtendsHasNoSuperClass() {
        writeJava(project, "p", "Plain", "public class Plain {}");
        JavaProject model = analyzeUnresolved(project);
        assertNull(((JavaClass) model.type("java-class:p.Plain").orElseThrow()).superClass());
    }

    @Test
    void duplicateTypesKeepFirstFileAndReportIssue() {
        com.java2okf.testutil.TestProjects.write(project, "a/src/main/java/p/D.java", "package p; public class D { void x() {} }");
        com.java2okf.testutil.TestProjects.write(project, "b/src/main/java/p/D.java", "package p; public class D { void y() {} }");
        JavaProject model = analyzeUnresolved(project);
        assertTrue(model.methods().containsKey("java-method:p.D.x()"));
        assertFalse(model.methods().containsKey("java-method:p.D.y()"));
        assertTrue(model.issues().stream().anyMatch(i -> i.message().contains("Duplicate type p.D")));
    }
}
