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
import com.github.javaparser.ast.type.Type;
import com.java2okf.model.JavaAnnotation;
import com.java2okf.model.TypeRef;

import java.util.List;
import java.util.Optional;

/**
 * Resolves AST references to entity IDs.
 *
 * <p>This is the only component that knows how references are resolved. The
 * analysers work purely against this interface, which keeps them testable and
 * lets symbol resolution be switched off ({@code analysis.resolveSymbols: false})
 * without changing any analyser.</p>
 *
 * <p>Implementations must never throw for unresolvable input and must never
 * guess: when a target cannot be proven it is reported as unresolved or
 * ambiguous. Implementations are not required to be thread-safe.</p>
 */
public interface SymbolResolver {

    /** True if this resolver actually attempts resolution. */
    boolean isEnabled();

    /** Resolves a declared type as a whole, e.g. the return type {@code List<Order>} → {@code java.util.List}. */
    TypeRef resolveType(Type type);

    /**
     * Returns every class or interface type mentioned in {@code type}, including
     * generic arguments and array components. Primitive types and type
     * variables are omitted because they do not create dependencies.
     */
    List<TypeRef> referencedTypes(Type type);

    /** Resolves the type named by a fully qualified or canonical name, e.g. from an import. */
    TypeRef resolveTypeByName(String qualifiedName);

    JavaAnnotation resolveAnnotation(AnnotationExpr annotation);

    /** Computes the erased signature of declared parameters (method, constructor, or record components). */
    ExecutableSignature signatureOf(List<Parameter> parameters);

    MemberResolution resolveMethodCall(MethodCallExpr call);

    /** Resolves the constructor invoked by {@code new T(...)}. */
    MemberResolution resolveConstructor(ObjectCreationExpr creation);

    /** Resolves {@code this(...)} or {@code super(...)}. */
    MemberResolution resolveConstructor(ExplicitConstructorInvocationStmt invocation);

    MemberResolution resolveMethodReference(MethodReferenceExpr reference);

    /**
     * Resolves a name or field-access expression to a field. Returns empty when
     * the expression does not denote a field (e.g. a local variable) or cannot be resolved.
     */
    Optional<MemberResolution> resolveFieldAccess(Expression expression);

    /**
     * Finds the methods that {@code method} overrides or implements in the
     * ancestors of {@code declaringType}. Returns an empty list if none are found.
     */
    List<MemberResolution> overriddenMethods(TypeDeclaration<?> declaringType, MethodDeclaration method,
                                             List<String> erasedParameterTypes);

    /** Resolves the target of an import declaration. */
    TypeRef resolveImport(ImportDeclaration importDeclaration);
}
