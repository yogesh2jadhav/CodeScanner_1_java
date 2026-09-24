package com.java2okf.analyzer;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.java2okf.model.JavaExecutable;
import com.java2okf.resolver.ExecutableSignature;

/**
 * Links a modelled method or constructor to its AST declaration for the
 * analysers that run after {@link MethodAnalyzer} (inheritance and bodies).
 *
 * @param declaration the AST node ({@link CallableDeclaration}, compact constructor, or annotation member)
 */
record AnalyzedExecutable(BodyDeclaration<?> declaration, JavaExecutable model, ExecutableSignature signature) {
}
