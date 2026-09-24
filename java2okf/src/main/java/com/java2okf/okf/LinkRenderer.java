package com.java2okf.okf;

import com.java2okf.graph.KnowledgeGraph;
import com.java2okf.model.EntityIds;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.ResolutionStatus;
import com.java2okf.model.TypeRef;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.java2okf.okf.MarkdownBuilder.code;
import static com.java2okf.okf.MarkdownBuilder.link;

/**
 * Renders references from one document to entities.
 *
 * <p>Markdown links are the primary relationship representation: whenever a
 * target document exists, a relative link is produced. Everything else is
 * rendered as code with an explicit marker from {@link OkfMarkers}, so a
 * reader can always tell an unresolved reference from an external one.</p>
 */
public final class LinkRenderer {

    private final DocumentLayout layout;
    private final KnowledgeGraph graph;
    private final String fromPath;

    public LinkRenderer(DocumentLayout layout, KnowledgeGraph graph, String fromPath) {
        this.layout = layout;
        this.graph = graph;
        this.fromPath = fromPath;
    }

    /** Link to an entity document, if it exists. */
    public Optional<String> linkTo(String id, String text) {
        return layout.pathOf(id).map(path -> link(text, DocumentLayout.relativeLink(fromPath, path)));
    }

    /** Relative path to an arbitrary bundle file. */
    public String pathTo(String bundlePath) {
        return DocumentLayout.relativeLink(fromPath, bundlePath);
    }

    /** Renders an entity known to be resolved, using its short label. */
    public String entity(String id) {
        return resolvedTarget(id, DocumentLayout.label(id), EntityIds.localPart(id));
    }

    /** Renders the target of a relationship. */
    public String target(JavaRelationship relationship) {
        return switch (relationship.status()) {
            case RESOLVED -> resolvedTarget(relationship.targetId(), DocumentLayout.label(relationship.targetId()),
                    relationship.targetName());
            case NOT_APPLICABLE -> relationship.targetId() != null
                    ? linkTo(relationship.targetId(), relationship.targetName())
                            .orElse(code(relationship.targetName()) + " " + unlinkedMarker(relationship.targetId()))
                    : code(relationship.targetName());
            case UNRESOLVED -> code(relationship.targetName()) + " — " + OkfMarkers.UNRESOLVED;
            case AMBIGUOUS -> code(relationship.targetName()) + " — " + OkfMarkers.AMBIGUOUS;
        };
    }

    /** Renders a declared type (field, parameter, return type). */
    public String type(TypeRef type) {
        if (type.status() == ResolutionStatus.RESOLVED) {
            Optional<String> linked = linkTo(type.targetId(), type.displayName());
            if (linked.isPresent()) {
                return linked.get();
            }
            return code(type.displayName()) + " " + unlinkedMarker(type.targetId());
        }
        if (type.status() == ResolutionStatus.NOT_APPLICABLE) {
            return code(type.displayName());
        }
        return code(type.displayName()) + " — " + (type.status() == ResolutionStatus.AMBIGUOUS
                ? OkfMarkers.AMBIGUOUS : OkfMarkers.UNRESOLVED);
    }

    /**
     * Orders relationships for display: linked internal targets first, then
     * external or implicit ones, then ambiguous, then unresolved; alphabetical
     * by target within each group. The underlying relationship order is by ID,
     * which is deterministic but groups by ID prefix rather than by relevance.
     */
    public List<JavaRelationship> displayOrder(List<JavaRelationship> relationships) {
        return relationships.stream()
                .sorted(Comparator.comparingInt(this::displayRank)
                        .thenComparing(JavaRelationship::targetName)
                        .thenComparing(JavaRelationship::compareTo))
                .toList();
    }

    private int displayRank(JavaRelationship relationship) {
        return switch (relationship.status()) {
            case RESOLVED -> graph.isInternal(relationship.targetId()) ? 0 : 1;
            case NOT_APPLICABLE -> 1;
            case AMBIGUOUS -> 2;
            case UNRESOLVED -> 3;
        };
    }

    /** Suffix listing the source lines of a relationship, e.g. {@code  — line 12}. */
    public static String lines(JavaRelationship relationship) {
        List<Integer> lines = relationship.lines();
        if (lines.isEmpty()) {
            return "";
        }
        if (lines.size() == 1) {
            return " — line " + lines.get(0);
        }
        return " — lines " + String.join(", ", lines.stream().map(String::valueOf).toList());
    }

    private String resolvedTarget(String id, String label, String qualifiedLabel) {
        Optional<String> linked = linkTo(id, label);
        if (linked.isPresent()) {
            return linked.get();
        }
        // Fields have no documents of their own; link to the declaring type instead.
        if (id.startsWith(EntityIds.FIELD_PREFIX + ":") && graph.isInternal(id)) {
            Optional<String> ownerLink = graph.owningType(id).flatMap(owner -> linkTo(owner.id(), label));
            if (ownerLink.isPresent()) {
                return ownerLink.get();
            }
        }
        return code(qualifiedLabel) + " " + unlinkedMarker(id);
    }

    private String unlinkedMarker(String id) {
        if (graph.isInternal(id)) {
            return OkfMarkers.NO_DOCUMENT;
        }
        return graph.isImplicitMember(id) ? OkfMarkers.IMPLICIT : OkfMarkers.EXTERNAL;
    }
}
