package com.java2okf.okf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for document frontmatter with a fixed field order:
 * {@code type, id, title, resource, tags, generated, java}.
 */
public final class OkfFrontmatter {

    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Map<String, Object> java = new LinkedHashMap<>();
    private List<String> tags;
    private Provenance provenance;
    private String resource;

    private OkfFrontmatter(OkfDocumentType type, String id, String title) {
        fields.put("type", type.value());
        if (id != null) {
            fields.put("id", id);
        }
        fields.put("title", title);
    }

    public static OkfFrontmatter of(OkfDocumentType type, String id, String title) {
        return new OkfFrontmatter(type, id, title);
    }

    public OkfFrontmatter resource(String resource) {
        this.resource = resource;
        return this;
    }

    public OkfFrontmatter tags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public OkfFrontmatter provenance(Provenance provenance) {
        this.provenance = provenance;
        return this;
    }

    /** Adds a field to the {@code java:} block; {@code null} and empty values are skipped. */
    public OkfFrontmatter java(String key, Object value) {
        if (value != null && !(value instanceof List<?> list && list.isEmpty()) && !"".equals(value)) {
            java.put(key, value);
        }
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> result = new LinkedHashMap<>(fields);
        if (resource != null) {
            result.put("resource", resource);
        }
        if (tags != null && !tags.isEmpty()) {
            result.put("tags", tags);
        }
        if (provenance != null && provenance.toFrontmatter() != null) {
            result.put("generated", provenance.toFrontmatter());
        }
        if (!java.isEmpty()) {
            result.put("java", java);
        }
        return result;
    }
}
