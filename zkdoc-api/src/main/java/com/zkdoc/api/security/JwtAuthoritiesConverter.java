package com.zkdoc.api.security;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Controlled JWT claim-to-authority converter.
 * Enforces strict role allowlisting, role escalation defenses, and mapping from
 * coarse roles to fine-grained domain permissions.
 */
@Component
public class JwtAuthoritiesConverter {

    public static final Set<String> ALLOWED_ROLES = Set.of("ISSUER", "HOLDER", "VERIFIER", "ADMIN");

    public static final String PERM_DOCUMENT_UPLOAD = "document:upload";
    public static final String PERM_DOCUMENT_VERIFY = "document:verify";
    public static final String PERM_CREDENTIAL_ISSUE = "credential:issue";
    public static final String PERM_CREDENTIAL_VERIFY = "credential:verify";
    public static final String PERM_ZKP_VERIFY = "zkp:verify";
    public static final String PERM_CAPABILITY_MINT = "capability:mint";
    public static final String PERM_CAPABILITY_REVOKE = "capability:revoke";
    public static final String PERM_KEYS_MANAGE = "keys:manage";
    public static final String PERM_AUDIT_READ = "audit:read";

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "ADMIN", Set.of(
                    PERM_DOCUMENT_UPLOAD, PERM_DOCUMENT_VERIFY,
                    PERM_CREDENTIAL_ISSUE, PERM_CREDENTIAL_VERIFY,
                    PERM_ZKP_VERIFY, PERM_CAPABILITY_MINT, PERM_CAPABILITY_REVOKE,
                    PERM_KEYS_MANAGE, PERM_AUDIT_READ),
            "ISSUER", Set.of(
                    PERM_DOCUMENT_UPLOAD, PERM_DOCUMENT_VERIFY,
                    PERM_CREDENTIAL_ISSUE, PERM_CREDENTIAL_VERIFY,
                    PERM_CAPABILITY_MINT, PERM_CAPABILITY_REVOKE),
            "HOLDER", Set.of(
                    PERM_CREDENTIAL_VERIFY, PERM_CAPABILITY_MINT, PERM_CAPABILITY_REVOKE),
            "VERIFIER", Set.of(
                    PERM_DOCUMENT_VERIFY, PERM_CREDENTIAL_VERIFY, PERM_ZKP_VERIFY)
    );

    /**
     * Extracts validated roles from JWT claims.
     */
    public Set<String> extractRoles(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        // 1. Check 'roles' claim
        Object rolesClaim = jwt.getClaims().get("roles");
        if (rolesClaim instanceof Collection<?> collection) {
            for (Object obj : collection) {
                if (obj instanceof String role && ALLOWED_ROLES.contains(role)) {
                    roles.add(role);
                }
            }
        } else if (rolesClaim instanceof String role && ALLOWED_ROLES.contains(role)) {
            roles.add(role);
        }

        // 2. Check 'role' claim (singular)
        Object singleRoleClaim = jwt.getClaims().get("role");
        if (singleRoleClaim instanceof String role && ALLOWED_ROLES.contains(role)) {
            roles.add(role);
        } else if (singleRoleClaim instanceof Collection<?> collection) {
            for (Object obj : collection) {
                if (obj instanceof String role && ALLOWED_ROLES.contains(role)) {
                    roles.add(role);
                }
            }
        }

        return Collections.unmodifiableSet(roles);
    }

    /**
     * Extracts and maps permissions from explicit claims and role definitions.
     */
    public Set<String> extractPermissions(Jwt jwt, Set<String> roles) {
        Set<String> permissions = new HashSet<>();

        // Map canonical permissions for assigned roles
        for (String role : roles) {
            Set<String> rolePerms = ROLE_PERMISSIONS.get(role);
            if (rolePerms != null) {
                permissions.addAll(rolePerms);
            }
        }

        // Add explicit permissions if present in token
        Object permsClaim = jwt.getClaims().get("permissions");
        if (permsClaim instanceof Collection<?> collection) {
            for (Object obj : collection) {
                if (obj instanceof String perm && !perm.isBlank()) {
                    permissions.add(perm.trim());
                }
            }
        }

        return Collections.unmodifiableSet(permissions);
    }

    /**
     * Converts JWT into Spring Security GrantedAuthority instances.
     */
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = extractRoles(jwt);
        Set<String> permissions = extractPermissions(jwt, roles);

        Set<GrantedAuthority> authorities = new HashSet<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        for (String perm : permissions) {
            authorities.add(new SimpleGrantedAuthority("PERM_" + perm));
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + perm));
        }

        return authorities;
    }
}
