# Architecture

Java2OKF is a pipeline of single-purpose stages. The stages talk through
explicit data structures, and the Java knowledge model in the middle is the
central boundary: analysers write it, generators read it.

```text
                ┌──────────────┐
 Java source ──▶│ ProjectScanner│── SourceFileInventory (sorted files, source roots)
                └──────┬───────┘
                       ▼
                ┌──────────────────┐  per worker: ResolutionToolkit
                │ JavaParserEngine │  (JavaParser + JavaSymbolSolver, not shared)
                └──────┬───────────┘
                       ▼
                ┌──────────────────────────────────────────────┐
                │ SourceFileAnalyzer → ClassAnalyzer           │
                │   FieldAnalyzer · MethodAnalyzer ·           │
                │   InheritanceAnalyzer · DependencyAnalyzer · │
                │   RelationshipAnalyzer · AnnotationAnalyzer  │
                │        (all resolve via SymbolResolver)      │
                └──────┬───────────────────────────────────────┘
                       ▼ FileAnalysisResult (one per file)
                ┌──────────────────────┐
                │ KnowledgeModelBuilder│── JavaProject (immutable, sorted, de-duplicated)
                └──────┬───────────────┘
                       ▼
                ┌──────────────────────┐
                │ KnowledgeGraphBuilder│── KnowledgeGraph (reverse indexes, aggregates)
                └──────┬───────────────┘
                       ▼
                ┌──────────────┐   ┌──────────┐   ┌──────────────────────────────┐
                │ OkfGenerator │──▶│ OkfWriter│──▶│ OkfValidator +               │
                └──────────────┘   └──────────┘   │ KnowledgeModelValidator      │
                                                  └──────────────────────────────┘
```

## Packages

| Package | Responsibility |
| --- | --- |
| `cli` | picocli commands (`analyze`, `validate`, `stats`, `version`) and exit codes |
| `config` | Configuration tree, YAML loading, CLI overrides, validation |
| `scanner` | File discovery, exclusions, package detection, source roots |
| `parser` | JavaParser wrapper that reports failures instead of throwing |
| `resolver` | `SymbolResolver` interface, JavaSymbolSolver implementation, disabled implementation |
| `analyzer` | Per-file analysers, model assembly, parallel orchestration (`ProjectAnalyzer`) |
| `model` | The Java knowledge model; no AST and no Markdown |
| `graph` | Relationship indexes and derived facts |
| `okf` | Bundle layout, Markdown and frontmatter rendering, metadata, writing |
| `validation` | Bundle and model validation, bundle statistics |
| `util` | Version information, logging setup |

`AnalysisPipeline` (root package) connects the stages. `Main` only starts picocli.

## Key design decisions

### The knowledge model is mandatory

The OKF generator never sees JavaParser classes. Everything it needs is in
`JavaProject` and `KnowledgeGraph`. A JSON exporter or graph-database loader
can be added as another consumer of the same model without touching the analysis.

### Resolution behind an interface

Analysers depend only on `SymbolResolver`. `JavaSymbolSolverResolver` guards
every call: JavaSymbolSolver reports failure through many runtime exceptions,
and occasionally a `StackOverflowError`. Any failure becomes `UNRESOLVED`, and
overload ambiguity becomes `AMBIGUOUS`. `DisabledSymbolResolver` implements
`analysis.resolveSymbols: false` with the same analysers.

### IDs match between declarations and call sites

A call is only linked if the ID computed at the call site equals the ID of the
declaration. Both sides use the same erasure (`JavaSymbolSolverResolver.erasedName`):
type arguments are dropped, type variables become their leftmost bound (or
`java.lang.Object`), and varargs become arrays. So `Box<T>.put(T)` and a call
`box.put("a")` both produce `java-method:p.Box.put(java.lang.Object)`.

Overrides go through generic supertypes: the ancestor's type arguments are
substituted before comparing, so `save(Order)` is recognised as implementing
`Repository<Order>.save(T)`.

### Concurrency without shared solver state

Neither JavaParser nor JavaSymbolSolver is thread-safe. JavaSymbolSolver resolves
a node through the resolver stored in that node's compilation unit, and its
type solvers cache parsed files without synchronisation. So:

1. Files are split into fixed round-robin partitions.
2. Each partition is handled by one task that owns a private `ResolutionToolkit`
   (parser + type solvers + resolver).
3. Results are merged and sorted by `KnowledgeModelBuilder`.

The output is therefore independent of worker count and scheduling. The
integration test checks this by comparing a 1-thread run with a 4-thread run.

### Bounded memory

ASTs are never kept for the whole project. Phase 2 parses only to check syntax
and collect failures. Phase 3 re-parses each file with resolution enabled and
drops the AST once its facts are extracted. Parsing is cheap compared with
resolution, so this trades a little CPU for predictable memory.

### Determinism

- Files are sorted by relative path. Packages, types, members, and
  relationships are sorted. Hash-map iteration order is never exposed.
- IDs come from qualified names and erased signatures, never from random UUIDs.
- File names come from qualified names. Method files add a SHA-256 prefix of
  the ID. Case-insensitive collisions (macOS, Windows) are detected and
  resolved in a fixed order.
- Absolute paths never appear in the bundle. Timestamps can be disabled.

### Failure isolation

- A parse failure is reported (`_metadata/errors.json`, stage `PARSING`) and
  the file is skipped.
- An unexpected analyser failure discards that file's facts, which could be
  partial and misleading, and records an `ANALYSIS` error.
- Unresolvable references are kept as `UNRESOLVED` relationships and
  reported with stage `SYMBOL_RESOLUTION`.

### Validation before success

`analyze` writes the bundle, then runs `OkfValidator` on the files on disk
and `KnowledgeModelValidator` on the model. It exits `0` only if both pass.

## Future work (not implemented)

- **Incremental analysis:** cache per-file results under `_metadata/cache/`,
  keyed by SHA-256(source + analyzer version + relevant configuration).
- **Semantic enrichment (v2):** an optional LLM layer that adds
  interpretations to the model, clearly separated from static facts.
