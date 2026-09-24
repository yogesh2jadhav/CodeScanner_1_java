package com.java2okf.model;

import java.util.List;

/**
 * A method, constructor, or record-component parameter.
 */
public record JavaParameter(String name, TypeRef type, boolean varArgs, boolean isFinal, List<JavaAnnotation> annotations) {

    public JavaParameter {
        annotations = List.copyOf(annotations);
    }
}
