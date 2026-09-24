package com.java2okf.analyzer;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.java2okf.model.Visibility;

/**
 * Helpers for Java modifiers, including the implicit ones the language adds.
 */
final class Modifiers {

    private Modifiers() {
    }

    /**
     * @param implicitlyPublic true for members of interfaces and annotation types,
     *                         which are public unless declared private
     */
    static Visibility visibility(NodeWithModifiers<?> node, boolean implicitlyPublic) {
        AccessSpecifier access = node.getAccessSpecifier();
        return switch (access) {
            case PUBLIC -> Visibility.PUBLIC;
            case PROTECTED -> Visibility.PROTECTED;
            case PRIVATE -> Visibility.PRIVATE;
            case NONE -> implicitlyPublic ? Visibility.PUBLIC : Visibility.PACKAGE_PRIVATE;
        };
    }

    static boolean has(NodeWithModifiers<?> node, Modifier.Keyword keyword) {
        return node.hasModifier(keyword);
    }
}
