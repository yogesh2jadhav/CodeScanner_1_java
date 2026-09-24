package com.java2okf.analyzer;

import com.github.javaparser.ast.Node;

/**
 * Visitor callback for nodes inside executable code. All body analysers share a
 * single tree walk per code block (see {@link ClassAnalyzer}), which keeps large
 * method bodies from being traversed once per relationship family.
 */
interface BodyAnalyzer {

    /**
     * @param ownerId entity the code is attributed to (method, constructor, or type for initialisers)
     * @param node    current node
     */
    void visit(FileAnalysisContext ctx, String ownerId, Node node);
}
