package com.java2okf.parser;

import com.java2okf.scanner.SourceFileInfo;

/**
 * Turns one source file into an AST. Implementations must never throw for
 * malformed input; problems are reported through {@link ParseOutcome}.
 */
public interface JavaSourceParser {

    ParseOutcome parse(SourceFileInfo file);
}
