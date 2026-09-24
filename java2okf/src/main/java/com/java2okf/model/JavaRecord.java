package com.java2okf.model;

import java.util.List;

/**
 * A record declaration. Components are also modelled as fields because the
 * compiler generates a private final field for each of them.
 */
public final class JavaRecord extends JavaType {

    private final List<JavaParameter> components;

    public JavaRecord(JavaTypeHeader header, List<TypeRef> interfaces, List<JavaParameter> components) {
        super(header, interfaces);
        this.components = List.copyOf(components);
    }

    /** Record components in declaration order. */
    public List<JavaParameter> components() {
        return components;
    }
}
