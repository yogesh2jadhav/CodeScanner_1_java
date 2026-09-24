package com.java2okf.model;

import java.util.List;

/**
 * Builds deterministic entity IDs.
 *
 * <p>IDs are derived only from fully qualified names and erased parameter
 * types, so the same source always yields the same IDs on every machine and
 * overloads remain distinguishable. Random identifiers are never used.</p>
 *
 * <pre>
 * java-package:com.example
 * java-class:com.example.Order
 * java-method:com.example.OrderService.place(com.example.Order,int)
 * java-constructor:com.example.OrderService(com.example.OrderRepository)
 * java-field:com.example.OrderService.repository
 * </pre>
 */
public final class EntityIds {

    public static final String PACKAGE_PREFIX = "java-package";
    public static final String METHOD_PREFIX = "java-method";
    public static final String CONSTRUCTOR_PREFIX = "java-constructor";
    public static final String FIELD_PREFIX = "java-field";

    /** Placeholder name for the default package in IDs, which has no name in Java. */
    public static final String DEFAULT_PACKAGE = "(default)";

    private EntityIds() {
    }

    public static String packageId(String packageName) {
        return PACKAGE_PREFIX + ":" + (packageName.isEmpty() ? DEFAULT_PACKAGE : packageName);
    }

    public static String typeId(TypeKind kind, String qualifiedName) {
        return kind.idPrefix() + ":" + qualifiedName;
    }

    /** Builds a signature such as {@code save(com.example.Order,int)}. */
    public static String signature(String name, List<String> erasedParameterTypes) {
        return name + "(" + String.join(",", erasedParameterTypes) + ")";
    }

    public static String methodId(String declaringTypeQualifiedName, String signature) {
        return METHOD_PREFIX + ":" + declaringTypeQualifiedName + "." + signature;
    }

    /** Constructor IDs omit the name because it always equals the simple type name. */
    public static String constructorId(String declaringTypeQualifiedName, List<String> erasedParameterTypes) {
        return CONSTRUCTOR_PREFIX + ":" + declaringTypeQualifiedName + "(" + String.join(",", erasedParameterTypes) + ")";
    }

    public static String fieldId(String declaringTypeQualifiedName, String fieldName) {
        return FIELD_PREFIX + ":" + declaringTypeQualifiedName + "." + fieldName;
    }

    /** Returns the part after the first {@code :}, e.g. the qualified name of a type ID. */
    public static String localPart(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    public static boolean isTypeId(String id) {
        for (TypeKind kind : TypeKind.values()) {
            if (id.startsWith(kind.idPrefix() + ":")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isExecutableId(String id) {
        return id.startsWith(METHOD_PREFIX + ":") || id.startsWith(CONSTRUCTOR_PREFIX + ":");
    }

    /**
     * Returns the qualified name of the type that declares a method, constructor,
     * or field ID; {@code null} for other IDs.
     */
    public static String declaringTypeName(String memberId) {
        String local = localPart(memberId);
        if (memberId.startsWith(CONSTRUCTOR_PREFIX + ":")) {
            return local.substring(0, local.indexOf('('));
        }
        if (memberId.startsWith(METHOD_PREFIX + ":")) {
            String beforeParams = local.substring(0, local.indexOf('('));
            return beforeParams.substring(0, beforeParams.lastIndexOf('.'));
        }
        if (memberId.startsWith(FIELD_PREFIX + ":")) {
            return local.substring(0, local.lastIndexOf('.'));
        }
        return null;
    }
}
