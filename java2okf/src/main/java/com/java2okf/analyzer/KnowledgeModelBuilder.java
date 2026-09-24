package com.java2okf.analyzer;

import com.java2okf.model.AnalysisIssue;
import com.java2okf.model.AnalysisStage;
import com.java2okf.model.EntityIds;
import com.java2okf.model.FileStatistics;
import com.java2okf.model.IssueSeverity;
import com.java2okf.model.JavaPackage;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaRelationship;
import com.java2okf.model.JavaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Merges per-file analysis fragments into the immutable {@link JavaProject}.
 *
 * <p>This is where determinism is established: fragments are processed in file
 * order, members and relationships are sorted, and repeated occurrences of the
 * same relationship are merged. The result is independent of how many worker
 * threads produced the fragments and in which order they finished.</p>
 */
public class KnowledgeModelBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeModelBuilder.class);

    public JavaProject build(String projectName, Collection<FileAnalysisResult> fragments,
                             List<AnalysisIssue> additionalIssues, FileStatistics fileStatistics) {
        List<FileAnalysisResult> ordered = new ArrayList<>(fragments);
        ordered.sort(Comparator.comparing(FileAnalysisResult::file));

        Map<String, JavaType> types = new LinkedHashMap<>();
        Map<String, JavaRelationship> relationships = new TreeMap<>();
        Set<AnalysisIssue> issues = new TreeSet<>(additionalIssues);

        for (FileAnalysisResult fragment : ordered) {
            issues.addAll(fragment.issues());
            Set<String> duplicateNames = new HashSet<>();
            for (JavaType type : fragment.types()) {
                JavaType existing = types.putIfAbsent(type.id(), type);
                if (existing != null) {
                    // The same fully qualified type in two files (e.g. copied sources in
                    // different modules). The first file in path order wins.
                    duplicateNames.add(type.qualifiedName());
                    issues.add(new AnalysisIssue(fragment.file(), AnalysisStage.ANALYSIS, IssueSeverity.WARNING,
                            type.location().lineStart(), "Duplicate type " + type.qualifiedName()
                            + " ignored; already declared in " + existing.location().file()));
                }
            }
            for (JavaRelationship relationship : fragment.relationships()) {
                if (belongsToDuplicate(relationship.sourceId(), duplicateNames)) {
                    continue;
                }
                relationships.merge(relationship.dedupKey(), relationship, JavaRelationship::mergeWith);
            }
        }

        types.values().forEach(JavaType::sortMembers);
        List<JavaRelationship> sortedRelationships = new ArrayList<>(relationships.values());
        sortedRelationships.sort(null);
        LOG.debug("Model built: {} types, {} relationships, {} issues", types.size(), sortedRelationships.size(), issues.size());
        return new JavaProject(projectName, packages(types.values()), types.values(), sortedRelationships,
                new ArrayList<>(issues), fileStatistics);
    }

    private static boolean belongsToDuplicate(String sourceId, Set<String> duplicateNames) {
        if (duplicateNames.isEmpty()) {
            return false;
        }
        String owner = EntityIds.isTypeId(sourceId) ? EntityIds.localPart(sourceId) : EntityIds.declaringTypeName(sourceId);
        return owner != null && duplicateNames.contains(owner);
    }

    private static List<JavaPackage> packages(Collection<JavaType> types) {
        Map<String, List<String>> byPackage = new TreeMap<>();
        for (JavaType type : types) {
            byPackage.computeIfAbsent(type.packageName(), k -> new ArrayList<>()).add(type.id());
        }
        List<JavaPackage> packages = new ArrayList<>();
        byPackage.forEach((name, ids) -> {
            ids.sort(null);
            packages.add(new JavaPackage(EntityIds.packageId(name), name, ids));
        });
        return packages;
    }
}
