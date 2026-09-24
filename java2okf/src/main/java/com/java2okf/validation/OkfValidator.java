package com.java2okf.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2okf.okf.MetadataGenerator;
import com.java2okf.okf.OkfDocumentType;
import com.java2okf.okf.OkfMarkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validates an OKF bundle on disk. It works on the files alone (no knowledge
 * model), so it can check bundles produced earlier or edited by hand.
 *
 * <ul>
 *   <li>File: UTF-8, frontmatter present and valid YAML, {@code type} present, Markdown structure</li>
 *   <li>Links: relative targets exist inside the bundle, no absolute filesystem links</li>
 *   <li>Structure: reserved {@code index.md}/{@code log.md}, concept locations, unique IDs</li>
 *   <li>Java knowledge: unique class and method IDs, distinguishable overloads,
 *       every unlinked relationship explicitly marked</li>
 * </ul>
 */
public class OkfValidator {

    private static final Logger LOG = LoggerFactory.getLogger(OkfValidator.class);

    /** Sections whose bullets are relationships and must be links or carry a marker. */
    static final Set<String> RELATIONSHIP_SECTIONS = Set.of(
            "Enclosing Type", "Annotations", "Extends", "Inheritance", "Implements", "Subtypes",
            "Extended / Implemented By", "Nested Types", "Initializer Calls", "Initializer Instantiations", "Imports",
            "Dependencies", "Used By", "Called By", "Overrides", "Overridden By", "Calls", "Uses", "Instantiates",
            "References", "Referenced By", "Throws", "Dependents", "Classes", "Interfaces", "Enums", "Records");

    private static final Pattern INLINE_CODE = Pattern.compile("(`+)(?:(?!\\1).)*?\\1");
    private static final Pattern MARKER = Pattern.compile("— (" + OkfMarkers.UNRESOLVED + "|" + OkfMarkers.AMBIGUOUS + ")\\b");

    private final FrontmatterValidator frontmatterValidator = new FrontmatterValidator();
    private final LinkValidator linkValidator = new LinkValidator();

    /**
     * Validates the bundle rooted at {@code bundleDirectory}.
     *
     * @throws IllegalArgumentException if the directory does not exist
     */
    public ValidationReport validate(Path bundleDirectory) {
        Path root = bundleDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Bundle directory does not exist: " + bundleDirectory);
        }
        ValidationReport report = new ValidationReport();
        Map<String, String> ids = new HashMap<>();
        Map<String, String> signatures = new HashMap<>();
        List<Path> files = listFiles(root);
        if (!Files.isRegularFile(root.resolve("index.md"))) {
            report.warning(null, null, "MISSING_INDEX", "Bundle has no root index.md");
        }
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (relative.endsWith(".md")) {
                validateDocument(root, file, relative, report, ids, signatures);
            } else if (relative.endsWith(".json")) {
                validateJson(file, relative, report);
            }
        }
        if (!Files.isRegularFile(root.resolve(MetadataGenerator.ANALYSIS_JSON))) {
            report.warning(null, null, "MISSING_METADATA", "Bundle has no " + MetadataGenerator.ANALYSIS_JSON);
        }
        LOG.debug("Validated {} documents: {} errors, {} warnings", report.documents(), report.errorCount(), report.warningCount());
        return report;
    }

    private void validateDocument(Path root, Path file, String relative, ValidationReport report,
                                  Map<String, String> ids, Map<String, String> signatures) {
        boolean[] valid = {true};
        String text;
        try {
            text = decodeUtf8(Files.readAllBytes(file));
        } catch (CharacterCodingException e) {
            report.error(relative, null, "NOT_UTF8", "Document is not valid UTF-8");
            report.countDocument(false);
            return;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + file, e);
        }
        BundleDocument document = frontmatterValidator.parse(relative, text, report, valid);
        checkIdentity(document, report, ids, signatures, valid);
        checkBody(root, document, report, valid);
        report.countDocument(valid[0]);
    }

    /** Unique IDs across the bundle, and unique signatures per declaring class (overloads). */
    private void checkIdentity(BundleDocument document, ValidationReport report, Map<String, String> ids,
                               Map<String, String> signatures, boolean[] valid) {
        String id = document.stringField("id");
        if (id != null) {
            String previous = ids.putIfAbsent(id, document.path());
            if (previous != null) {
                report.countDuplicateId();
                report.error(document.path(), 1, "DUPLICATE_ID", "ID " + id + " is also used by " + previous);
                valid[0] = false;
            }
        }
        OkfDocumentType type = OkfDocumentType.fromValue(String.valueOf(document.stringField("type")));
        if (type == OkfDocumentType.JAVA_METHOD || type == OkfDocumentType.JAVA_CONSTRUCTOR) {
            String declaringClass = document.javaField("declaringClass");
            String signature = document.javaField("signature");
            if (declaringClass == null || signature == null) {
                report.error(document.path(), 1, "MISSING_SIGNATURE", "Method document lacks java.declaringClass or java.signature");
                valid[0] = false;
                return;
            }
            String key = type + ":" + declaringClass + "#" + signature;
            String previous = signatures.putIfAbsent(key, document.path());
            if (previous != null) {
                report.error(document.path(), 1, "INDISTINGUISHABLE_OVERLOAD",
                        "Signature " + signature + " of " + declaringClass + " is also documented by " + previous);
                valid[0] = false;
            }
        }
    }

    /** Checks code fences, links, relationship markers, and counts unresolved references. */
    private void checkBody(Path root, BundleDocument document, ValidationReport report, boolean[] valid) {
        String[] lines = document.body().split("\n", -1);
        boolean inFence = false;
        int fenceLine = 0;
        String section = null;
        int unresolved = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = document.bodyStartLine() + i;
            if (line.startsWith("```")) {
                inFence = !inFence;
                fenceLine = lineNumber;
                continue;
            }
            if (inFence) {
                continue;
            }
            if (line.startsWith("## ")) {
                section = line.substring(3).trim();
            } else if (line.startsWith("# ")) {
                section = null;
            }
            String withoutCode = INLINE_CODE.matcher(line).replaceAll("``");
            linkValidator.validateLine(root, document, withoutCode, lineNumber, report, valid);
            if (MARKER.matcher(withoutCode).find()) {
                unresolved++;
            }
            if (section != null && RELATIONSHIP_SECTIONS.contains(section) && isBullet(line)
                    && !LinkValidator.containsLink(withoutCode) && OkfMarkers.ALL.stream().noneMatch(withoutCode::contains)) {
                report.countUnmarkedRelationship();
                report.error(document.path(), lineNumber, "UNMARKED_RELATIONSHIP",
                        "Relationship in '" + section + "' is neither a link nor marked: " + line.trim());
                valid[0] = false;
            }
        }
        if (inFence) {
            report.error(document.path(), fenceLine, "UNTERMINATED_CODE_BLOCK", "Code block is not closed");
            valid[0] = false;
        }
        report.countUnresolved(unresolved);
    }

    private static boolean isBullet(String line) {
        return line.startsWith("- ") || line.startsWith("  - ");
    }

    private void validateJson(Path file, String relative, ValidationReport report) {
        try {
            new ObjectMapper().readTree(file.toFile());
        } catch (IOException e) {
            report.error(relative, null, "INVALID_JSON", "Malformed JSON: " + e.getMessage());
        }
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static List<Path> listFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to list " + root, e);
        }
    }
}
