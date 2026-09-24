package com.java2okf.resolver;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.MethodAmbiguityException;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedAnnotationDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeParameterDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.java2okf.model.EntityIds;
import com.java2okf.model.JavaAnnotation;
import com.java2okf.model.ResolutionStatus;
import com.java2okf.model.TypeKind;
import com.java2okf.model.TypeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link SymbolResolver} backed by JavaSymbolSolver.
 *
 * <p>Every call is guarded: JavaSymbolSolver signals failure through a variety
 * of runtime exceptions (and, for pathological generics, stack overflows).
 * Any failure becomes {@link ResolutionStatus#UNRESOLVED}, overload ambiguity
 * becomes {@link ResolutionStatus#AMBIGUOUS}; nothing is ever guessed.</p>
 *
 * <p>Not thread-safe: JavaSymbolSolver's type solvers keep unsynchronised caches.
 * Each analysis worker owns one instance (see {@link ResolutionToolkit}).</p>
 */
public final class JavaSymbolSolverResolver implements SymbolResolver {

    private static final Logger LOG = LoggerFactory.getLogger(JavaSymbolSolverResolver.class);

    private static final String OBJECT = "java.lang.Object";

    private final TypeSolver typeSolver;
    private final JavaParserFacade facade;
    private final Map<String, TypeRef> typesByName = new HashMap<>();
    private final Map<TypeDeclaration<?>, AncestorMethods> ancestorCache = new IdentityHashMap<>();

    public JavaSymbolSolverResolver(TypeSolver typeSolver) {
        this.typeSolver = typeSolver;
        this.facade = JavaParserFacade.get(typeSolver);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ------------------------------------------------------------------ types

    @Override
    public TypeRef resolveType(Type type) {
        if (type.isPrimitiveType() || type.isVoidType()) {
            return TypeRef.notApplicable(type.asString());
        }
        String display = type.asString();
        try {
            // 'var' has no syntactic type; Type.resolve() lets the solver infer it from the initializer.
            ResolvedType resolved = type.isVarType() ? type.resolve() : facade.convertToUsage(type);
            return toTypeRef(resolved, display);
        } catch (RuntimeException | StackOverflowError e) {
            LOG.debug("Unable to resolve type {}: {}", display, SourceText.reason(e));
            return TypeRef.unresolved(display);
        }
    }

    @Override
    public List<TypeRef> referencedTypes(Type type) {
        Map<String, TypeRef> result = new LinkedHashMap<>();
        for (ClassOrInterfaceType classType : TypeWalker.classTypes(type)) {
            TypeRef ref = resolveType(classType);
            if (ref.status() == ResolutionStatus.NOT_APPLICABLE) {
                continue; // type variable
            }
            String display = classType.getNameWithScope();
            TypeRef named = new TypeRef(display, ref.qualifiedName(), ref.targetId(), ref.status());
            result.putIfAbsent(named.targetId() != null ? named.targetId() : "?" + display, named);
        }
        return new ArrayList<>(result.values());
    }

    @Override
    public TypeRef resolveTypeByName(String qualifiedName) {
        return typesByName.computeIfAbsent(qualifiedName, name -> {
            try {
                SymbolReference<ResolvedReferenceTypeDeclaration> ref = typeSolver.tryToSolveType(name);
                if (ref.isSolved()) {
                    ResolvedReferenceTypeDeclaration declaration = ref.getCorrespondingDeclaration();
                    String qn = declaration.getQualifiedName();
                    return TypeRef.resolved(name, qn, EntityIds.typeId(kindOf(declaration), qn));
                }
            } catch (RuntimeException | StackOverflowError e) {
                LOG.debug("Unable to resolve type name {}: {}", name, SourceText.reason(e));
            }
            return TypeRef.unresolved(name);
        });
    }

    @Override
    public JavaAnnotation resolveAnnotation(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        try {
            SymbolReference<ResolvedAnnotationDeclaration> ref = facade.solve(annotation);
            if (ref.isSolved()) {
                String qn = ref.getCorrespondingDeclaration().getQualifiedName();
                return new JavaAnnotation(name, qn, EntityIds.typeId(TypeKind.ANNOTATION, qn), ResolutionStatus.RESOLVED);
            }
        } catch (RuntimeException | StackOverflowError e) {
            LOG.debug("Unable to resolve annotation @{}: {}", name, SourceText.reason(e));
        }
        return new JavaAnnotation(name, null, null, ResolutionStatus.UNRESOLVED);
    }

    @Override
    public TypeRef resolveImport(ImportDeclaration importDeclaration) {
        String name = importDeclaration.getNameAsString();
        if (importDeclaration.isAsterisk() && !importDeclaration.isStatic()) {
            // On-demand package import: the target is a package, not a resolvable type.
            return new TypeRef(name + ".*", name, EntityIds.packageId(name), ResolutionStatus.NOT_APPLICABLE);
        }
        String typeName = importDeclaration.isStatic() && !importDeclaration.isAsterisk()
                ? name.substring(0, Math.max(0, name.lastIndexOf('.')))
                : name;
        String display = name + (importDeclaration.isAsterisk() ? ".*" : "");
        TypeRef ref = resolveTypeByName(typeName);
        return new TypeRef(display, ref.qualifiedName(), ref.targetId(), ref.status());
    }

    // ------------------------------------------------------------- signatures

    @Override
    public ExecutableSignature signatureOf(List<Parameter> parameters) {
        List<String> types = new ArrayList<>();
        ResolutionStatus status = ResolutionStatus.RESOLVED;
        for (Parameter parameter : parameters) {
            String suffix = parameter.isVarArgs() ? "[]" : "";
            try {
                types.add(erasedName(facade.convertToUsage(parameter.getType())) + suffix);
            } catch (RuntimeException | StackOverflowError e) {
                // Fall back to the erased source text; the ID stays deterministic but
                // cannot match call sites, which is why the status is recorded.
                types.add(SourceText.erasedName(parameter.getType()) + suffix);
                status = ResolutionStatus.UNRESOLVED;
            }
        }
        return new ExecutableSignature(types, status);
    }

    // ---------------------------------------------------------------- members

    @Override
    public MemberResolution resolveMethodCall(MethodCallExpr call) {
        String text = SourceText.call(call);
        try {
            SymbolReference<ResolvedMethodDeclaration> ref = facade.solve(call);
            if (!ref.isSolved()) {
                return MemberResolution.unresolved(text, "no matching declaration found");
            }
            return methodTarget(ref.getCorrespondingDeclaration(), text);
        } catch (MethodAmbiguityException e) {
            return MemberResolution.ambiguous(text, SourceText.reason(e));
        } catch (RuntimeException | StackOverflowError e) {
            return MemberResolution.unresolved(text, SourceText.reason(e));
        }
    }

    @Override
    public MemberResolution resolveConstructor(ObjectCreationExpr creation) {
        String text = "new " + creation.getType().asString() + "(..)";
        try {
            SymbolReference<ResolvedConstructorDeclaration> ref = facade.solve(creation);
            if (!ref.isSolved()) {
                return MemberResolution.unresolved(text, "no matching constructor found");
            }
            return constructorTarget(ref.getCorrespondingDeclaration(), text);
        } catch (MethodAmbiguityException e) {
            return MemberResolution.ambiguous(text, SourceText.reason(e));
        } catch (RuntimeException | StackOverflowError e) {
            return MemberResolution.unresolved(text, SourceText.reason(e));
        }
    }

    @Override
    public MemberResolution resolveConstructor(ExplicitConstructorInvocationStmt invocation) {
        String text = invocation.isThis() ? "this(..)" : "super(..)";
        try {
            SymbolReference<ResolvedConstructorDeclaration> ref = facade.solve(invocation);
            if (!ref.isSolved()) {
                return MemberResolution.unresolved(text, "no matching constructor found");
            }
            return constructorTarget(ref.getCorrespondingDeclaration(), text);
        } catch (MethodAmbiguityException e) {
            return MemberResolution.ambiguous(text, SourceText.reason(e));
        } catch (RuntimeException | StackOverflowError e) {
            return MemberResolution.unresolved(text, SourceText.reason(e));
        }
    }

    @Override
    public MemberResolution resolveMethodReference(MethodReferenceExpr reference) {
        String text = SourceText.singleLine(reference.toString());
        try {
            SymbolReference<ResolvedMethodDeclaration> ref = facade.solve(reference);
            if (!ref.isSolved()) {
                return MemberResolution.unresolved(text, "no matching declaration found");
            }
            return methodTarget(ref.getCorrespondingDeclaration(), text);
        } catch (MethodAmbiguityException e) {
            return MemberResolution.ambiguous(text, SourceText.reason(e));
        } catch (RuntimeException | StackOverflowError e) {
            return MemberResolution.unresolved(text, SourceText.reason(e));
        }
    }

    @Override
    public Optional<MemberResolution> resolveFieldAccess(Expression expression) {
        try {
            SymbolReference<? extends ResolvedValueDeclaration> ref;
            if (expression instanceof NameExpr name) {
                ref = facade.solve(name);
            } else if (expression instanceof FieldAccessExpr access) {
                ref = facade.solve(access);
            } else {
                return Optional.empty();
            }
            if (!ref.isSolved() || !ref.getCorrespondingDeclaration().isField()) {
                return Optional.empty();
            }
            ResolvedFieldDeclaration field = ref.getCorrespondingDeclaration().asField();
            String owner = field.declaringType().getQualifiedName();
            return Optional.of(MemberResolution.resolved(EntityIds.fieldId(owner, field.getName()),
                    owner + "." + field.getName(), EntityIds.typeId(kindOf(field.declaringType()), owner)));
        } catch (RuntimeException | StackOverflowError e) {
            // Names that are not fields (locals, packages, type names) routinely fail; that is not a finding.
            return Optional.empty();
        }
    }

    @Override
    public List<MemberResolution> overriddenMethods(TypeDeclaration<?> declaringType, MethodDeclaration method,
                                                    List<String> erasedParameterTypes) {
        AncestorMethods ancestors = ancestorCache.computeIfAbsent(declaringType, this::collectAncestorMethods);
        if (ancestors.failure != null) {
            return List.of(MemberResolution.unresolved(method.getNameAsString(), ancestors.failure));
        }
        List<MemberResolution> result = new ArrayList<>();
        for (AncestorMethod candidate : ancestors.methods) {
            MethodUsage usage = candidate.usage();
            if (!usage.getName().equals(method.getNameAsString()) || usage.getNoParams() != erasedParameterTypes.size()) {
                continue;
            }
            try {
                if (!overridable(usage.getDeclaration()) || !parametersMatch(candidate, erasedParameterTypes)) {
                    continue;
                }
                MemberResolution target = methodTarget(usage.getDeclaration(), usage.getName());
                if (target.isResolved() && result.stream().noneMatch(r -> r.targetId().equals(target.targetId()))) {
                    result.add(target);
                }
            } catch (RuntimeException | StackOverflowError e) {
                LOG.debug("Skipping candidate {} while checking overrides: {}", usage.getName(), SourceText.reason(e));
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- helpers

    private MemberResolution methodTarget(ResolvedMethodDeclaration declaration, String sourceText) {
        String owner = declaration.declaringType().getQualifiedName();
        List<String> parameters = erasedParameterTypes(declaration);
        if (parameters == null) {
            return MemberResolution.unresolved(sourceText, "parameter types of " + owner + "." + declaration.getName()
                    + " could not be resolved");
        }
        String signature = EntityIds.signature(declaration.getName(), parameters);
        return MemberResolution.resolved(EntityIds.methodId(owner, signature), owner + "." + signature,
                EntityIds.typeId(kindOf(declaration.declaringType()), owner));
    }

    private MemberResolution constructorTarget(ResolvedConstructorDeclaration declaration, String sourceText) {
        String owner = declaration.declaringType().getQualifiedName();
        List<String> parameters = erasedParameterTypes(declaration);
        if (parameters == null) {
            return MemberResolution.unresolved(sourceText, "parameter types of " + owner + " constructor could not be resolved");
        }
        String id = EntityIds.constructorId(owner, parameters);
        return MemberResolution.resolved(id, EntityIds.localPart(id),
                EntityIds.typeId(kindOf(declaration.declaringType()), owner));
    }

    /**
     * Erased parameter types of a resolved declaration. When a parameter type of a
     * source declaration cannot be resolved, the same textual fallback as in
     * {@link #signatureOf} is used so that the ID still matches the declaration.
     * Returns {@code null} if the signature cannot be determined.
     */
    private List<String> erasedParameterTypes(ResolvedMethodLikeDeclaration declaration) {
        List<String> types = new ArrayList<>();
        for (int i = 0; i < declaration.getNumberOfParams(); i++) {
            try {
                types.add(erasedName(declaration.getParam(i).getType()));
            } catch (RuntimeException | StackOverflowError e) {
                Optional<Parameter> astParameter = astParameter(declaration, i);
                if (astParameter.isEmpty()) {
                    return null;
                }
                Parameter p = astParameter.get();
                types.add(SourceText.erasedName(p.getType()) + (p.isVarArgs() ? "[]" : ""));
            }
        }
        return types;
    }

    private static Optional<Parameter> astParameter(ResolvedMethodLikeDeclaration declaration, int index) {
        try {
            Optional<Node> ast = declaration.toAst();
            if (ast.isPresent() && ast.get() instanceof CallableDeclaration<?> callable
                    && index < callable.getParameters().size()) {
                return Optional.of(callable.getParameters().get(index));
            }
        } catch (RuntimeException e) {
            LOG.debug("No AST available for {}", declaration.getName());
        }
        return Optional.empty();
    }

    private boolean parametersMatch(AncestorMethod candidate, List<String> erasedParameterTypes) {
        for (int i = 0; i < erasedParameterTypes.size(); i++) {
            // Substitute the ancestor's type arguments first: Repository<Order>.save(T) takes an Order.
            ResolvedType parameterType = candidate.ancestor()
                    .useThisTypeParametersOnTheGivenType(candidate.usage().getParamType(i));
            if (!erasedName(parameterType).equals(erasedParameterTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean overridable(ResolvedMethodDeclaration declaration) {
        return !declaration.isStatic() && declaration.accessSpecifier() != AccessSpecifier.PRIVATE;
    }

    /**
     * Collects the methods of all ancestors with type arguments substituted, so
     * that {@code save(Order)} is recognised as implementing {@code Repository<Order>.save(T)}.
     */
    private AncestorMethods collectAncestorMethods(TypeDeclaration<?> declaringType) {
        ResolvedReferenceTypeDeclaration declaration;
        try {
            declaration = facade.getTypeDeclaration(declaringType);
        } catch (RuntimeException | StackOverflowError e) {
            return new AncestorMethods(List.of(), "declaring type could not be resolved: " + SourceText.reason(e));
        }
        List<ResolvedReferenceType> ancestors;
        try {
            ancestors = declaration.getAllAncestors();
        } catch (RuntimeException | StackOverflowError e) {
            // Some ancestor is unresolvable (e.g. missing library); fall back to the resolvable direct ancestors.
            LOG.debug("Incomplete ancestors for {}: {}", declaringType.getNameAsString(), SourceText.reason(e));
            try {
                ancestors = declaration.getAncestors(true);
            } catch (RuntimeException | StackOverflowError inner) {
                return new AncestorMethods(List.of(), "ancestors could not be resolved: " + SourceText.reason(inner));
            }
        }
        List<AncestorMethod> methods = new ArrayList<>();
        for (ResolvedReferenceType ancestor : ancestors) {
            try {
                ancestor.getDeclaredMethods().forEach(usage -> methods.add(new AncestorMethod(ancestor, usage)));
            } catch (RuntimeException | StackOverflowError e) {
                LOG.debug("Unable to list methods of {}: {}", ancestor.describe(), SourceText.reason(e));
            }
        }
        return new AncestorMethods(methods, null);
    }

    private TypeRef toTypeRef(ResolvedType resolved, String display) {
        if (resolved.isPrimitive() || resolved.isVoid() || resolved.isTypeVariable()) {
            return TypeRef.notApplicable(display);
        }
        if (resolved.isArray()) {
            ResolvedType element = resolved;
            while (element.isArray()) {
                element = element.asArrayType().getComponentType();
            }
            TypeRef elementRef = toTypeRef(element, display);
            return new TypeRef(display, erasedName(resolved), elementRef.targetId(),
                    elementRef.status() == ResolutionStatus.NOT_APPLICABLE ? ResolutionStatus.NOT_APPLICABLE : elementRef.status());
        }
        if (resolved.isReferenceType()) {
            ResolvedReferenceType reference = resolved.asReferenceType();
            Optional<ResolvedReferenceTypeDeclaration> declaration = reference.getTypeDeclaration();
            if (declaration.isEmpty()) {
                return TypeRef.unresolved(display);
            }
            String qn = reference.getQualifiedName();
            return TypeRef.resolved(display, qn, EntityIds.typeId(kindOf(declaration.get()), qn));
        }
        if (resolved.isWildcard() && resolved.asWildcard().isBounded()) {
            return toTypeRef(resolved.asWildcard().getBoundedType(), display);
        }
        return TypeRef.notApplicable(display);
    }

    /**
     * JLS erasure rendered with qualified names: type arguments are dropped and
     * type variables become the erasure of their leftmost bound (or Object).
     */
    static String erasedName(ResolvedType type) {
        if (type.isPrimitive() || type.isVoid()) {
            return type.describe();
        }
        if (type.isArray()) {
            return erasedName(type.asArrayType().getComponentType()) + "[]";
        }
        if (type.isReferenceType()) {
            return type.asReferenceType().getQualifiedName();
        }
        if (type.isTypeVariable()) {
            ResolvedTypeParameterDeclaration parameter = type.asTypeParameter();
            List<ResolvedTypeParameterDeclaration.Bound> bounds = parameter.getBounds();
            for (ResolvedTypeParameterDeclaration.Bound bound : bounds) {
                if (bound.isExtends()) {
                    return erasedName(bound.getType());
                }
            }
            return OBJECT;
        }
        if (type.isWildcard()) {
            return type.asWildcard().isExtends() ? erasedName(type.asWildcard().getBoundedType()) : OBJECT;
        }
        return type.describe();
    }

    static TypeKind kindOf(ResolvedTypeDeclaration declaration) {
        // Annotation types are interfaces too, so they must be checked first.
        if (declaration.isAnnotation()) {
            return TypeKind.ANNOTATION;
        }
        if (declaration.isEnum()) {
            return TypeKind.ENUM;
        }
        if (declaration.isRecord()) {
            return TypeKind.RECORD;
        }
        if (declaration.isInterface()) {
            return TypeKind.INTERFACE;
        }
        return TypeKind.CLASS;
    }

    private record AncestorMethods(List<AncestorMethod> methods, String failure) {
    }

    private record AncestorMethod(ResolvedReferenceType ancestor, MethodUsage usage) {
    }
}
