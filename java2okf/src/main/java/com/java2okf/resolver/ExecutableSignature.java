package com.java2okf.resolver;

import com.java2okf.model.ResolutionStatus;

import java.util.List;

/**
 * Erased parameter types of a declared method or constructor.
 *
 * @param erasedParameterTypes one entry per parameter, fully qualified when resolved,
 *                             otherwise the erased source text (e.g. {@code Order})
 * @param status               {@link ResolutionStatus#RESOLVED} only if every parameter type was resolved
 */
public record ExecutableSignature(List<String> erasedParameterTypes, ResolutionStatus status) {

    public ExecutableSignature {
        erasedParameterTypes = List.copyOf(erasedParameterTypes);
    }
}
