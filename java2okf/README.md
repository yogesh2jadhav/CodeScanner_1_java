# Java2OKF

## What is Java2OKF?

Java2OKF is a Java static-analysis tool that produces an **OKF v0.2 knowledge bundle**:
a directory of Markdown documents with YAML frontmatter, where Markdown links are the
relationships between packages, types, methods, and constructors.

It is a deterministic knowledge-extraction engine, not a Java-to-Markdown converter:

```text
Java source → scanner → JavaParser AST → JavaSymbolSolver → analysers
            → Java knowledge model → relationship graph → OKF generator → validator
```

- **Static analysis only.** No LLM, no database, no network, no build execution.
- **Never guesses.** Every reference is `RESOLVED`, `UNRESOLVED`, `AMBIGUOUS`, or
  `NOT_APPLICABLE`. Unresolved references are kept and marked; they never point at a guessed target.
- **Deterministic.** The same source and configuration give byte-identical output,
  whatever the thread count (with timestamps disabled).
- **Validated.** `analyze` only reports success after the written bundle passes validation.

See [docs/architecture.md](docs/architecture.md) for the design.

## Requirements

```text
Java 21+
Maven 3.9+
```

## Build

```bash
mvn clean package
```

This runs the tests and produces the self-contained executable `target/java2okf-1.0.0.jar`.

## Run

```bash
java -jar target/java2okf-1.0.0.jar analyze \
  --source ./examples/sample-project \
  --output ./output
```

`--source` is the project directory. It is scanned recursively, and source roots such as
`src/main/java` are detected from package declarations.

With a configuration file (CLI options still win):

```bash
java -jar target/java2okf-1.0.0.jar analyze --config config/java2okf.yaml --output ./output
```

Useful options (see `analyze --help` for all of them):

| Option | Effect |
| --- | --- |
| `-c, --config FILE` | YAML configuration file |
| `-s, --source DIR` | Project directory to scan |
| `-o, --output DIR` | Bundle directory |
| `--project-name NAME` | Name shown in the bundle (default: source directory name) |
| `--[no-]clean` | Remove previously generated bundle entries first |
| `--[no-]include-tests` | Also analyse `src/test`, `src/integrationTest`, … |
| `--[no-]method-docs` | One document per method/constructor (default on) |
| `--[no-]timestamps` | Generation timestamps; `--no-timestamps` for reproducible output |
| `--threads N` | Worker threads; `0` = automatic, `1` = serial |
| `--classpath a.jar,b.jar` | Library jars for symbol resolution (read, never executed) |
| `--log-level LEVEL` | `TRACE` … `ERROR` |
| `--log-file FILE` | Log file; `""` disables file logging |

Exit codes: `0` success (bundle valid), `1` bundle failed validation, `2` configuration or
argument error, `3` unexpected error.

Progress is logged to stderr:

```text
INFO  [1/7] Scanning source files...
INFO  [2/7] Parsing Java files...
INFO  [3/7] Resolving symbols...
INFO  [4/7] Building knowledge model...
INFO  [5/7] Building relationship graph...
INFO  [6/7] Generating OKF documents...
INFO  [7/7] Validating bundle...
```

## Validate

```bash
java -jar target/java2okf-1.0.0.jar validate \
  --bundle ./output
```

```text
OKF Validation
--------------

Documents:                  31
Valid documents:            31
Broken links:                0
Missing type fields:         0
Invalid frontmatter:         0
Duplicate IDs:               0
Unmarked relationships:      0
Unresolved Java refs:        0
Errors:                      0
Warnings:                    0

STATUS: PASS
```

The validator checks:

- **Files:** UTF-8, frontmatter present, valid YAML, `type` present, code fences closed.
- **Links:** targets exist, stay inside the bundle, no absolute filesystem links.
- **Structure:** reserved `index.md`/`log.md`, each concept type in its directory, unique IDs.
- **Java knowledge:** unique class and method IDs, distinguishable overloads, and every
  relationship that is not a link carries an explicit marker (`UNRESOLVED`, `AMBIGUOUS`,
  `(external)`, `(implicit)`, `(no document)`).

## Statistics

```bash
java -jar target/java2okf-1.0.0.jar stats \
  --bundle ./output
```

Prints entity counts, relationship counts by type, resolution counts, source-file counts,
documents per type, and broken links. The numbers come from `_metadata/analysis.json` plus a
scan of the bundle.

## Other commands

```bash
java -jar target/java2okf-1.0.0.jar version
java -jar target/java2okf-1.0.0.jar --help
java -jar target/java2okf-1.0.0.jar analyze --help
```

## Configuration

Precedence: **built-in defaults → configuration file → CLI options** (CLI always wins).
Relative paths are resolved against the current working directory. Unknown keys are rejected,
so typos fail loudly. Full example: [config/java2okf.yaml](config/java2okf.yaml).

| Property | Default | Description |
| --- | --- | --- |
| `project.name` | source directory name | Project name used in the bundle. |
| `project.sourceRoot` | — (required) | Directory to scan. CLI: `--source`. |
| `output.directory` | `./output` | Bundle directory. CLI: `--output`. |
| `output.cleanBeforeGenerate` | `false` | Delete previously generated entries (`packages/`, `classes/`, …, `_metadata/`, `index.md`, `log.md`) before writing. Other files are never touched. Refused if the directory holds bundle-named entries but no `_metadata/analysis.json`. |
| `analysis.includeTests` | `false` | Include test source directories (`src/test*`, `src/*Test`). |
| `analysis.includeGeneratedSources` | `false` | Include `generated`, `generated-sources`, `generated-test-sources` directories. |
| `analysis.resolveSymbols` | `true` | Use JavaSymbolSolver. When `false`, everything that needs resolution is `UNRESOLVED` and IDs use source type names. |
| `analysis.analyzeMethodCalls` | `true` | `CALLS` (methods, constructors, `this(..)`/`super(..)`) and method-reference `REFERENCES`. |
| `analysis.analyzeDependencies` | `true` | Type relationships: field/parameter/return/throws types, casts, `instanceof`, class literals, imports. |
| `analysis.analyzeInheritance` | `true` | `EXTENDS`, `IMPLEMENTS`, `OVERRIDES_METHOD`. |
| `analysis.analyzeAnnotations` | `true` | `ANNOTATED_WITH`. Annotations are always recorded on declarations. |
| `analysis.analyzeFields` | `true` | Field declarations, `HAS_FIELD`, and field-access `REFERENCES`. |
| `analysis.analyzeConstructors` | `true` | Constructor declarations and constructor calls. |
| `analysis.analyzeLocalVariables` | `true` | Local variable, lambda-parameter, and catch-parameter types. |
| `analysis.analyzeObjectCreation` | `true` | `INSTANTIATES` for `new T(..)` and `T::new`. |
| `analysis.classpath` | `[]` | Jars read by the symbol solver to resolve library types. CLI: `--classpath`. |
| `scanner.extensions` | `[.java]` | File extensions to scan. |
| `scanner.excludeDirectories` | `[target, build, out, generated, .git]` | Directory **names** skipped anywhere in the tree. |
| `okf.version` | `"0.2"` | OKF version to generate (only `0.2` is supported). |
| `okf.generateIndexes` | `true` | Root and per-directory `index.md`. |
| `okf.generateLogs` | `true` | `log.md`. |
| `okf.generateClassDocuments` | `true` | Documents for classes, interfaces, enums, records, annotations. |
| `okf.generateMethodDocuments` | `true` | One document per method/constructor. When `false`, class documents list each method's calls and callers instead. The model is always built. |
| `okf.includeProvenance` | `true` | `generated.by` in frontmatter. |
| `okf.includeGenerationTimestamp` | `true` | `generated.at` and run times in metadata. Disable for reproducible bundles. |
| `logging.level` | `INFO` | Level for Java2OKF loggers. |
| `logging.file` | `./logs/java2okf.log` | Log file (appended). Empty disables it. |
| `logging.console` | `true` | Log to stderr. |
| `performance.parallelAnalysis` | `true` | Analyse files in parallel. |
| `performance.threadCount` | `0` | Worker count; `0` = min(CPU cores, 4). Each worker has its own symbol-solver caches, so memory grows with it. |

## Output

Output for `examples/sample-project`:

```text
output/
├── index.md                       # root index (reserved)
├── log.md                         # generation log (reserved)
├── _metadata/
│   ├── analysis.json              # run metadata and counts (tool metadata, not OKF)
│   └── errors.json                # parse / resolution / analysis issues
├── packages/
│   ├── index.md
│   └── com.example.md
├── classes/
│   ├── index.md
│   ├── com.example.Customer.md
│   ├── com.example.Order.md
│   ├── com.example.OrderRepository.md
│   └── com.example.OrderService.md
├── enums/
│   ├── index.md
│   └── com.example.Order.Status.md
└── methods/
    ├── index.md
    ├── com.example.OrderRepository.save-b28378.md
    ├── com.example.OrderService.OrderService-018dcd.md     # constructor
    ├── com.example.OrderService.placeOrder-d1a758.md
    └── …
```

`interfaces/`, `records/`, and `annotations/` are created when the project has such types.
Method file names are `<qualified type>.<method>-<6 hex chars of SHA-256(id)>.md`. The full
signature is in the document. Document contents are described in [docs/okf-output.md](docs/okf-output.md).

## Limitations

Static analysis sees the source as written. Java2OKF does not hide what it cannot know:

- **Unresolved symbols.** Without `analysis.classpath`, references into libraries (Spring,
  SLF4J, …) are `UNRESOLVED`, as are their calls, annotations, and supertypes. JDK types always
  resolve. JavaSymbolSolver also has known gaps (some lambda-heavy generic calls, chained
  `computeIfAbsent(..).add(..)`); those calls are `UNRESOLVED` or `AMBIGUOUS` and listed in
  `_metadata/errors.json`.
- **Reflection and dynamic class loading.** `Class.forName`, `Method.invoke`, and string-based
  invocation cannot be followed.
- **Dependency injection and framework magic.** Wiring in XML, annotations, or at runtime is not
  modelled, e.g. which implementation Spring injects into an interface-typed field.
- **Runtime proxies.** AOP proxies, `java.lang.reflect.Proxy`, and bytecode-generated
  subclasses are invisible.
- **Polymorphic dispatch.** A call is linked to the statically resolved declaration (e.g. the
  interface method), not every runtime implementation. Implementations are reachable through
  that method's `Overridden By` section.
- **Generated sources.** Excluded by default. Annotation-processor output only exists after a
  build; add the generated directory and set `includeGeneratedSources: true` to include it.
- **Lombok-generated code.** Getters, setters, builders, and constructors generated by Lombok
  do not exist in source. Calls to them are `UNRESOLVED`.
- **Local and anonymous classes** are not modelled as types. Their code is attributed to the
  enclosing method, so none of their calls are lost.
- **Implicit members** (record accessors, default constructors, enum `values()`) resolve but
  have no declaration, so they are shown as `(implicit)` without a document.
- **Source roots** come from package declarations. Files whose directory doesn't match their
  package still parse, but cross-file resolution for them may be incomplete.

See [docs/troubleshooting.md](docs/troubleshooting.md) for how to improve resolution.

## Tests

```bash
mvn clean test
```

Unit tests cover the scanner, parser, analysers, symbol resolution, graph, generator,
validator, and CLI. `SampleProjectIntegrationTest` runs the full pipeline on
`examples/sample-project` twice with different thread counts and checks the bundles are identical.

## License

MIT — see [LICENSE](LICENSE).
