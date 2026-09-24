package com.java2okf.resolver;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

/**
 * Produces short, stable textual representations of AST nodes for references
 * that could not be resolved.
 */
public final class SourceText {

    private static final int MAX_SCOPE_LENGTH = 60;

    private SourceText() {
    }

    /**
     * Describes a call as written, e.g. {@code repository.save(..)}. Arguments
     * are elided because they can be arbitrarily long (lambdas, nested calls).
     */
    public static String call(MethodCallExpr call) {
        String scope = call.getScope().map(Object::toString).map(SourceText::singleLine).orElse(null);
        String args = call.getArguments().isEmpty() ? "()" : "(..)";
        if (scope == null) {
            return call.getNameAsString() + args;
        }
        if (scope.length() > MAX_SCOPE_LENGTH) {
            scope = "…" + scope.substring(scope.length() - MAX_SCOPE_LENGTH);
        }
        return scope + "." + call.getNameAsString() + args;
    }

    /** Erased textual form of a type, e.g. {@code Map<K, V>[]} → {@code Map[]}. */
    public static String erasedName(Type type) {
        if (type instanceof ArrayType arrayType) {
            return erasedName(arrayType.getComponentType()) + "[]";
        }
        if (type instanceof ClassOrInterfaceType classType) {
            return classType.getNameWithScope();
        }
        return type.asString();
    }

    public static String singleLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /** Truncates diagnostic messages so that errors.json stays readable. */
    public static String reason(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + singleLine(error.getMessage());
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }
}
