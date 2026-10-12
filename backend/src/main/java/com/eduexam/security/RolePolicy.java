package com.eduexam.security;

import java.util.Set;

/** Central allow-list for roles accepted from persisted user records. */
public final class RolePolicy {
    private static final Set<String> SUPPORTED_ROLES = Set.of("admin", "teacher", "student");

    private RolePolicy() {
    }

    public static boolean isSupported(String role) {
        return role != null && SUPPORTED_ROLES.contains(role);
    }
}
