package com.java2okf.model;

import java.util.List;

/**
 * An interface declaration. {@link #interfaces()} holds the extended interfaces.
 */
public final class JavaInterface extends JavaType {

    public JavaInterface(JavaTypeHeader header, List<TypeRef> extendedInterfaces) {
        super(header, extendedInterfaces);
    }
}
