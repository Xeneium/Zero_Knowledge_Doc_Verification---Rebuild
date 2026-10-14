package com.zkdoc.api.security;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable authenticated principal representing a verified caller.
 * Provides stable subject semantics, issuer/audience lineage, validated roles,
 * and fine-grained permissions without holding raw tokens or credentials.
 */
public record AuthenticatedUser(
        String subject,
        String issuer,
        List<String> audiences,
        Set<String> roles,
        Set<String> permissions) implements Principal {

    public AuthenticatedUser {
        Objects.requireNonNull(subject, "subject must not be null");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        issuer = issuer != null ? issuer : "unknown";
        audiences = audiences != null ? List.copyOf(audiences) : Collections.emptyList();
        roles = roles != null ? Set.copyOf(roles) : Collections.emptySet();
        permissions = permissions != null ? Set.copyOf(permissions) : Collections.emptySet();
    }

    @Override
    public String getName() {
        return subject;
    }

    public boolean hasRole(String role) {
        if (role == null) return false;
        String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;
        return roles.contains(cleanRole);
    }

    public boolean hasPermission(String permission) {
        if (permission == null) return false;
        return permissions.contains(permission);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
