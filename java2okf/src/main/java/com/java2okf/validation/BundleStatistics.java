package com.java2okf.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2okf.okf.MetadataGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Statistics read back from a bundle: run metadata plus document counts.
 *
 * @param metadata        parsed {@code _metadata/analysis.json}, if present
 * @param documentsByType number of Markdown documents per frontmatter {@code type}
 * @param documents       total number of Markdown documents
 * @param brokenLinks     broken links found by the validator
 */
public record BundleStatistics(Optional<JsonNode> metadata, Map<String, Integer> documentsByType, int documents,
                               int brokenLinks) {

    private static final Pattern TYPE_LINE = Pattern.compile("(?m)^type:\\s*\"?([^\"\\n]+)\"?\\s*$");

    public static BundleStatistics collect(Path bundle) {
        Path root = bundle.toAbsolutePath().normalize();
        Optional<JsonNode> metadata = Optional.empty();
        Path analysis = root.resolve(MetadataGenerator.ANALYSIS_JSON);
        try {
            if (Files.isRegularFile(analysis)) {
                metadata = Optional.of(new ObjectMapper().readTree(analysis.toFile()));
            }
            Map<String, Integer> byType = new TreeMap<>();
            int documents = 0;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                    documents++;
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    int end = text.indexOf("\n---", 3);
                    Matcher matcher = TYPE_LINE.matcher(end > 0 ? text.substring(0, end) : "");
                    byType.merge(matcher.find() ? matcher.group(1).trim() : "(none)", 1, Integer::sum);
                }
            }
            int brokenLinks = new OkfValidator().validate(root).brokenLinks();
            return new BundleStatistics(metadata, byType, documents, brokenLinks);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read bundle " + root, e);
        }
    }
}
