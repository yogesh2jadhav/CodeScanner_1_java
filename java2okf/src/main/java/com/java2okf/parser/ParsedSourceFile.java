package com.java2okf.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.java2okf.scanner.SourceFileInfo;

/**
 * A successfully parsed source file. The AST never leaves the analysis stage;
 * later stages only see the knowledge model.
 */
public record ParsedSourceFile(SourceFileInfo file, CompilationUnit unit) {
}
