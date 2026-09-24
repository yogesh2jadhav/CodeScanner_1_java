package com.java2okf.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeModelTest {

    private static final JavaSourceLocation LOC = new JavaSourceLocation("src/A.java", 3, 9);

    @Test
    void entityIdsFollowDocumentedFormat() {
        assertEquals("java-class:com.example.claim.ClaimProcessor",
                EntityIds.typeId(TypeKind.CLASS, "com.example.claim.ClaimProcessor"));
        assertEquals("java-method:com.example.claim.ClaimProcessor.processClaim(java.lang.String)",
                EntityIds.methodId("com.example.claim.ClaimProcessor",
                        EntityIds.signature("processClaim", List.of("java.lang.String"))));
        assertEquals("java-field:com.example.claim.ClaimProcessor.repository",
                EntityIds.fieldId("com.example.claim.ClaimProcessor", "repository"));
        assertEquals("java-constructor:a.B(int)", EntityIds.constructorId("a.B", List.of("int")));
        assertEquals("java-package:(default)", EntityIds.packageId(""));
    }

    @Test
    void overloadsProduceDistinctIds() {
        String a = EntityIds.methodId("a.B", EntityIds.signature("m", List.of("int")));
        String b = EntityIds.methodId("a.B", EntityIds.signature("m", List.of("java.lang.String")));
        String c = EntityIds.methodId("a.B", EntityIds.signature("m", List.of()));
        assertEquals(3, List.of(a, b, c).stream().distinct().count());
    }

    @Test
    void declaringTypeNameIsRecoveredFromMemberIds() {
        assertEquals("a.Outer.Inner", EntityIds.declaringTypeName("java-method:a.Outer.Inner.m(java.util.Map.Entry)"));
        assertEquals("a.B", EntityIds.declaringTypeName("java-constructor:a.B(int)"));
        assertEquals("a.B", EntityIds.declaringTypeName("java-field:a.B.f"));
        assertNull(EntityIds.declaringTypeName("java-class:a.B"));
        assertTrue(EntityIds.isTypeId("java-record:a.R"));
        assertTrue(EntityIds.isExecutableId("java-constructor:a.B()"));
    }

    @Test
    void unresolvedRelationshipCannotCarryTargetId() {
        assertThrows(IllegalArgumentException.class, () -> JavaRelationship.of("s", RelationshipType.CALLS,
                "java-method:x.Y.z()", "z()", ResolutionStatus.UNRESOLVED, LOC, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> JavaRelationship.of("s", RelationshipType.CALLS,
                null, "z()", ResolutionStatus.RESOLVED, LOC, null, Map.of()));
    }

    @Test
    void mergingKeepsEarliestLocationAndAllLines() {
        JavaRelationship first = JavaRelationship.of("s", RelationshipType.CALLS, "t", "t", ResolutionStatus.RESOLVED,
                new JavaSourceLocation("src/A.java", 20, 20), null, Map.of());
        JavaRelationship second = JavaRelationship.of("s", RelationshipType.CALLS, "t", "t", ResolutionStatus.RESOLVED,
                new JavaSourceLocation("src/A.java", 7, 7), null, Map.of());
        assertEquals(first.dedupKey(), second.dedupKey());
        JavaRelationship merged = first.mergeWith(second);
        assertEquals(List.of(7, 20), merged.lines());
        assertEquals(7, merged.sourceLocation().lineStart());
    }

    @Test
    void relationshipOrderingIsTotalAndPutsUnresolvedLast() {
        JavaRelationship resolved = JavaRelationship.of("s", RelationshipType.CALLS, "java-method:b.B.m()", "b.B.m()",
                ResolutionStatus.RESOLVED, LOC, null, Map.of());
        JavaRelationship unresolved = JavaRelationship.of("s", RelationshipType.CALLS, null, "x.y()",
                ResolutionStatus.UNRESOLVED, LOC, null, Map.of());
        JavaRelationship otherSource = JavaRelationship.of("a", RelationshipType.CALLS, null, "x.y()",
                ResolutionStatus.UNRESOLVED, LOC, null, Map.of());
        List<JavaRelationship> list = new ArrayList<>(List.of(unresolved, resolved, otherSource));
        Collections.sort(list);
        assertEquals(List.of(otherSource, resolved, unresolved), list);
    }

    @Test
    void membersAreSortedDeterministically() {
        JavaClass type = new JavaClass(header("java-class:a.B", "a.B", TypeKind.CLASS), null, List.of());
        type.addMethod(method("java-method:a.B.z()", "a.B"));
        type.addMethod(method("java-method:a.B.a()", "a.B"));
        type.sortMembers();
        assertEquals("java-method:a.B.a()", type.methods().get(0).id());
        assertThrows(UnsupportedOperationException.class, () -> type.methods().clear());
    }

    @Test
    void projectIndexesEntities() {
        JavaClass type = new JavaClass(header("java-class:a.B", "a.B", TypeKind.CLASS), null, List.of());
        type.addMethod(method("java-method:a.B.m()", "java-class:a.B"));
        JavaProject project = new JavaProject("demo",
                List.of(new JavaPackage("java-package:a", "a", List.of(type.id()))),
                List.of(type), List.of(), List.of(), new FileStatistics(1, 1, 0));

        assertTrue(project.containsEntity("java-class:a.B"));
        assertTrue(project.containsEntity("java-method:a.B.m()"));
        assertTrue(project.containsEntity("java-package:a"));
        assertFalse(project.containsEntity("java-class:a.Missing"));
        assertEquals(type, project.typeByQualifiedName("a.B").orElseThrow());
        assertTrue(project.executable("java-method:a.B.m()").isPresent());
        assertEquals(1, project.countTypes(TypeKind.CLASS));
    }

    static JavaTypeHeader header(String id, String fqn, TypeKind kind) {
        String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
        String pkg = fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "";
        return new JavaTypeHeader(id, fqn, simple, pkg, kind, LOC, Visibility.PUBLIC,
                false, false, false, false, List.of(), null, List.of());
    }

    static JavaMethod method(String id, String typeId) {
        return new JavaMethod(id, typeId, "m", "m()", "void m()", TypeRef.notApplicable("void"),
                Visibility.PUBLIC, false, false, false, false, false, List.of(), List.of(), List.of(), List.of(),
                LOC, ResolutionStatus.RESOLVED);
    }
}
