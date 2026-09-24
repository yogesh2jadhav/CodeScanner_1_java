package com.java2okf.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated validation outcome with the counters shown by {@code java2okf validate}.
 */
public final class ValidationReport {

    private int documents;
    private int validDocuments;
    private int brokenLinks;
    private int missingTypeFields;
    private int invalidFrontmatter;
    private int unresolvedJavaReferences;
    private int duplicateIds;
    private int unmarkedRelationships;
    private final List<ValidationProblem> problems = new ArrayList<>();

    public int documents() {
        return documents;
    }

    public int validDocuments() {
        return validDocuments;
    }

    public int brokenLinks() {
        return brokenLinks;
    }

    public int missingTypeFields() {
        return missingTypeFields;
    }

    public int invalidFrontmatter() {
        return invalidFrontmatter;
    }

    public int unresolvedJavaReferences() {
        return unresolvedJavaReferences;
    }

    public int duplicateIds() {
        return duplicateIds;
    }

    public int unmarkedRelationships() {
        return unmarkedRelationships;
    }

    /** Problems sorted by severity, file, and line. */
    public List<ValidationProblem> problems() {
        List<ValidationProblem> sorted = new ArrayList<>(problems);
        Collections.sort(sorted);
        return sorted;
    }

    public long errorCount() {
        return problems.stream().filter(p -> p.severity() == ValidationProblem.Severity.ERROR).count();
    }

    public long warningCount() {
        return problems.stream().filter(p -> p.severity() == ValidationProblem.Severity.WARNING).count();
    }

    /** Validation passes when there is no error-level problem. Warnings never fail validation. */
    public boolean passed() {
        return errorCount() == 0;
    }

    void countDocument(boolean valid) {
        documents++;
        if (valid) {
            validDocuments++;
        }
    }

    void countBrokenLink() {
        brokenLinks++;
    }

    void countMissingType() {
        missingTypeFields++;
    }

    void countInvalidFrontmatter() {
        invalidFrontmatter++;
    }

    void countUnresolved(int count) {
        unresolvedJavaReferences += count;
    }

    void countDuplicateId() {
        duplicateIds++;
    }

    void countUnmarkedRelationship() {
        unmarkedRelationships++;
    }

    void error(String file, Integer line, String code, String message) {
        problems.add(new ValidationProblem(ValidationProblem.Severity.ERROR, file, line, code, message));
    }

    void warning(String file, Integer line, String code, String message) {
        problems.add(new ValidationProblem(ValidationProblem.Severity.WARNING, file, line, code, message));
    }

    /** Returns a new report combining this one with {@code other}. */
    public ValidationReport merge(ValidationReport other) {
        ValidationReport merged = new ValidationReport();
        merged.documents = documents + other.documents;
        merged.validDocuments = validDocuments + other.validDocuments;
        merged.brokenLinks = brokenLinks + other.brokenLinks;
        merged.missingTypeFields = missingTypeFields + other.missingTypeFields;
        merged.invalidFrontmatter = invalidFrontmatter + other.invalidFrontmatter;
        merged.unresolvedJavaReferences = unresolvedJavaReferences + other.unresolvedJavaReferences;
        merged.duplicateIds = duplicateIds + other.duplicateIds;
        merged.unmarkedRelationships = unmarkedRelationships + other.unmarkedRelationships;
        merged.problems.addAll(problems);
        merged.problems.addAll(other.problems);
        return merged;
    }

    /**
     * Formats the report for the console.
     *
     * @param maxProblems maximum number of individual problems to list
     */
    public String format(int maxProblems) {
        StringBuilder out = new StringBuilder();
        out.append("OKF Validation\n");
        out.append("--------------\n\n");
        row(out, "Documents:", documents);
        row(out, "Valid documents:", validDocuments);
        row(out, "Broken links:", brokenLinks);
        row(out, "Missing type fields:", missingTypeFields);
        row(out, "Invalid frontmatter:", invalidFrontmatter);
        row(out, "Duplicate IDs:", duplicateIds);
        row(out, "Unmarked relationships:", unmarkedRelationships);
        row(out, "Unresolved Java refs:", unresolvedJavaReferences);
        row(out, "Errors:", (int) errorCount());
        row(out, "Warnings:", (int) warningCount());
        List<ValidationProblem> sorted = problems();
        if (!sorted.isEmpty()) {
            out.append('\n');
            sorted.stream().limit(maxProblems).forEach(p -> out.append(p.format()).append('\n'));
            if (sorted.size() > maxProblems) {
                out.append("... ").append(sorted.size() - maxProblems).append(" more\n");
            }
        }
        out.append("\nSTATUS: ").append(passed() ? "PASS" : "FAIL").append('\n');
        return out.toString();
    }

    private static void row(StringBuilder out, String label, int value) {
        out.append(String.format("%-24s%6d%n", label, value));
    }
}
