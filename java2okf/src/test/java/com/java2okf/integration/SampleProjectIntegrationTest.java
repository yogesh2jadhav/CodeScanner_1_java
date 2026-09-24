package com.java2okf.integration;

import com.java2okf.AnalysisPipeline;
import com.java2okf.analyzer.ProgressListener;
import com.java2okf.config.Java2OkfConfig;
import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.RelationshipType;
import com.java2okf.validation.OkfValidator;
import com.java2okf.validation.ValidationReport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end run on {@code examples/sample-project}: OrderService → OrderRepository, Order, Customer.
 */
class SampleProjectIntegrationTest {

    private static final Path SAMPLE = Path.of("examples/sample-project");
    private static final Pattern LINK = Pattern.compile("\\[[^\\]]*]\\(([^)#]+)\\)");

    @TempDir
    static Path temp;

    private static AnalysisPipeline.Result first;
    private static Path firstOutput;
    private static Path secondOutput;

    @BeforeAll
    static void runTwice() {
        firstOutput = temp.resolve("run1");
        secondOutput = temp.resolve("run2");
        first = new AnalysisPipeline().run(config(firstOutput, 1), ProgressListener.NONE);
        // Different worker count on purpose: output must not depend on scheduling.
        new AnalysisPipeline().run(config(secondOutput, 4), ProgressListener.NONE);
    }

    private static Java2OkfConfig config(Path output, int threads) {
        Java2OkfConfig config = Java2OkfConfig.defaults();
        config.getProject().setName("sample-project");
        config.getProject().setSourceRoot(SAMPLE.toString());
        config.getOutput().setDirectory(output.toString());
        config.getOkf().setIncludeGenerationTimestamp(false);
        config.getPerformance().setThreadCount(threads);
        config.getPerformance().setParallelAnalysis(threads > 1);
        return config;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path methodDoc(String prefix) throws IOException {
        try (Stream<Path> files = Files.list(firstOutput.resolve("methods"))) {
            return files.filter(p -> p.getFileName().toString().startsWith(prefix + "-")).findFirst().orElseThrow();
        }
    }

    @Test
    void discoversAllClassesAndMethods() {
        KnowledgeGraph graph = first.graph();
        assertEquals(List.of("java-class:com.example.Customer", "java-class:com.example.Order",
                        "java-class:com.example.OrderRepository", "java-class:com.example.OrderService",
                        "java-enum:com.example.Order.Status"),
                List.copyOf(graph.project().types().keySet()));
        assertTrue(graph.project().methods().containsKey("java-method:com.example.OrderService.placeOrder(com.example.Customer,double)"));
        assertTrue(graph.project().methods().containsKey("java-method:com.example.OrderRepository.save(com.example.Order)"));
        assertEquals(3, graph.project().constructors().size());
        assertEquals(0, graph.project().fileStatistics().failed());
    }

    @Test
    void discoversDependenciesAndCalls() {
        KnowledgeGraph graph = first.graph();
        List<String> deps = graph.dependenciesOf("java-class:com.example.OrderService").stream()
                .filter(d -> d.internal()).map(d -> d.targetTypeId()).toList();
        assertEquals(List.of("java-class:com.example.Customer", "java-class:com.example.Order",
                "java-class:com.example.OrderRepository"), deps);
        List<String> calls = graph.outgoing("java-method:com.example.OrderService.placeOrder(com.example.Customer,double)",
                RelationshipType.CALLS).stream().map(JavaRelationship::targetId).toList();
        assertTrue(calls.contains("java-method:com.example.OrderRepository.save(com.example.Order)"));
        assertTrue(calls.contains("java-constructor:com.example.Order(java.lang.String,com.example.Customer,double)"));
        assertTrue(graph.relationships().stream().noneMatch(r -> r.status() == com.java2okf.model.ResolutionStatus.UNRESOLVED),
                "the sample project resolves completely against the JDK");
    }

    @Test
    void generatesClassAndMethodDocumentsWithValidLinks() throws IOException {
        String service = read(firstOutput.resolve("classes/com.example.OrderService.md"));
        assertTrue(service.contains("[placeOrder(Customer, double)](../methods/com.example.OrderService.placeOrder-"),
                "class → method link");
        String placeOrder = read(methodDoc("com.example.OrderService.placeOrder"));
        assertTrue(placeOrder.contains("[OrderRepository.save(Order)](./com.example.OrderRepository.save-"), "method → method link");
        String save = read(methodDoc("com.example.OrderRepository.save"));
        assertTrue(save.contains("## Called By"));
        assertTrue(save.contains("[OrderService.placeOrder(Customer, double)](./com.example.OrderService.placeOrder-"),
                "reverse Called By link");
        String repository = read(firstOutput.resolve("classes/com.example.OrderRepository.md"));
        assertTrue(repository.contains("## Called By"));

        for (Path document : allFiles(firstOutput)) {
            if (!document.toString().endsWith(".md")) {
                continue;
            }
            Matcher matcher = LINK.matcher(read(document));
            while (matcher.find()) {
                Path target = document.getParent().resolve(matcher.group(1)).normalize();
                assertTrue(Files.exists(target), firstOutput.relativize(document) + " -> " + matcher.group(1));
            }
        }
    }

    @Test
    void validationPasses() {
        assertTrue(first.isSuccessful(), first.validation().format(20));
        ValidationReport report = new OkfValidator().validate(firstOutput);
        assertTrue(report.passed(), report.format(20));
        assertEquals(0, report.brokenLinks());
        assertTrue(report.documents() > 20);
    }

    @Test
    void outputIsDeterministicAcrossRunsAndWorkerCounts() throws IOException {
        Map<String, String> a = snapshot(firstOutput);
        Map<String, String> b = snapshot(secondOutput);
        assertEquals(a.keySet(), b.keySet());
        a.forEach((path, content) -> assertEquals(content, b.get(path), path));
        assertTrue(a.values().stream().noneMatch(c -> c.contains(temp.toString())), "no machine-specific paths in output");
    }

    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        for (Path file : allFiles(root)) {
            files.put(root.relativize(file).toString(), read(file));
        }
        return files;
    }

    private static List<Path> allFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return new ArrayList<>(walk.filter(Files::isRegularFile).sorted().toList());
        }
    }
}
