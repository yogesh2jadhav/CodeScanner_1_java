# Knowledge Model

The model lives in `com.java2okf.model` and holds only facts extracted by
static analysis. `JavaProject` is immutable once built.

## Entities

| Class | Captures |
| --- | --- |
| `JavaProject` | Name, packages, types, methods, constructors, fields, relationships, issues, file statistics |
| `JavaPackage` | ID, name, type IDs |
| `JavaType` (sealed) | Header (ID, qualified/simple name, package, kind, location, visibility, `abstract`/`final`/`static`/`sealed`, annotations, enclosing type, type parameters), interfaces, fields, constructors, methods, nested types |
| `JavaClass` | + `superClass` (the explicit `extends`, or `null`) |
| `JavaInterface` | `interfaces()` = extended interfaces |
| `JavaEnum` | + constants in declaration order |
| `JavaRecord` | + components (also modelled as private final fields) |
| `JavaAnnotationType` | Annotation members are modelled as abstract methods |
| `JavaMethod` | ID, declaring type, name, erased signature, declaration text, return type, visibility, modifiers, type parameters, parameters, thrown types, annotations, location, signature status |
| `JavaConstructor` | Like a method without return type; includes compact record constructors |
| `JavaField` | One per variable (`int a, b;` → two fields), with type, modifiers, annotations |
| `JavaParameter` | Name, type, varargs, `final`, annotations |
| `JavaAnnotation` | Name as written, qualified name and target ID when resolved, status |
| `TypeRef` | Type as written (`List<Order>`), erased qualified name, target ID, status |
| `JavaSourceLocation` | Project-relative file, first and last line |
| `JavaRelationship` | See below |
| `AnalysisIssue` | File, stage, severity, line, message |

Declaration records are pure facts. Calls, callers, dependencies, and other
connections are relationships, indexed by `KnowledgeGraph`, so there is one
source of truth for each fact.

## Entity IDs

IDs are deterministic and built only from qualified names and erased types:

```text
java-package:com.example                     (default package: java-package:(default))
java-class:com.example.Order                 java-interface: / java-enum: / java-record: / java-annotation:
java-class:com.example.Order.Line            nested types use canonical dotted names
java-method:com.example.OrderService.place(com.example.Customer,double)
java-constructor:com.example.Order(java.lang.String,com.example.Customer,double)
java-field:com.example.OrderService.repository
```

Signature erasure follows the JLS: type arguments are removed, type variables
become the erasure of their leftmost bound (`<T extends Comparable<T>>` →
`java.lang.Comparable`, unbounded → `java.lang.Object`), and varargs become
arrays (`String...` → `java.lang.String[]`).

If a parameter type cannot be resolved, the erased source text is used
(`save(Order)`) and `signatureStatus` is `UNRESOLVED`. If two such signatures
collide within a type, the source line disambiguates them (`…@L42`) and an
issue is recorded.

## Relationships

```text
JavaRelationship(sourceId, type, targetId, targetName, status, confidence,
                 sourceLocation, lines, category, details)
```

- `targetId` is **always `null`** for `UNRESOLVED` and `AMBIGUOUS`. The record
  constructor enforces this, so a guessed target cannot be created.
- `targetName` is the qualified name when resolved, or the source text otherwise
  (e.g. `repository.save(..)`).
- `lines` lists every line where the fact occurs. Repeated occurrences
  are merged into one relationship.
- `confidence` is reserved for future non-deterministic producers and is always
  `null`. Static facts get no made-up confidence.
- `details` holds extra facts such as `parameter`, `variable`, `kind`
  (`cast`, `field`, `methodReference`, …), `reason` (why resolution failed),
  `declaringType` (type ID of a member target, including library members), and
  `derived`.

| Type | Source → target | Emitted for |
| --- | --- | --- |
| `CONTAINS` | package → type | every type |
| `DECLARES` | type → method / constructor / nested type | every member |
| `HAS_FIELD` | type → field | every field |
| `EXTENDS` | type → type | `extends` of classes and interfaces |
| `IMPLEMENTS` | type → interface | `implements` of classes, enums, records |
| `IMPORTS` | top-level type → type / package | each import (package imports are `NOT_APPLICABLE`) |
| `USES_TYPE` | field / executable / type → type | field types, local variables, generic arguments of supertypes |
| `HAS_PARAMETER` | executable → type | each class type in each parameter type |
| `RETURNS_TYPE` | method → type | each class type in the return type |
| `THROWS` | executable → type | `throws` clause |
| `CALLS` | executable / type → method / constructor | invocations, resolved `new`, `this(..)`, `super(..)` |
| `INSTANTIATES` | executable / type → type | `new T(..)`, `T::new` |
| `REFERENCES` | executable / type → field / method / type | field access, method references, casts, `instanceof`, patterns, class literals, array creation |
| `ANNOTATED_WITH` | type / executable / field → annotation type | each annotation (parameter annotations have `details.parameter`) |
| `OVERRIDES_METHOD` | method → method | resolved overrides and implementations, including through generic supertypes. `@Override` with no identifiable target → `UNRESOLVED` |
| `OVERRIDES` | type → ancestor type | derived: at least one method of the type overrides a method of the ancestor |

Code in field initialisers, initializer blocks, and enum constant bodies is
attributed to the declaring type. Code in lambdas and in local or anonymous
classes is attributed to the enclosing method.

## Dependency categories

Relationships that create a type dependency carry a category.
`KnowledgeGraph.dependenciesOf(typeId)` aggregates them per target type across
the type and all its members:

| Category | From |
| --- | --- |
| `FIELD_DEPENDENCY` | field types |
| `PARAMETER_DEPENDENCY` | parameter types |
| `RETURN_TYPE_DEPENDENCY` | return types |
| `LOCAL_VARIABLE_DEPENDENCY` | local variables (including inferred `var`), lambda and catch parameters |
| `CALL_DEPENDENCY` | calls and instantiations (target: the declaring type of the callee) |
| `INHERITANCE_DEPENDENCY` | supertypes and their generic arguments |
| `ANNOTATION_DEPENDENCY` | annotations |
| `EXCEPTION_DEPENDENCY` | `throws` clauses |
| `TYPE_REFERENCE_DEPENDENCY` | casts, `instanceof`, patterns, class literals, field accesses, method references |

`EXCEPTION_DEPENDENCY` and `TYPE_REFERENCE_DEPENDENCY` extend the category list
in the implementation plan, so that thrown exceptions and type references are
classified instead of being lumped into another category.

`IMPORTS` has no category because an import can be unused.

## Resolution status

| Status | Meaning |
| --- | --- |
| `RESOLVED` | Proven by JavaSymbolSolver; `targetId` set |
| `UNRESOLVED` | Target could not be determined; `targetId` null; `details.reason` explains why |
| `AMBIGUOUS` | Several overloads equally applicable; `targetId` null |
| `NOT_APPLICABLE` | Resolution does not apply (primitives, type variables, package imports) |

A resolved target may lie outside the analysed sources (JDK or library):
`KnowledgeGraph.isInternal(id)` is then false. A resolved member of an analysed
type that has no declaration (record accessor, default constructor, enum
`values()`) is `isImplicitMember(id)`.

## Graph queries

`KnowledgeGraph` answers questions such as:

- What does this method call? — `outgoing(methodId, CALLS)`
- Who calls this method? — `incoming(methodId, CALLS)`
- Who calls into this type? — `callsIntoType(typeId)`
- Which types does this type depend on, and why? — `dependenciesOf(typeId)`
- Which classes depend on this class? — `dependentsOf(typeId)`
- Which packages depend on this package? — `packageDependenciesOf` / `packageDependentsOf`
- Who overrides this method? — `incoming(methodId, OVERRIDES_METHOD)`
