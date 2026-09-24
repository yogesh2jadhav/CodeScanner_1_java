package com.java2okf.model;

import java.util.List;

/**
 * An enum declaration.
 */
public final class JavaEnum extends JavaType {

    private final List<String> constants;

    public JavaEnum(JavaTypeHeader header, List<TypeRef> interfaces, List<String> constants) {
        super(header, interfaces);
        this.constants = List.copyOf(constants);
    }

    /** Enum constants in declaration order (the order is semantically meaningful). */
    public List<String> constants() {
        return constants;
    }
}
