package com.java2okf.okf;

import com.java2okf.model.TypeKind;

/**
 * Values of the OKF {@code type} frontmatter field produced by Java2OKF, and
 * the bundle directory each concept type lives in.
 */
public enum OkfDocumentType {
    JAVA_CLASS("JavaClass", "classes"),
    JAVA_INTERFACE("JavaInterface", "interfaces"),
    JAVA_ENUM("JavaEnum", "enums"),
    JAVA_RECORD("JavaRecord", "records"),
    JAVA_ANNOTATION("JavaAnnotation", "annotations"),
    JAVA_METHOD("JavaMethod", "methods"),
    JAVA_CONSTRUCTOR("JavaConstructor", "methods"),
    JAVA_PACKAGE("JavaPackage", "packages"),
    INDEX("Index", null),
    LOG("Log", null);

    private final String value;
    private final String directory;

    OkfDocumentType(String value, String directory) {
        this.value = value;
        this.directory = directory;
    }

    /** The literal {@code type:} value. */
    public String value() {
        return value;
    }

    /** Directory for concept documents of this type; {@code null} for reserved documents. */
    public String directory() {
        return directory;
    }

    public static OkfDocumentType forKind(TypeKind kind) {
        return switch (kind) {
            case CLASS -> JAVA_CLASS;
            case INTERFACE -> JAVA_INTERFACE;
            case ENUM -> JAVA_ENUM;
            case RECORD -> JAVA_RECORD;
            case ANNOTATION -> JAVA_ANNOTATION;
        };
    }

    /** Looks up a type by its frontmatter value; returns {@code null} if unknown. */
    public static OkfDocumentType fromValue(String value) {
        for (OkfDocumentType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
