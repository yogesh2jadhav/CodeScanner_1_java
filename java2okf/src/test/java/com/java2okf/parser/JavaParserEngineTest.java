package com.java2okf.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.java2okf.config.AnalysisSettings;
import com.java2okf.config.ScannerSettings;
import com.java2okf.scanner.ProjectScanner;
import com.java2okf.scanner.SourceFileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.java2okf.testutil.TestProjects.writeJava;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaParserEngineTest {

    @TempDir
    Path project;

    private final JavaParserEngine engine = new JavaParserEngine();

    private ParseOutcome parse(String packageName, String typeName, String body) {
        writeJava(project, packageName, typeName, body);
        SourceFileInfo info = new ProjectScanner(new ScannerSettings(), new AnalysisSettings()).scan(project).files().stream()
                .filter(f -> f.fileName().equals(typeName + ".java"))
                .findFirst().orElseThrow();
        return engine.parse(info);
    }

    private CompilationUnit parseOk(String typeName, String body) {
        ParseOutcome outcome = parse("p", typeName, body);
        assertTrue(outcome.isSuccess(), () -> "parse failed: " + outcome.problems());
        return outcome.parsed().orElseThrow().unit();
    }

    @Test
    void parsesClass() {
        CompilationUnit unit = parseOk("A", "public class A { int x; A() {} void m() {} }");
        assertTrue(unit.getClassByName("A").isPresent());
    }

    @Test
    void parsesInterface() {
        CompilationUnit unit = parseOk("I", "public interface I { void run(); default int size() { return 0; } }");
        assertTrue(unit.getInterfaceByName("I").isPresent());
    }

    @Test
    void parsesEnum() {
        CompilationUnit unit = parseOk("E", "public enum E { A, B; int code() { return ordinal(); } }");
        assertTrue(unit.getEnumByName("E").isPresent());
        assertEquals(2, unit.findFirst(EnumDeclaration.class).orElseThrow().getEntries().size());
    }

    @Test
    void parsesRecord() {
        CompilationUnit unit = parseOk("R", "public record R(String name, int age) { R { } }");
        assertTrue(unit.findFirst(RecordDeclaration.class).isPresent());
    }

    @Test
    void parsesNestedAndInnerClasses() {
        CompilationUnit unit = parseOk("Outer", """
                public class Outer {
                    static class Nested {}
                    class Inner { class Deeper {} }
                }
                """);
        List<ClassOrInterfaceDeclaration> classes = unit.findAll(ClassOrInterfaceDeclaration.class);
        assertEquals(4, classes.size());
    }

    @Test
    void parsesGenericsLambdasAndMethodReferences() {
        CompilationUnit unit = parseOk("G", """
                import java.util.*;
                import java.util.function.*;
                public class G<T extends Comparable<T>> {
                    <R> List<R> map(List<T> in, Function<? super T, ? extends R> f) {
                        List<R> out = new ArrayList<>();
                        in.forEach(t -> out.add(f.apply(t)));
                        in.stream().map(Object::toString).forEach(System.out::println);
                        return out;
                    }
                }
                """);
        assertEquals(1, unit.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow().getTypeParameters().size());
    }

    @Test
    void parsesAnnotationsAndAnnotationDeclarations() {
        CompilationUnit unit = parseOk("Marker", """
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Marker { String value() default ""; }
                """);
        AnnotationDeclaration declaration = unit.findFirst(AnnotationDeclaration.class).orElseThrow();
        assertEquals(1, declaration.getAnnotations().size());
    }

    @Test
    void parsesModernSyntax() {
        CompilationUnit unit = parseOk("S", """
                public sealed interface S permits S.A, S.B {
                    record A(int v) implements S {}
                    record B(String s) implements S {}
                    static String describe(Object o) {
                        return switch (o) {
                            case A(int v) when v > 0 -> "positive";
                            case A a -> "a";
                            case String str -> str;
                            default -> "other";
                        };
                    }
                    static String text() { return \"""
                        block
                        \"""; }
                }
                """);
        assertTrue(unit.getInterfaceByName("S").isPresent());
    }

    @Test
    void failureIsReportedWithLocationAndDoesNotThrow() {
        ParseOutcome outcome = parse("p", "Broken", "public class Broken { void m( { }");
        assertFalse(outcome.isSuccess());
        assertFalse(outcome.problems().isEmpty());
        assertTrue(outcome.problems().get(0).contains("line"), outcome.problems().toString());
        assertTrue(outcome.parsed().isEmpty());
    }
}
