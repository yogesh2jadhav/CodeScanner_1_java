package com.java2okf.validation;

import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.model.EntityIds;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.JavaType;
import com.java2okf.model.ResolutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates the in-memory knowledge model before it is declared successful:
 * unique IDs, relationship endpoints, and the "never fabricate" invariant.
 */
public class KnowledgeModelValidator {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeModelValidator.class);

    public ValidationReport validate(KnowledgeGraph graph) {
        ValidationReport report = new ValidationReport();
        JavaProject project = graph.project();
        checkUniqueIds(project, report);
        long implicitTargets = 0;
        for (JavaRelationship relationship : graph.relationships()) {
            if (!graph.isInternal(relationship.sourceId())) {
                report.error(null, null, "UNKNOWN_SOURCE", "Relationship " + relationship.type()
                        + " starts at unknown entity " + relationship.sourceId());
            }
            boolean unresolved = relationship.status() == ResolutionStatus.UNRESOLVED
                    || relationship.status() == ResolutionStatus.AMBIGUOUS;
            if (unresolved && relationship.targetId() != null) {
                report.error(null, null, "FABRICATED_TARGET", "Unresolved relationship from "
                        + relationship.sourceId() + " carries a target ID");
            }
            if (relationship.status() == ResolutionStatus.RESOLVED) {
                if (relationship.targetId() == null) {
                    report.error(null, null, "MISSING_TARGET", "Resolved relationship from "
                            + relationship.sourceId() + " has no target ID");
                } else if (graph.isImplicitMember(relationship.targetId())) {
                    implicitTargets++;
                } else if (EntityIds.isTypeId(relationship.targetId()) && !graph.isInternal(relationship.targetId())
                        && project.typeByQualifiedName(EntityIds.localPart(relationship.targetId())).isPresent()) {
                    // The type exists under a different kind prefix: an ID construction bug, not an external type.
                    report.error(null, null, "INCONSISTENT_TARGET", "Relationship target " + relationship.targetId()
                            + " does not match the declared kind of that type");
                }
            }
        }
        // Record accessors, default constructors, and enum values() are legitimate targets without
        // a source declaration; they are rendered as "(implicit)" and are not a validation problem.
        LOG.debug("{} resolved relationships target implicit members", implicitTargets);
        return report;
    }

    private void checkUniqueIds(JavaProject project, ValidationReport report) {
        Set<String> seen = new HashSet<>();
        for (JavaType type : project.types().values()) {
            add(seen, type.id(), report);
            type.methods().forEach(m -> add(seen, m.id(), report));
            type.constructors().forEach(c -> add(seen, c.id(), report));
            type.fields().forEach(f -> add(seen, f.id(), report));
        }
    }

    private static void add(Set<String> seen, String id, ValidationReport report) {
        if (!seen.add(id)) {
            report.countDuplicateId();
            report.error(null, null, "DUPLICATE_ID", "Duplicate entity ID " + id);
        }
    }
}
