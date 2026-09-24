package com.java2okf.resolver;

import com.java2okf.model.ResolutionStatus;

/**
 * Result of resolving a reference to a method, constructor, or field.
 *
 * @param targetId   entity ID of the resolved member, or {@code null} unless {@link ResolutionStatus#RESOLVED}
 * @param targetName qualified member name when resolved; the source text otherwise
 * @param status     resolution outcome
 * @param reason     why resolution failed; {@code null} when resolved
 * @param ownerTypeId entity ID of the declaring type when resolved (also for library members,
 *                    whose type kind is otherwise unknown); {@code null} otherwise
 */
public record MemberResolution(String targetId, String targetName, ResolutionStatus status, String reason,
                               String ownerTypeId) {

    public static MemberResolution resolved(String targetId, String targetName, String ownerTypeId) {
        return new MemberResolution(targetId, targetName, ResolutionStatus.RESOLVED, null, ownerTypeId);
    }

    public static MemberResolution unresolved(String sourceText, String reason) {
        return new MemberResolution(null, sourceText, ResolutionStatus.UNRESOLVED, reason, null);
    }

    public static MemberResolution ambiguous(String sourceText, String reason) {
        return new MemberResolution(null, sourceText, ResolutionStatus.AMBIGUOUS, reason, null);
    }

    public boolean isResolved() {
        return status == ResolutionStatus.RESOLVED;
    }
}
