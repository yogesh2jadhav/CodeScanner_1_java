package com.java2okf.resolver;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.java2okf.model.JavaAnnotation;
import com.java2okf.model.ResolutionStatus;
import com.java2okf.model.TypeRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolver used when {@code analysis.resolveSymbols} is false. Everything that
 * would need a symbol solver is reported as {@link ResolutionStatus#UNRESOLVED};
 * only facts that are evident from syntax alone (primitive types) are kept.
 */
public final class DisabledSymbolResolver implements SymbolResolver {

    static final String REASON = "symbol resolution disabled";

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public TypeRef resolveType(Type type) {
        if (type.isPrimitiveType() || type.isVoidType()) {
            return TypeRef.notApplicable(type.asString());
        }
        return TypeRef.unresolved(type.asString());
    }

    @Override
    public List<TypeRef> referencedTypes(Type type) {
        List<TypeRef> result = new ArrayList<>();
        for (ClassOrInterfaceType classType : TypeWalker.classTypes(type)) {
            result.add(TypeRef.unresolved(classType.getNameWithScope()));
        }
        return result;
    }

    @Override
    public TypeRef resolveTypeByName(String qualifiedName) {
        return TypeRef.unresolved(qualifiedName);
    }

    @Override
    public JavaAnnotation resolveAnnotation(AnnotationExpr annotation) {
        return new JavaAnnotation(annotation.getNameAsString(), null, null, ResolutionStatus.UNRESOLVED);
    }

    @Override
    public ExecutableSignature signatureOf(List<Parameter> parameters) {
        List<String> types = parameters.stream()
                .map(p -> SourceText.erasedName(p.getType()) + (p.isVarArgs() ? "[]" : ""))
                .toList();
        // Primitive-only signatures are fully known even without a solver.
        boolean allPrimitive = parameters.stream().allMatch(p -> p.getType().isPrimitiveType());
        return new ExecutableSignature(types, allPrimitive ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED);
    }

    @Override
    public MemberResolution resolveMethodCall(MethodCallExpr call) {
        return MemberResolution.unresolved(SourceText.call(call), REASON);
    }

    @Override
    public MemberResolution resolveConstructor(ObjectCreationExpr creation) {
        return MemberResolution.unresolved("new " + creation.getType().asString() + "(..)", REASON);
    }

    @Override
    public MemberResolution resolveConstructor(ExplicitConstructorInvocationStmt invocation) {
        return MemberResolution.unresolved(invocation.isThis() ? "this(..)" : "super(..)", REASON);
    }

    @Override
    public MemberResolution resolveMethodReference(MethodReferenceExpr reference) {
        return MemberResolution.unresolved(SourceText.singleLine(reference.toString()), REASON);
    }

    @Override
    public Optional<MemberResolution> resolveFieldAccess(Expression expression) {
        return Optional.empty();
    }

    @Override
    public List<MemberResolution> overriddenMethods(TypeDeclaration<?> declaringType, MethodDeclaration method,
                                                    List<String> erasedParameterTypes) {
        return List.of();
    }

    @Override
    public TypeRef resolveImport(ImportDeclaration importDeclaration) {
        return TypeRef.unresolved(importDeclaration.getNameAsString());
    }
}
