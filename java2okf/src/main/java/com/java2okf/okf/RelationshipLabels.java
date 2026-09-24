package com.java2okf.okf;

import com.java2okf.model.DependencyCategory;
import com.java2okf.model.JavaRelationship;

import java.util.Locale;

/**
 * Human-readable descriptions of relationship facets.
 */
final class RelationshipLabels {

    private RelationshipLabels() {
    }

    static String category(DependencyCategory category) {
        return switch (category) {
            case FIELD_DEPENDENCY -> "field";
            case PARAMETER_DEPENDENCY -> "parameter";
            case RETURN_TYPE_DEPENDENCY -> "return type";
            case LOCAL_VARIABLE_DEPENDENCY -> "local variable";
            case CALL_DEPENDENCY -> "call";
            case INHERITANCE_DEPENDENCY -> "inheritance";
            case ANNOTATION_DEPENDENCY -> "annotation";
            case EXCEPTION_DEPENDENCY -> "exception";
            case TYPE_REFERENCE_DEPENDENCY -> "type reference";
        };
    }

    /** Describes how a method uses a type, e.g. {@code parameter `order`} or {@code cast}. */
    static String usage(JavaRelationship relationship) {
        return switch (relationship.type()) {
            case HAS_PARAMETER -> "parameter " + MarkdownBuilder.code(relationship.details().getOrDefault("parameter", "?"));
            case RETURNS_TYPE -> "return type";
            case THROWS -> "throws";
            case USES_TYPE -> relationship.details().containsKey("variable")
                    ? "local variable " + MarkdownBuilder.code(relationship.details().get("variable"))
                    : "type";
            case REFERENCES -> kind(relationship.details().getOrDefault("kind", "reference"));
            default -> relationship.type().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static String kind(String kind) {
        return switch (kind) {
            case "classLiteral" -> "class literal";
            case "arrayCreation" -> "array creation";
            case "methodReference" -> "method reference";
            default -> kind;
        };
    }
}
