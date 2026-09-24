package com.java2okf.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.java2okf.okf.OkfDocumentType;

import java.util.Map;

/**
 * Splits a document into frontmatter and body and checks the frontmatter:
 * presence, YAML syntax, the mandatory {@code type} field, and concept location.
 */
final class FrontmatterValidator {

    private static final String DELIMITER = "---";

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    /** Parses {@code text}; problems are recorded in {@code report}. Returns the parsed document. */
    BundleDocument parse(String path, String text, ValidationReport report, boolean[] valid) {
        String normalized = text.replace("\r\n", "\n");
        if (!normalized.startsWith(DELIMITER + "\n")) {
            if (!isReserved(path)) {
                report.countInvalidFrontmatter();
                report.error(path, 1, "MISSING_FRONTMATTER", "Document has no YAML frontmatter");
                valid[0] = false;
            }
            return new BundleDocument(path, null, normalized, 1);
        }
        int end = normalized.indexOf("\n" + DELIMITER + "\n", DELIMITER.length());
        if (end < 0 && normalized.endsWith("\n" + DELIMITER)) {
            end = normalized.length() - DELIMITER.length() - 1;
        }
        if (end < 0) {
            report.countInvalidFrontmatter();
            report.error(path, 1, "INVALID_FRONTMATTER", "Frontmatter is not terminated by '---'");
            valid[0] = false;
            return new BundleDocument(path, null, normalized, 1);
        }
        String yamlText = normalized.substring(DELIMITER.length() + 1, end + 1);
        int bodyStart = Math.min(normalized.length(), end + DELIMITER.length() + 2);
        int bodyStartLine = (int) normalized.substring(0, bodyStart).chars().filter(c -> c == '\n').count() + 1;
        String body = normalized.substring(bodyStart);
        Map<String, Object> frontmatter;
        try {
            frontmatter = yaml.readValue(yamlText, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            report.countInvalidFrontmatter();
            report.error(path, 1, "INVALID_FRONTMATTER", "Malformed YAML: " + e.getOriginalMessage());
            valid[0] = false;
            return new BundleDocument(path, null, body, bodyStartLine);
        }
        if (frontmatter == null) {
            report.countInvalidFrontmatter();
            report.error(path, 1, "INVALID_FRONTMATTER", "Frontmatter is empty");
            valid[0] = false;
            return new BundleDocument(path, null, body, bodyStartLine);
        }
        BundleDocument document = new BundleDocument(path, frontmatter, body, bodyStartLine);
        checkType(document, report, valid);
        return document;
    }

    private void checkType(BundleDocument document, ValidationReport report, boolean[] valid) {
        String type = document.stringField("type");
        if (type == null || type.isBlank()) {
            if (!document.isReserved()) {
                report.countMissingType();
                report.error(document.path(), 1, "MISSING_TYPE", "Frontmatter has no 'type' field");
                valid[0] = false;
            }
            return;
        }
        OkfDocumentType known = OkfDocumentType.fromValue(type);
        if (known == null) {
            report.warning(document.path(), 1, "UNKNOWN_TYPE", "Unknown document type '" + type + "'");
            return;
        }
        if (document.isReserved()) {
            if (known.directory() != null) {
                report.error(document.path(), 1, "RESERVED_NAME",
                        "Reserved file name used for a " + type + " concept document");
                valid[0] = false;
            }
            return;
        }
        if (known.directory() == null) {
            report.error(document.path(), 1, "INVALID_LOCATION", type + " is only valid for index.md / log.md");
            valid[0] = false;
        } else if (!known.directory().equals(document.directory())) {
            report.error(document.path(), 1, "INVALID_LOCATION",
                    type + " documents belong in '" + known.directory() + "/', found in '" + document.directory() + "/'");
            valid[0] = false;
        }
        if (document.stringField("id") == null) {
            report.error(document.path(), 1, "MISSING_ID", "Concept document has no 'id' field");
            valid[0] = false;
        }
    }

    private static boolean isReserved(String path) {
        return path.endsWith("index.md") && (path.equals("index.md") || path.endsWith("/index.md"))
                || path.equals("log.md") || path.endsWith("/log.md");
    }
}
