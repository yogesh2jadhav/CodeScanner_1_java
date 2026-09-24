package com.java2okf.graph;

import com.java2okf.model.DependencyCategory;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.RelationshipType;
import com.java2okf.model.ResolutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.java2okf.testutil.AnalysisFixture.analyzeResolved;
import static com.java2okf.testutil.TestProjects.writeJava;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relationship extraction and graph queries on a small layered project.
 */
class RelationshipGraphTest {

    @TempDir
    static Path project;

    private static KnowledgeGraph graph;

    @BeforeEach
    void analyzeOnce() {
        if (graph != null) {
            return;
        }
        writeJava(project, "shop.model", "Customer", "public class Customer { String name; }");
        writeJava(project, "shop.model", "Order", """
                public class Order {
                    private final Customer customer;
                    public Order(Customer customer) { this.customer = customer; }
                    public Customer customer() { return customer; }
                }
                """);
        writeJava(project, "shop.model", "Status", "public enum Status { NEW, DONE }");
        writeJava(project, "shop.repo", "Repository", "public interface Repository<T> { void save(T item); }");
        writeJava(project, "shop.repo", "BaseRepository", """
                public abstract class BaseRepository<T> implements Repository<T> {
                    protected int count;
                }
                """);
        writeJava(project, "shop.repo", "OrderRepository", """
                import shop.model.Order;
                public class OrderRepository extends BaseRepository<Order> {
                    @Override public void save(Order order) { count++; }
                }
                """);
        writeJava(project, "shop.service", "OrderException", "public class OrderException extends Exception {}");
        writeJava(project, "shop.service", "OrderService", """
                import shop.model.*;
                import shop.repo.OrderRepository;
                @Deprecated
                public class OrderService {
                    private final OrderRepository repository = new OrderRepository();
                    public Order place(Customer customer) throws OrderException {
                        Order order = new Order(customer);
                        repository.save(order);
                        Status s = Status.values()[0];
                        return order;
                    }
                }
                """);
        graph = new KnowledgeGraphBuilder().build(analyzeResolved(project));
    }

    private static final String SERVICE = "java-class:shop.service.OrderService";
    private static final String PLACE = "java-method:shop.service.OrderService.place(shop.model.Customer)";
    private static final String SAVE = "java-method:shop.repo.OrderRepository.save(shop.model.Order)";

    private List<String> targets(String source, RelationshipType type) {
        return graph.outgoing(source, type).stream().map(JavaRelationship::targetId).toList();
    }

    @Test
    void extendsAndImplements() {
        assertEquals(List.of("java-class:shop.repo.BaseRepository"), targets("java-class:shop.repo.OrderRepository", RelationshipType.EXTENDS));
        assertEquals(List.of("java-interface:shop.repo.Repository"), targets("java-class:shop.repo.BaseRepository", RelationshipType.IMPLEMENTS));
        assertEquals(List.of("java-class:java.lang.Exception"), targets("java-class:shop.service.OrderException", RelationshipType.EXTENDS));
        assertEquals(List.of("java-class:shop.repo.OrderRepository"),
                graph.incoming("java-class:shop.repo.BaseRepository", RelationshipType.EXTENDS).stream().map(JavaRelationship::sourceId).toList());
    }

    @Test
    void fieldParameterAndReturnDependencies() {
        JavaRelationship field = graph.outgoing("java-field:shop.service.OrderService.repository", RelationshipType.USES_TYPE).get(0);
        assertEquals("java-class:shop.repo.OrderRepository", field.targetId());
        assertEquals(DependencyCategory.FIELD_DEPENDENCY, field.category());

        JavaRelationship parameter = graph.outgoing(PLACE, RelationshipType.HAS_PARAMETER).get(0);
        assertEquals("java-class:shop.model.Customer", parameter.targetId());
        assertEquals("customer", parameter.details().get("parameter"));
        assertEquals(DependencyCategory.PARAMETER_DEPENDENCY, parameter.category());

        assertEquals(List.of("java-class:shop.model.Order"), targets(PLACE, RelationshipType.RETURNS_TYPE));
        assertEquals(List.of("java-class:shop.service.OrderException"), targets(PLACE, RelationshipType.THROWS));
    }

    @Test
    void methodCallsConstructorCallsAndObjectCreation() {
        List<String> calls = targets(PLACE, RelationshipType.CALLS);
        assertTrue(calls.contains(SAVE), calls.toString());
        assertTrue(calls.contains("java-constructor:shop.model.Order(shop.model.Customer)"), calls.toString());
        assertTrue(targets(PLACE, RelationshipType.INSTANTIATES).contains("java-class:shop.model.Order"));
        assertTrue(targets(SERVICE, RelationshipType.INSTANTIATES).contains("java-class:shop.repo.OrderRepository"),
                "field initialisers are attributed to the type");
    }

    @Test
    void annotationsAreRelationships() {
        assertEquals(List.of("java-annotation:java.lang.Deprecated"), targets(SERVICE, RelationshipType.ANNOTATED_WITH));
        assertEquals(List.of("java-annotation:java.lang.Override"), targets(SAVE, RelationshipType.ANNOTATED_WITH));
    }

    @Test
    void reverseCallRelationships() {
        List<String> callers = graph.incoming(SAVE, RelationshipType.CALLS).stream().map(JavaRelationship::sourceId).toList();
        assertEquals(List.of(PLACE), callers);
        assertEquals(1, graph.callsIntoType("java-class:shop.repo.OrderRepository").size());
    }

    @Test
    void aggregatesDependenciesWithCategories() {
        Map<String, TypeDependency> deps = graph.dependenciesOf(SERVICE).stream()
                .collect(Collectors.toMap(TypeDependency::targetTypeId, Function.identity()));
        assertTrue(deps.get("java-class:shop.repo.OrderRepository").categories()
                .containsAll(Set.of(DependencyCategory.FIELD_DEPENDENCY, DependencyCategory.CALL_DEPENDENCY)));
        assertTrue(deps.get("java-class:shop.model.Customer").categories().contains(DependencyCategory.PARAMETER_DEPENDENCY));
        assertTrue(deps.get("java-class:shop.model.Order").categories().containsAll(Set.of(
                DependencyCategory.RETURN_TYPE_DEPENDENCY, DependencyCategory.LOCAL_VARIABLE_DEPENDENCY, DependencyCategory.CALL_DEPENDENCY)));
        assertTrue(deps.get("java-enum:shop.model.Status").internal());
        assertTrue(deps.get("java-annotation:java.lang.Deprecated").categories().contains(DependencyCategory.ANNOTATION_DEPENDENCY));
        assertFalse(deps.get("java-annotation:java.lang.Deprecated").internal());
        assertFalse(deps.containsKey(SERVICE), "self-dependencies are excluded");
    }

    @Test
    void reverseAndPackageDependencies() {
        assertTrue(graph.dependentsOf("java-class:shop.model.Order").containsAll(Set.of(SERVICE, "java-class:shop.repo.OrderRepository")));
        assertEquals(Set.of("shop.model", "shop.repo"), graph.packageDependenciesOf("shop.service"));
        assertTrue(graph.packageDependentsOf("shop.model").contains("shop.service"));
        assertTrue(graph.packageDependenciesOf("shop.model").isEmpty());
    }

    @Test
    void derivesTypeLevelOverridesAndImplicitMembers() {
        assertEquals(List.of("java-interface:shop.repo.Repository"), targets("java-class:shop.repo.OrderRepository", RelationshipType.OVERRIDES));
        JavaRelationship valuesCall = graph.outgoing(PLACE, RelationshipType.CALLS).stream()
                .filter(r -> r.targetName().contains("values")).findFirst().orElseThrow();
        assertEquals(ResolutionStatus.RESOLVED, valuesCall.status());
        assertTrue(graph.isImplicitMember(valuesCall.targetId()), "enum values() has no source declaration");
        assertFalse(graph.isImplicitMember(SAVE));
    }

    @Test
    void relationshipsAreSortedAndUnique() {
        List<JavaRelationship> all = graph.relationships();
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).compareTo(all.get(i)) < 0, "sorted and de-duplicated at " + i);
        }
        JavaProject project = graph.project();
        project.relationships().stream().filter(JavaRelationship::isResolved)
                .forEach(r -> assertTrue(r.targetId() != null));
    }
}
