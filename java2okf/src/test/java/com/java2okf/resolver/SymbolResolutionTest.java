package com.java2okf.resolver;

import com.java2okf.model.AnalysisStage;
import com.java2okf.model.JavaField;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.java2okf.testutil.AnalysisFixture.analyzeResolved;
import static com.java2okf.testutil.AnalysisFixture.relationships;
import static com.java2okf.testutil.AnalysisFixture.targetNames;
import static com.java2okf.testutil.TestProjects.writeJava;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolResolutionTest {

    @TempDir
    Path project;

    private List<String> targetIds(JavaProject model, String sourceId, RelationshipType type) {
        return relationships(model, sourceId, type).stream().map(JavaRelationship::targetId).toList();
    }

    @Test
    void resolvesMethodCallToDeclaringTypeAndSignature() {
        writeJava(project, "p", "Order", "public class Order {}");
        writeJava(project, "p", "OrderRepository", "public class OrderRepository { public void save(Order o) {} }");
        writeJava(project, "p", "OrderService", """
                public class OrderService {
                    private final OrderRepository repository = new OrderRepository();
                    public void place(Order order) { repository.save(order); }
                }
                """);
        JavaProject model = analyzeResolved(project);

        JavaRelationship call = relationships(model, "java-method:p.OrderService.place(p.Order)", RelationshipType.CALLS).get(0);
        assertEquals(ResolutionStatus.RESOLVED, call.status());
        assertEquals("java-method:p.OrderRepository.save(p.Order)", call.targetId());
        assertTrue(model.containsEntity(call.targetId()), "call-site IDs match declaration IDs");
    }

    @Test
    void keepsUnresolvableCallsExplicitlyUnresolved() {
        writeJava(project, "p", "Client", """
                import com.missing.ExternalApi;
                public class Client {
                    private ExternalApi api;
                    void run() { api.execute("x"); }
                }
                """);
        JavaProject model = analyzeResolved(project);

        JavaRelationship call = relationships(model, "java-method:p.Client.run()", RelationshipType.CALLS).get(0);
        assertEquals(ResolutionStatus.UNRESOLVED, call.status());
        assertNull(call.targetId());
        assertEquals("api.execute(..)", call.targetName());
        assertTrue(model.issues().stream().anyMatch(i -> i.stage() == AnalysisStage.SYMBOL_RESOLUTION
                && i.message().contains("api.execute")), "unresolved calls are reported");
        JavaField field = model.fields().get("java-field:p.Client.api");
        assertEquals(ResolutionStatus.UNRESOLVED, field.type().status());
        assertEquals(ResolutionStatus.UNRESOLVED,
                relationships(model, "java-class:p.Client", RelationshipType.IMPORTS).get(0).status());
    }

    @Test
    void distinguishesOverloadedMethodsAtCallSites() {
        writeJava(project, "p", "Printer", """
                public class Printer {
                    void print(int value) {}
                    void print(String value) {}
                    void print(Object value, int times) {}
                    void all() { print(1); print("x"); print(new Object(), 2); }
                }
                """);
        JavaProject model = analyzeResolved(project);
        List<String> targets = targetIds(model, "java-method:p.Printer.all()", RelationshipType.CALLS);
        assertTrue(targets.containsAll(List.of(
                "java-method:p.Printer.print(int)",
                "java-method:p.Printer.print(java.lang.String)",
                "java-method:p.Printer.print(java.lang.Object,int)")), targets.toString());
        targets.stream().filter(t -> t.startsWith("java-method:p.")).forEach(t -> assertTrue(model.containsEntity(t), t));
    }

    @Test
    void resolvesImportedAndSamePackageClasses() {
        writeJava(project, "q", "Thing", "public class Thing {}");
        writeJava(project, "p", "Sibling", "public class Sibling {}");
        writeJava(project, "p", "User", """
                import q.Thing;
                public class User {
                    Thing thing;
                    Sibling sibling;
                }
                """);
        JavaProject model = analyzeResolved(project);

        assertEquals("java-class:q.Thing", model.fields().get("java-field:p.User.thing").type().targetId());
        assertEquals("java-class:p.Sibling", model.fields().get("java-field:p.User.sibling").type().targetId());
        JavaRelationship importRel = relationships(model, "java-class:p.User", RelationshipType.IMPORTS).get(0);
        assertEquals(ResolutionStatus.RESOLVED, importRel.status());
        assertEquals("java-class:q.Thing", importRel.targetId());
    }

    @Test
    void resolvesJdkCallsWithErasedGenericSignatures() {
        writeJava(project, "p", "Lists", """
                import java.util.*;
                public class Lists {
                    List<String> names = new ArrayList<>();
                    void add(String n) { names.add(n); }
                }
                """);
        JavaProject model = analyzeResolved(project);
        assertEquals(List.of("java-method:java.util.List.add(java.lang.Object)"),
                targetIds(model, "java-method:p.Lists.add(java.lang.String)", RelationshipType.CALLS));
        assertTrue(targetIds(model, "java-class:p.Lists", RelationshipType.INSTANTIATES).contains("java-class:java.util.ArrayList"));
    }

    @Test
    void genericAndVarargsDeclarationsMatchCallSites() {
        writeJava(project, "p", "Box", """
                public class Box<T extends Comparable<T>> {
                    public void put(T item) {}
                    public static void log(String... parts) {}
                }
                """);
        writeJava(project, "p", "Use", """
                public class Use {
                    void run(Box<String> box) { box.put("a"); Box.log("x", "y"); }
                }
                """);
        JavaProject model = analyzeResolved(project);
        assertTrue(model.methods().containsKey("java-method:p.Box.put(java.lang.Comparable)"), model.methods().keySet().toString());
        assertTrue(model.methods().containsKey("java-method:p.Box.log(java.lang.String[])"));
        List<String> targets = targetIds(model, "java-method:p.Use.run(p.Box)", RelationshipType.CALLS);
        assertEquals(List.of("java-method:p.Box.log(java.lang.String[])", "java-method:p.Box.put(java.lang.Comparable)"), targets);
    }

    @Test
    void resolvesObjectCreationAndConstructorCalls() {
        writeJava(project, "p", "Part", """
                public class Part {
                    public Part() { this(1); }
                    public Part(int size) {}
                }
                """);
        writeJava(project, "p", "Factory", "public class Factory { Part make() { return new Part(3); } }");
        JavaProject model = analyzeResolved(project);

        assertEquals(List.of("java-class:p.Part"), targetIds(model, "java-method:p.Factory.make()", RelationshipType.INSTANTIATES));
        assertEquals(List.of("java-constructor:p.Part(int)"), targetIds(model, "java-method:p.Factory.make()", RelationshipType.CALLS));
        assertEquals(List.of("java-constructor:p.Part(int)"), targetIds(model, "java-constructor:p.Part()", RelationshipType.CALLS));
    }

    @Test
    void detectsOverridesIncludingSubstitutedGenerics() {
        writeJava(project, "p", "Order", "public class Order {}");
        writeJava(project, "p", "Repository", "public interface Repository<T> { void save(T item); }");
        writeJava(project, "p", "OrderRepository", """
                public class OrderRepository implements Repository<Order> {
                    @Override public void save(Order item) {}
                    @Override public String toString() { return "repo"; }
                }
                """);
        JavaProject model = analyzeResolved(project);
        assertEquals(List.of("java-method:p.Repository.save(java.lang.Object)"),
                targetIds(model, "java-method:p.OrderRepository.save(p.Order)", RelationshipType.OVERRIDES_METHOD));
        assertEquals(List.of("java-method:java.lang.Object.toString()"),
                targetIds(model, "java-method:p.OrderRepository.toString()", RelationshipType.OVERRIDES_METHOD));
        assertEquals(List.of("java-interface:p.Repository"),
                targetIds(model, "java-class:p.OrderRepository", RelationshipType.IMPLEMENTS));
        assertTrue(targetIds(model, "java-class:p.OrderRepository", RelationshipType.USES_TYPE).contains("java-class:p.Order"),
                "generic arguments of supertypes are dependencies");
    }

    @Test
    void unresolvableOverrideIsKeptExplicitly() {
        writeJava(project, "p", "Impl", """
                public class Impl extends com.missing.Base {
                    @Override public void hook() {}
                }
                """);
        JavaProject model = analyzeResolved(project);
        JavaRelationship override = relationships(model, "java-method:p.Impl.hook()", RelationshipType.OVERRIDES_METHOD).get(0);
        assertEquals(ResolutionStatus.UNRESOLVED, override.status());
        assertEquals(ResolutionStatus.UNRESOLVED, relationships(model, "java-class:p.Impl", RelationshipType.EXTENDS).get(0).status());
    }

    @Test
    void resolvesKindsAnnotationsFieldsAndVar() {
        writeJava(project, "p", "Color", "public enum Color { RED }");
        writeJava(project, "p", "Point", "public record Point(int x, int y) {}");
        writeJava(project, "p", "Audit", "public @interface Audit {}");
        writeJava(project, "p", "Canvas", """
                public class Canvas {
                    private Color color = Color.RED;
                    @Audit @Deprecated
                    Point draw() {
                        var p = new Point(1, 2);
                        Color c = this.color;
                        return p;
                    }
                }
                """);
        JavaProject model = analyzeResolved(project);
        String draw = "java-method:p.Canvas.draw()";
        assertEquals(List.of("java-record:p.Point"), targetIds(model, draw, RelationshipType.RETURNS_TYPE));
        assertEquals(List.of("java-annotation:java.lang.Deprecated", "java-annotation:p.Audit"),
                targetIds(model, draw, RelationshipType.ANNOTATED_WITH));
        List<String> uses = targetIds(model, draw, RelationshipType.USES_TYPE);
        assertTrue(uses.containsAll(List.of("java-enum:p.Color", "java-record:p.Point")), "var types are inferred: " + uses);
        assertTrue(targetIds(model, draw, RelationshipType.REFERENCES).contains("java-field:p.Canvas.color"));
        assertTrue(targetIds(model, draw, RelationshipType.CALLS).contains("java-constructor:p.Point(int,int)"));
    }

    @Test
    void resolvesNestedTypes() {
        writeJava(project, "p", "Outer", """
                public class Outer {
                    public static class Inner { public void ping() {} }
                    void use() { new Inner().ping(); }
                }
                """);
        JavaProject model = analyzeResolved(project);
        assertTrue(targetIds(model, "java-method:p.Outer.use()", RelationshipType.CALLS)
                .contains("java-method:p.Outer.Inner.ping()"));
        assertEquals(List.of("java-class:p.Outer.Inner"), targetIds(model, "java-method:p.Outer.use()", RelationshipType.INSTANTIATES));
    }

    @Test
    void methodReferencesAndLambdasAreAttributedToEnclosingMethod() {
        writeJava(project, "p", "Stream", """
                import java.util.List;
                public class Stream {
                    void each(List<String> items) {
                        items.forEach(this::handle);
                        items.forEach(i -> handle(i));
                    }
                    void handle(String s) {}
                }
                """);
        JavaProject model = analyzeResolved(project);
        String each = "java-method:p.Stream.each(java.util.List)";
        assertTrue(targetIds(model, each, RelationshipType.REFERENCES).contains("java-method:p.Stream.handle(java.lang.String)"));
        assertTrue(targetIds(model, each, RelationshipType.CALLS).contains("java-method:p.Stream.handle(java.lang.String)"));
        assertTrue(targetNames(model, each, RelationshipType.CALLS).contains("java.lang.Iterable.forEach(java.util.function.Consumer)"));
    }
}
