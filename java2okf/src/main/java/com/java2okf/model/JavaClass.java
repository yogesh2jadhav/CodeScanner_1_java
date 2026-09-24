package com.java2okf.model;

import java.util.List;

/**
 * A class declaration.
 */
public final class JavaClass extends JavaType {

    private final TypeRef superClass;

    /**
     * @param superClass the explicit {@code extends} clause, or {@code null} when the class implicitly extends Object
     */
    public JavaClass(JavaTypeHeader header, TypeRef superClass, List<TypeRef> interfaces) {
        super(header, interfaces);
        this.superClass = superClass;
    }

    public TypeRef superClass() {
        return superClass;
    }
}
