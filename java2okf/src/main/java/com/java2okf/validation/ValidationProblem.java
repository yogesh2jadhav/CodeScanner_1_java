package com.java2okf.validation;

import java.util.Comparator;

/**
 * One validation finding.
 *
 * @param severity {@link Severity#ERROR} fails validation, {@link Severity#WARNING} does not
 * @param file     bundle-relative file, or {@code null} for model-level problems
 * @param line     1-based line within the file, or {@code null}
 * @param code     stable machine-readable category, e.g. {@code BROKEN_LINK}
 * @param message  human-readable description
 */
public record ValidationProblem(Severity severity, String file, Integer line, String code, String message)
        implements Comparable<ValidationProblem> {

    public enum Severity { ERROR, WARNING }

    private static final Comparator<ValidationProblem> ORDER = Comparator
            .comparing(ValidationProblem::severity)
            .thenComparing(ValidationProblem::file, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(p -> p.line() == null ? 0 : p.line())
            .thenComparing(ValidationProblem::code)
            .thenComparing(ValidationProblem::message);

    @Override
    public int compareTo(ValidationProblem other) {
        return ORDER.compare(this, other);
    }

    public String format() {
        String location = file == null ? "" : file + (line == null ? "" : ":" + line) + ": ";
        return severity + " " + code + " " + location + message;
    }
}
