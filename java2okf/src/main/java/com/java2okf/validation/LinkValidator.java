package com.java2okf.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks every Markdown link of a document: relative targets must exist inside
 * the bundle, absolute filesystem links are rejected.
 */
final class LinkValidator {

    // [text](target) or [text](target "title"); escaped brackets are allowed in the text.
    private static final Pattern LINK = Pattern.compile("\\[((?:\\\\.|[^\\]\\\\])*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");

    /** Validates one line of Markdown (code spans must already be removed). Returns the number of links. */
    int validateLine(Path bundleRoot, BundleDocument document, String line, int lineNumber, ValidationReport report,
                     boolean[] valid) {
        Matcher matcher = LINK.matcher(line);
        int links = 0;
        while (matcher.find()) {
            links++;
            String target = matcher.group(2);
            if (target.startsWith("http://") || target.startsWith("https://") || target.startsWith("mailto:")
                    || target.startsWith("#")) {
                continue;
            }
            if (target.startsWith("/") || target.startsWith("file:") || WINDOWS_ABSOLUTE.matcher(target).matches()) {
                report.error(document.path(), lineNumber, "ABSOLUTE_LINK", "Absolute link not allowed: " + target);
                valid[0] = false;
                continue;
            }
            String pathPart = target.replaceAll("[#?].*$", "");
            if (pathPart.isEmpty()) {
                continue;
            }
            Path documentDir = bundleRoot.resolve(document.path()).getParent();
            Path resolved = documentDir.resolve(pathPart).normalize();
            if (!resolved.startsWith(bundleRoot)) {
                report.countBrokenLink();
                report.error(document.path(), lineNumber, "LINK_OUTSIDE_BUNDLE", "Link leaves the bundle: " + target);
                valid[0] = false;
            } else if (!Files.exists(resolved)) {
                report.countBrokenLink();
                report.error(document.path(), lineNumber, "BROKEN_LINK", "Link target does not exist: " + target);
                valid[0] = false;
            }
        }
        return links;
    }

    static boolean containsLink(String line) {
        return LINK.matcher(line).find();
    }
}
