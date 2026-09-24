package com.java2okf.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.java2okf.scanner.SourceFileInfo;

import java.util.List;
import java.util.Optional;

/**
 * Result of parsing one file: either a compilation unit or a list of problems.
 * A failed file is reported and skipped; it never aborts the whole analysis.
 */
public final class ParseOutcome {

    private final SourceFileInfo file;
    private final CompilationUnit unit;
    private final List<String> problems;

    private ParseOutcome(SourceFileInfo file, CompilationUnit unit, List<String> problems) {
        this.file = file;
        this.unit = unit;
        this.problems = List.copyOf(problems);
    }

    public static ParseOutcome success(SourceFileInfo file, CompilationUnit unit) {
        return new ParseOutcome(file, unit, List.of());
    }

    public static ParseOutcome failure(SourceFileInfo file, List<String> problems) {
        return new ParseOutcome(file, null, problems.isEmpty() ? List.of("Unknown parse failure") : problems);
    }

    public SourceFileInfo file() {
        return file;
    }

    public boolean isSuccess() {
        return unit != null;
    }

    public Optional<ParsedSourceFile> parsed() {
        return isSuccess() ? Optional.of(new ParsedSourceFile(file, unit)) : Optional.empty();
    }

    public List<String> problems() {
        return problems;
    }
}
