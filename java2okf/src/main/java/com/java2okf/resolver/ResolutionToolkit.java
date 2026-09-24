package com.java2okf.resolver;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.java2okf.parser.JavaParserEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A parser and a resolver that share one private symbol-solver instance.
 *
 * <p>JavaSymbolSolver resolves a node through the resolver stored in the node's
 * compilation unit, and its type solvers cache parsed files without
 * synchronisation. Pairing each parser with its own solver, and confining each
 * toolkit to one worker, gives thread safety without global locks.</p>
 */
public record ResolutionToolkit(JavaParserEngine parser, SymbolResolver resolver) {

    private static final Logger LOG = LoggerFactory.getLogger(ResolutionToolkit.class);

    /**
     * Creates a toolkit.
     *
     * @param resolveSymbols false yields a syntax-only parser and a {@link DisabledSymbolResolver}
     * @param sourceRoots    roots of the analysed sources, used to resolve cross-file references
     * @param classpathJars  optional library jars, read (never executed) to resolve external types
     */
    public static ResolutionToolkit create(boolean resolveSymbols, List<Path> sourceRoots, List<Path> classpathJars) {
        if (!resolveSymbols) {
            return new ResolutionToolkit(new JavaParserEngine(), new DisabledSymbolResolver());
        }
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration configuration = JavaParserEngine.newConfiguration().setSymbolResolver(symbolSolver);

        // Project sources first, then libraries, then the JDK.
        for (Path root : sourceRoots) {
            typeSolver.add(new JavaParserTypeSolver(root, configuration));
        }
        for (Path jar : classpathJars) {
            try {
                typeSolver.add(new JarTypeSolver(jar));
            } catch (IOException | RuntimeException e) {
                LOG.warn("Ignoring classpath entry {}: {}", jar, e.getMessage());
            }
        }
        // JRE_ONLY: the tool's own dependencies must never resolve references of the analysed project.
        typeSolver.add(new ReflectionTypeSolver(true));

        return new ResolutionToolkit(new JavaParserEngine(configuration), new JavaSymbolSolverResolver(typeSolver));
    }
}
