package com.java2okf.graph;

import com.java2okf.model.DependencyCategory;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Aggregated dependency of one type on another.
 *
 * @param targetTypeId  entity ID of the target type
 * @param qualifiedName qualified name of the target type
 * @param categories    every reason for the dependency
 * @param internal      true if the target type is declared in the analysed sources
 */
public record TypeDependency(String targetTypeId, String qualifiedName, SortedSet<DependencyCategory> categories,
                             boolean internal) {

    public TypeDependency {
        categories = Collections.unmodifiableSortedSet(new TreeSet<>(categories));
    }
}
