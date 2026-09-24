package com.java2okf.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.java2okf.scanner.SourceFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * {@link JavaSourceParser} backed by JavaParser.
 *
 * <p>A {@link JavaParser} instance is <em>not</em> thread-safe. Each analysis
 * worker therefore owns its own engine; see {@code AnalysisWorker}.</p>
 */
public class JavaParserEngine implements JavaSourceParser {

    private static final Logger LOG = LoggerFactory.getLogger(JavaParserEngine.class);

    /** Language level used for parsing; matches the Java 21 LTS baseline of the tool. */
    public static final ParserConfiguration.LanguageLevel LANGUAGE_LEVEL = ParserConfiguration.LanguageLevel.JAVA_21;

    /** Only the first few problems are kept; later ones are usually follow-up errors. */
    private static final int MAX_REPORTED_PROBLEMS = 5;

    private final JavaParser parser;

    /** Creates an engine without symbol resolution (syntax only). */
    public JavaParserEngine() {
        this(newConfiguration());
    }

    /** Creates an engine with a caller-supplied configuration, e.g. one carrying a symbol resolver. */
    public JavaParserEngine(ParserConfiguration configuration) {
        this.parser = new JavaParser(configuration);
    }

    /** Returns a parser configuration with the tool's standard settings. */
    public static ParserConfiguration newConfiguration() {
        return new ParserConfiguration()
                .setLanguageLevel(LANGUAGE_LEVEL)
                .setCharacterEncoding(StandardCharsets.UTF_8)
                // Comments are not needed for structure extraction and cost memory on large projects.
                .setAttributeComments(false);
    }

    @Override
    public ParseOutcome parse(SourceFileInfo file) {
        LOG.debug("Parsing {}", file.relativePath());
        try {
            ParseResult<CompilationUnit> result = parser.parse(file.absolutePath());
            if (result.isSuccessful() && result.getResult().isPresent()) {
                return ParseOutcome.success(file, result.getResult().get());
            }
            // JavaParser can recover a partial AST from broken input; analysing it would
            // produce facts about code that does not compile, so the file is rejected.
            List<String> problems = result.getProblems().stream()
                    .limit(MAX_REPORTED_PROBLEMS)
                    .map(JavaParserEngine::describe)
                    .toList();
            return ParseOutcome.failure(file, problems);
        } catch (IOException e) {
            return ParseOutcome.failure(file, List.of("I/O error: " + e.getMessage()));
        } catch (RuntimeException | StackOverflowError e) {
            // Deeply nested expressions can overflow the recursive-descent parser.
            return ParseOutcome.failure(file, List.of(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private static String describe(Problem problem) {
        String location = problem.getLocation()
                .flatMap(tokenRange -> tokenRange.getBegin().getRange())
                .map(range -> "line " + range.begin.line + ", column " + range.begin.column + ": ")
                .orElse("");
        // Problem messages can span several lines (expected-token lists); keep the first.
        String message = problem.getMessage().lines().findFirst().orElse(problem.getMessage());
        return location + message;
    }
}
