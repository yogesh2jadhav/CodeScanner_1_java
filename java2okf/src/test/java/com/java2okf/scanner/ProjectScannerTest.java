package com.java2okf.scanner;

import com.java2okf.config.AnalysisSettings;
import com.java2okf.config.ScannerSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.java2okf.testutil.TestProjects.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScannerTest {

    @TempDir
    Path project;

    private List<String> scan(ScannerSettings scanner, AnalysisSettings analysis) {
        return new ProjectScanner(scanner, analysis).scan(project).files().stream()
                .map(SourceFileInfo::relativePath)
                .toList();
    }

    private List<String> scanWithDefaults() {
        return scan(new ScannerSettings(), new AnalysisSettings());
    }

    @Test
    void discoversJavaFilesRecursively() {
        write(project, "src/main/java/com/a/A.java", "package com.a; class A {}");
        write(project, "src/main/java/com/a/b/B.java", "package com.a.b; class B {}");
        write(project, "src/main/java/com/a/readme.txt", "not java");

        assertEquals(List.of("src/main/java/com/a/A.java", "src/main/java/com/a/b/B.java"), scanWithDefaults());
    }

    @Test
    void excludesTargetBuildOutGitAndGeneratedByDefault() {
        write(project, "src/main/java/p/Keep.java", "package p; class Keep {}");
        write(project, "target/classes/p/T.java", "package p; class T {}");
        write(project, "build/p/B.java", "package p; class B {}");
        write(project, "out/p/O.java", "package p; class O {}");
        write(project, ".git/p/G.java", "package p; class G {}");
        write(project, "src/main/generated/p/Gen.java", "package p; class Gen {}");

        assertEquals(List.of("src/main/java/p/Keep.java"), scanWithDefaults());
    }

    @Test
    void respectsConfiguredExcludesAndExtensions() {
        write(project, "src/main/java/p/A.java", "package p; class A {}");
        write(project, "legacy/p/L.java", "package p; class L {}");
        write(project, "target/p/T.java", "package p; class T {}");
        write(project, "src/main/java/p/S.jav", "package p; class S {}");

        ScannerSettings scanner = new ScannerSettings();
        scanner.setExcludeDirectories(List.of("legacy"));
        scanner.setExtensions(List.of(".java", ".jav"));

        assertEquals(List.of("src/main/java/p/A.java", "src/main/java/p/S.jav", "target/p/T.java"),
                scan(scanner, new AnalysisSettings()));
    }

    @Test
    void excludesTestSourcesUnlessRequested() {
        write(project, "src/main/java/p/A.java", "package p; class A {}");
        write(project, "src/test/java/p/ATest.java", "package p; class ATest {}");
        write(project, "src/integrationTest/java/p/AIT.java", "package p; class AIT {}");

        assertEquals(List.of("src/main/java/p/A.java"), scanWithDefaults());

        AnalysisSettings analysis = new AnalysisSettings();
        analysis.setIncludeTests(true);
        assertEquals(3, scan(new ScannerSettings(), analysis).size());
    }

    @Test
    void includeGeneratedSourcesLiftsGeneratedExclusion() {
        write(project, "src/main/java/p/A.java", "package p; class A {}");
        write(project, "src/main/generated/p/Gen.java", "package p; class Gen {}");
        AnalysisSettings analysis = new AnalysisSettings();
        analysis.setIncludeGeneratedSources(true);
        assertEquals(2, scan(new ScannerSettings(), analysis).size());
    }

    @Test
    void orderingIsDeterministicAndSorted() {
        write(project, "src/main/java/z/Z.java", "package z; class Z {}");
        write(project, "src/main/java/a/A.java", "package a; class A {}");
        write(project, "src/main/java/m/M.java", "package m; class M {}");

        List<String> first = scanWithDefaults();
        List<String> second = scanWithDefaults();
        assertEquals(first, second);
        assertEquals(List.of("src/main/java/a/A.java", "src/main/java/m/M.java", "src/main/java/z/Z.java"), first);
    }

    @Test
    void derivesPackageAndSourceRoot() {
        write(project, "src/main/java/com/x/Y.java", """
                /* package fake.one; */
                // package fake.two;
                package com.x;
                class Y {}
                """);
        write(project, "misplaced/Z.java", "package com.other; class Z {}");

        SourceFileInventory inventory = new ProjectScanner(new ScannerSettings(), new AnalysisSettings()).scan(project);
        SourceFileInfo y = inventory.files().stream().filter(f -> f.fileName().equals("Y.java")).findFirst().orElseThrow();
        assertEquals("com.x", y.packageName());
        assertEquals("com/x", y.packagePath());
        assertEquals(project.toAbsolutePath().normalize().resolve("src/main/java"), y.sourceRoot());

        SourceFileInfo z = inventory.files().stream().filter(f -> f.fileName().equals("Z.java")).findFirst().orElseThrow();
        assertEquals(project.toAbsolutePath().normalize().resolve("misplaced"), z.sourceRoot(),
                "mismatched layouts fall back to the file's directory");
        assertTrue(inventory.sourceRoots().contains(y.sourceRoot()));
    }

    @Test
    void rejectsMissingDirectory() {
        ProjectScanner scanner = new ProjectScanner(new ScannerSettings(), new AnalysisSettings());
        assertThrows(IllegalArgumentException.class, () -> scanner.scan(project.resolve("missing")));
    }
}
