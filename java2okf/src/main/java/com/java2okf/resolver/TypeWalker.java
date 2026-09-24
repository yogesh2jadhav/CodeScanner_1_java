package com.java2okf.resolver;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.List;

/**
 * Syntactic helpers for walking type expressions.
 */
final class TypeWalker {

    private TypeWalker() {
    }

    /**
     * Returns every class or interface type in {@code type} (including generic
     * arguments, wildcard bounds, and array components), excluding types that
     * only act as a qualifier of another type ({@code Map} in {@code Map.Entry}).
     */
    static List<ClassOrInterfaceType> classTypes(Type type) {
        return type.findAll(ClassOrInterfaceType.class).stream()
                .filter(t -> !isScopeOfEnclosingType(t))
                .toList();
    }

    private static boolean isScopeOfEnclosingType(ClassOrInterfaceType type) {
        Node parent = type.getParentNode().orElse(null);
        return parent instanceof ClassOrInterfaceType enclosing
                && enclosing.getScope().filter(scope -> scope == type).isPresent();
    }
}
