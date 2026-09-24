package com.java2okf.analyzer;

import com.github.javaparser.ast.Node;

/**
 * A piece of executable code together with the entity it is attributed to.
 * Method bodies belong to their method; field initialisers, initializer blocks,
 * and enum constant bodies belong to the declaring type.
 */
record CodeBlock(String ownerId, Node node) {
}
