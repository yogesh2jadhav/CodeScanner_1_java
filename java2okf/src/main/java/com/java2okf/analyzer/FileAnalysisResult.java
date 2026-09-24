package com.java2okf.analyzer;

import com.java2okf.model.AnalysisIssue;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.JavaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model fragment produced by analysing a single source file. Fragments are
 * merged into the project model by {@link KnowledgeModelBuilder}, which makes
 * per-file analysis independent and therefore parallelisable.
 */
public final class FileAnalysisResult {

    private final String file;
    private final List<JavaType> types = new ArrayList<>();
    private final List<JavaRelationship> relationships = new ArrayList<>();
    private final List<AnalysisIssue> issues = new ArrayList<>();

    public FileAnalysisResult(String file) {
        this.file = file;
    }

    public String file() {
        return file;
    }

    public List<JavaType> types() {
        return Collections.unmodifiableList(types);
    }

    public List<JavaRelationship> relationships() {
        return Collections.unmodifiableList(relationships);
    }

    public List<AnalysisIssue> issues() {
        return Collections.unmodifiableList(issues);
    }

    void addType(JavaType type) {
        types.add(type);
    }

    void addRelationship(JavaRelationship relationship) {
        relationships.add(relationship);
    }

    public void addIssue(AnalysisIssue issue) {
        issues.add(issue);
    }

    /** Drops all extracted facts, keeping only issues; used when a file's analysis fails midway. */
    void discardFacts() {
        types.clear();
        relationships.clear();
    }
}
