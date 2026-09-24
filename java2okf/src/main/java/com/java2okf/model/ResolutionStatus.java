package com.java2okf.model;

/**
 * Outcome of symbol resolution for a reference.
 *
 * <p>Static analysis must never guess. Whenever the symbol solver cannot prove
 * a target, the reference is kept with {@link #UNRESOLVED} or {@link #AMBIGUOUS}
 * and no target ID, instead of being dropped or pointed at a plausible match.</p>
 */
public enum ResolutionStatus {
    /** The target was proven by the symbol solver. */
    RESOLVED,
    /** The target could not be determined (missing dependency, unsupported construct, ...). */
    UNRESOLVED,
    /** Several targets are equally applicable (e.g. overloads with unresolved argument types). */
    AMBIGUOUS,
    /** Resolution does not apply, e.g. primitive types, type variables, or package imports. */
    NOT_APPLICABLE
}
