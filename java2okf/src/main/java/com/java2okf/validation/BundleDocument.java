package com.java2okf.validation;

import java.util.Map;

/**
 * A Markdown file read back from a bundle.
 *
 * @param path             bundle-relative path
 * @param frontmatter      parsed frontmatter, or {@code null} if absent or invalid
 * @param body             Markdown body (everything after the frontmatter)
 * @param bodyStartLine    1-based line number of the first body line
 */
record BundleDocument(String path, Map<String, Object> frontmatter, String body, int bodyStartLine) {

    String fileName() {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    String directory() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    boolean isReserved() {
        return fileName().equals("index.md") || fileName().equals("log.md");
    }

    String stringField(String name) {
        Object value = frontmatter == null ? null : frontmatter.get(name);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    String javaField(String name) {
        Object java = frontmatter == null ? null : frontmatter.get("java");
        if (java instanceof Map<?, ?> map) {
            Object value = ((Map<String, Object>) map).get(name);
            return value == null ? null : value.toString();
        }
        return null;
    }
}
