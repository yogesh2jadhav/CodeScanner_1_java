package com.java2okf.okf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Serialises frontmatter maps to YAML. Insertion order is preserved, which
 * keeps field order stable across runs.
 */
public final class FrontmatterSerializer {

    private final YAMLMapper mapper = new YAMLMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            // Values such as "0.2" or "12" must stay strings.
            .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
            // Long signatures must not be folded onto several lines.
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .build());

    public String serialize(Map<String, Object> frontmatter) {
        try {
            return mapper.writeValueAsString(frontmatter);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Unable to serialise frontmatter", e);
        }
    }
}
