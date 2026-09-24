# Troubleshooting

## Many `UNRESOLVED` relationships

Check `_metadata/errors.json`. Entries with `"stage": "SYMBOL_RESOLUTION"` show
each unresolved call and the solver's reason.

- **Library types are missing.** Pass the project's dependency jars so the
  solver can read them (they are never executed):

  ```bash
  # Maven projects: export the compile classpath once
  mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
  java -jar target/java2okf-1.0.0.jar analyze -s . -o ./output \
    --classpath "$(tr ':' ',' < cp.txt)"
  ```

  or list them in `analysis.classpath` in the configuration file. On
  Java2OKF's own sources this reduced unresolved references from 939 to 36.
- **Lombok or annotation processors.** Generated members don't exist in source.
  Run the build and include the generated sources directory
  (`analysis.includeGeneratedSources: true`), or accept these as `UNRESOLVED`.
- **Package does not match directory.** The log shows
  `Package 'x.y' does not match directory layout of …`. Such files are analysed,
  but other files may not resolve their types. Fix the layout if you can.
- **Known JavaSymbolSolver gaps.** Some calls inside lambdas passed to overloaded
  generic methods (`forEach`, `computeIfAbsent(..).add(..)`) cannot be inferred.
  They stay `UNRESOLVED` or `AMBIGUOUS` rather than being guessed.

## A file failed to parse

```text
WARN  Unable to parse: src/main/java/com/example/Legacy.java Reason: line 12, column 5: …
```

The file is skipped and listed in `errors.json` with `"stage": "PARSING"`. The
rest of the project is still analysed. Common causes: syntax newer than Java 21,
a file that doesn't compile, or a non-UTF-8 encoding. Fix the file or exclude
its directory with `scanner.excludeDirectories`.

## Validation fails

Run `java -jar target/java2okf-1.0.0.jar validate --bundle ./output --max-problems 200`.
Each problem has a code:

| Code | Typical cause |
| --- | --- |
| `BROKEN_LINK` | Stale documents from an earlier run, or a hand-edited bundle. Regenerate with `--clean`. |
| `ABSOLUTE_LINK`, `LINK_OUTSIDE_BUNDLE` | Hand-edited link |
| `MISSING_FRONTMATTER`, `INVALID_FRONTMATTER`, `MISSING_TYPE`, `MISSING_ID` | Hand-edited or foreign document |
| `INVALID_LOCATION` | Document moved to the wrong directory |
| `DUPLICATE_ID`, `INDISTINGUISHABLE_OVERLOAD` | Copied documents, or documents left over from an earlier run |
| `UNMARKED_RELATIONSHIP` | A relationship bullet that is neither a link nor marked |
| `UNTERMINATED_CODE_BLOCK`, `NOT_UTF8`, `INVALID_JSON` | Corrupted file |

## Stale documents after re-running

Without cleaning, documents for code that has since been deleted stay in the
output directory, and Java2OKF warns about it. Use `--clean`
(`output.cleanBeforeGenerate: true`). Only Java2OKF's own entries are removed.
Cleaning is refused if the directory holds bundle-named entries but no
`_metadata/analysis.json`, so an unrelated directory is never wiped.

## "Output directory must not be the source directory or one of its parents"

Choose an output directory outside the analysed project, or a subdirectory of it
such as `./output`.

## Out of memory on large projects

Each worker keeps its own symbol-solver caches. Lower `performance.threadCount`
(or `--threads 1`) and/or raise the heap:

```bash
java -Xmx4g -jar target/java2okf-1.0.0.jar analyze …
```

## More detail in the logs

```bash
java -jar target/java2okf-1.0.0.jar analyze … --log-level DEBUG
```

DEBUG logs each analysed type and each unresolved reference with its reason.
The log file (`logging.file`, default `./logs/java2okf.log`) receives the same output.

## `Unable to locate a Java Runtime` (macOS)

No JDK is on the `PATH`. Install Java 21+ and point `JAVA_HOME` at it, for
example with Homebrew:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```
