# OKF Output

The bundle is plain Markdown with YAML frontmatter. Markdown links are the
primary way relationships are represented. Every document can be reached from
the root `index.md` by following links.

## Layout

| Path | `type` | Content |
| --- | --- | --- |
| `index.md` | `Index` | Root index: counts, packages, all types, link to the methods index |
| `log.md` | `Log` | Generation log |
| `packages/<name>.md` | `JavaPackage` | Types of the package, package dependencies and dependents |
| `classes/<fqn>.md` | `JavaClass` | |
| `interfaces/<fqn>.md` | `JavaInterface` | |
| `enums/<fqn>.md` | `JavaEnum` | |
| `records/<fqn>.md` | `JavaRecord` | |
| `annotations/<fqn>.md` | `JavaAnnotation` | |
| `methods/<fqn>.<name>-<hash>.md` | `JavaMethod` / `JavaConstructor` | One document per method and constructor |
| `<dir>/index.md` | `Index` | Sorted listing of the directory |
| `_metadata/analysis.json` | — | Run metadata and counts (tool metadata, not an OKF concept) |
| `_metadata/errors.json` | — | Issues: `file`, `stage`, `severity`, `line`, `error` |

`index.md` and `log.md` are reserved names. Concept documents always live in
the directory for their `type`. `annotations/` holds annotation type declarations.
Constructors share `methods/` with methods.

File names are portable (`[A-Za-z0-9._$-]`, other characters become `_`).
`<hash>` is the first 6 hex characters of SHA-256 of the entity ID, which keeps
overloads apart. Names that differ only in case get an extra hash suffix.

## Frontmatter

Field order is fixed: `type, id, title, resource, tags, generated, java`.

```yaml
---
type: JavaMethod
id: java-method:com.example.OrderRepository.save(com.example.Order)
title: save
resource: src/main/java/com/example/OrderRepository.java
tags:
- java
- method
- com.example
generated:
  by: java2okf/1.0.0
  at: 2026-09-24T10:00:00Z      # omitted with okf.includeGenerationTimestamp: false
java:
  declaringClass: com.example.OrderRepository
  signature: save(com.example.Order)
  returnType: void
  visibility: public
  lines: 16-18
---
```

- `resource` is relative to the analysed project directory, never absolute.
- `generated` is omitted entirely with `okf.includeProvenance: false`.
- Type documents have `java.qualifiedName`, `package`, `kind`, `visibility`,
  `modifiers`, `typeParameters`, `lines`.
- Method documents have `java.signatureStatus: UNRESOLVED` when a parameter type
  could not be resolved.
- Nothing is marked "verified". Validity is only ever reported by the validator.

## Rendering relationship targets

| Target | Rendering |
| --- | --- |
| Entity with a document | `[OrderRepository](../classes/com.example.OrderRepository.md)` |
| Field of an analysed type | link to the declaring type's document |
| Resolved, outside the sources | `` `java.util.Map.put(java.lang.Object,java.lang.Object)` (external) `` |
| Resolved implicit member | `` `com.example.Order.Status.values()` (implicit) `` |
| Analysed entity whose document is disabled | `` `…` (no document) `` |
| Unresolved | `` `repository.save(..)` — UNRESOLVED `` |
| Ambiguous | `` `pick(..)` — AMBIGUOUS `` |

The validator requires every bullet in a relationship section to be either a
link or one of these markers.

## Class documents

Sections appear only when non-empty, in this order:

`Source` · `Package` · `Declaration` (reconstructed head, e.g.
`public abstract class A<T> extends B implements C`) · `Enclosing Type` ·
`Annotations` · `Inheritance` / `Extends` · `Implements` · `Subtypes` ·
`Enum Constants` · `Record Components` · `Fields` · `Constructors` · `Methods` ·
`Nested Types` · `Initializer Calls` · `Initializer Instantiations` · `Imports` ·
`Dependencies` (with categories) · `Used By` · `Called By` · `Analysis Notes`

No semantic description is invented. With `okf.generateMethodDocuments: false`,
each entry under `Methods` gets `Calls:` and `Called by:` sub-bullets instead.

## Method documents

`Declared By` · `Signature` · `Source` (`File.java:first-last`) · `Annotations` ·
`Parameters` (table) · `Returns` · `Throws` · `Overrides` · `Overridden By` ·
`Calls` (with lines) · `Called By` · `Uses` (every way each type is used:
parameter, return type, local variable, cast, …) · `Instantiates` ·
`References` (fields, method references) · `Referenced By` · `Resolution Status`

Example (sample project):

```markdown
## Calls

- [Order.getId()](./com.example.Order.getId-4943d9.md) — line 17
- `java.util.Map.put(java.lang.Object,java.lang.Object)` (external) — line 17

## Called By

- [OrderService.placeOrder(Customer, double)](./com.example.OrderService.placeOrder-d1a758.md) — at line 22
```

Forward relationships (`Calls`, `Overrides`) and reverse relationships
(`Called By`, `Overridden By`, `Referenced By`) are both generated, so the
bundle answers "what does this call?" and "who calls this?" by link-following.

## Package documents

`Classes` · `Interfaces` · `Enums` · `Records` · `Annotations` (nested types as
`Outer.Inner`) · `Dependencies` · `Dependents` (analysed packages only).

## analysis.json

```json
{
  "tool" : "Java2OKF",
  "version" : "1.0.0",
  "okfVersion" : "0.2",
  "project" : "sample-project",
  "sourceFiles" : 4, "parsedFiles" : 4, "failedFiles" : 0,
  "packages" : 1, "classes" : 4, "interfaces" : 0, "enums" : 1, "records" : 0, "annotations" : 0,
  "methods" : 16, "constructors" : 3, "fields" : 9,
  "relationships" : 138,
  "resolvedRelationships" : 138, "unresolvedRelationships" : 0,
  "ambiguousRelationships" : 0, "notApplicableRelationships" : 0,
  "relationshipsByType" : { "CALLS" : 25, "…" : 0 },
  "documents" : 31,
  "issues" : 0,
  "symbolResolution" : true
}
```

`startedAt`, `completedAt`, and `durationMillis` are added when timestamps are enabled.

## Reproducibility

With `okf.includeGenerationTimestamp: false` (or `--no-timestamps`), two runs on
the same source and configuration produce byte-identical bundles, whatever the
thread count. Documents use `\n` line endings and UTF-8.
