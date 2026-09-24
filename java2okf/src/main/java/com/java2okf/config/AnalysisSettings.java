package com.java2okf.config;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code analysis:} section of {@code java2okf.yaml}. Each flag switches one
 * family of extracted relationships on or off; the core declarations
 * (types, methods, fields) are always analysed.
 */
public class AnalysisSettings {

    private boolean includeTests = false;
    private boolean includeGeneratedSources = false;
    private boolean resolveSymbols = true;
    private boolean analyzeMethodCalls = true;
    private boolean analyzeDependencies = true;
    private boolean analyzeInheritance = true;
    private boolean analyzeAnnotations = true;
    private boolean analyzeFields = true;
    private boolean analyzeConstructors = true;
    private boolean analyzeLocalVariables = true;
    private boolean analyzeObjectCreation = true;

    /**
     * Optional jar files that are handed to the symbol solver so references to
     * third-party libraries can be resolved. The jars are only read, never executed.
     */
    private List<String> classpath = new ArrayList<>();

    public boolean isIncludeTests() {
        return includeTests;
    }

    public void setIncludeTests(boolean includeTests) {
        this.includeTests = includeTests;
    }

    public boolean isIncludeGeneratedSources() {
        return includeGeneratedSources;
    }

    public void setIncludeGeneratedSources(boolean includeGeneratedSources) {
        this.includeGeneratedSources = includeGeneratedSources;
    }

    public boolean isResolveSymbols() {
        return resolveSymbols;
    }

    public void setResolveSymbols(boolean resolveSymbols) {
        this.resolveSymbols = resolveSymbols;
    }

    public boolean isAnalyzeMethodCalls() {
        return analyzeMethodCalls;
    }

    public void setAnalyzeMethodCalls(boolean analyzeMethodCalls) {
        this.analyzeMethodCalls = analyzeMethodCalls;
    }

    public boolean isAnalyzeDependencies() {
        return analyzeDependencies;
    }

    public void setAnalyzeDependencies(boolean analyzeDependencies) {
        this.analyzeDependencies = analyzeDependencies;
    }

    public boolean isAnalyzeInheritance() {
        return analyzeInheritance;
    }

    public void setAnalyzeInheritance(boolean analyzeInheritance) {
        this.analyzeInheritance = analyzeInheritance;
    }

    public boolean isAnalyzeAnnotations() {
        return analyzeAnnotations;
    }

    public void setAnalyzeAnnotations(boolean analyzeAnnotations) {
        this.analyzeAnnotations = analyzeAnnotations;
    }

    public boolean isAnalyzeFields() {
        return analyzeFields;
    }

    public void setAnalyzeFields(boolean analyzeFields) {
        this.analyzeFields = analyzeFields;
    }

    public boolean isAnalyzeConstructors() {
        return analyzeConstructors;
    }

    public void setAnalyzeConstructors(boolean analyzeConstructors) {
        this.analyzeConstructors = analyzeConstructors;
    }

    public boolean isAnalyzeLocalVariables() {
        return analyzeLocalVariables;
    }

    public void setAnalyzeLocalVariables(boolean analyzeLocalVariables) {
        this.analyzeLocalVariables = analyzeLocalVariables;
    }

    public boolean isAnalyzeObjectCreation() {
        return analyzeObjectCreation;
    }

    public void setAnalyzeObjectCreation(boolean analyzeObjectCreation) {
        this.analyzeObjectCreation = analyzeObjectCreation;
    }

    public List<String> getClasspath() {
        return classpath;
    }

    public void setClasspath(List<String> classpath) {
        this.classpath = classpath == null ? new ArrayList<>() : new ArrayList<>(classpath);
    }
}
