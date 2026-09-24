package com.java2okf.okf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generator identity and generation time written into every document's
 * {@code generated:} frontmatter block.
 *
 * @param enabled   false omits the block entirely ({@code okf.includeProvenance: false})
 * @param generator e.g. {@code java2okf/1.0.0}
 * @param timestamp ISO-8601 UTC timestamp, or {@code null} for reproducible output
 */
public record Provenance(boolean enabled, String generator, String timestamp) {

    /** Returns the {@code generated:} map, or {@code null} when provenance is disabled. */
    public Map<String, Object> toFrontmatter() {
        if (!enabled) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("by", generator);
        if (timestamp != null) {
            map.put("at", timestamp);
        }
        return map;
    }
}
