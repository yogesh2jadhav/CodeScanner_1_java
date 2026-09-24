package com.java2okf.model;

import java.util.List;

/**
 * An annotation type declaration ({@code @interface}). Annotation members are
 * modelled as abstract methods.
 */
public final class JavaAnnotationType extends JavaType {

    public JavaAnnotationType(JavaTypeHeader header) {
        super(header, List.of());
    }
}
