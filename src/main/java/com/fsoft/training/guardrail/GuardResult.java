package com.fsoft.training.guardrail;

/** Result of {@link SqlGuard#check(String)}. {@code reason} is Vietnamese, user-facing. */
public record GuardResult(boolean allowed, String safeSql, String reason) {

    public static GuardResult allowed(String safeSql) {
        return new GuardResult(true, safeSql, null);
    }

    public static GuardResult blocked(String reason) {
        return new GuardResult(false, null, reason);
    }
}
