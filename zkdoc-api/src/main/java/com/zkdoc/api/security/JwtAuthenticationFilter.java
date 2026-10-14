package com.zkdoc.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Production-hardened JWT authentication filter.
 * Validates bearer tokens using {@link ManagedJwtDecoderProvider}, enforces role/permission
 * allowlists via {@link JwtAuthoritiesConverter}, populates a safe {@link AuthenticatedUser} principal,
 * and ensures raw tokens or credentials are never retained in the security context.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final ManagedJwtDecoderProvider decoderProvider;
    private final JwtAuthoritiesConverter authoritiesConverter;
    private final com.zkdoc.api.audit.SecurityAuditService auditService;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthenticationFilter(
            ManagedJwtDecoderProvider decoderProvider,
            JwtAuthoritiesConverter authoritiesConverter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.audit.SecurityAuditService auditService) {
        this.decoderProvider = Objects.requireNonNull(decoderProvider, "decoderProvider must not be null");
        this.authoritiesConverter = Objects.requireNonNull(authoritiesConverter, "authoritiesConverter must not be null");
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            recordAuthFailure(request, "EMPTY_TOKEN");
            sendUnauthorizedError(response, "Bearer token is empty", request.getRequestURI());
            return;
        }

        try {
            Jwt jwt = decoderProvider.decode(token);
            Set<String> roles = authoritiesConverter.extractRoles(jwt);
            Set<String> permissions = authoritiesConverter.extractPermissions(jwt, roles);
            Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);

            AuthenticatedUser principal = new AuthenticatedUser(
                    jwt.getSubject(),
                    jwt.getClaimAsString("iss"),
                    jwt.getAudience(),
                    roles,
                    permissions
            );

            // Null credentials to avoid memory/logging exposure of the raw bearer token
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
            log.warn("JWT authentication rejected for URI={}: {}", request.getRequestURI(), exception.getMessage());
            recordAuthFailure(request, "INVALID_TOKEN");
            sendUnauthorizedError(response, "Invalid or unauthorized bearer token", request.getRequestURI());
        }
    }

    private void recordAuthFailure(HttpServletRequest request, String reasonCode) {
        if (auditService != null) {
            String correlationId = org.slf4j.MDC.get(com.zkdoc.api.logging.MdcCorrelationIdFilter.MDC_KEY);
            com.zkdoc.api.audit.AuditEventRequest auditReq = com.zkdoc.api.audit.AuditEventRequest
                    .builder(com.zkdoc.api.audit.AuditEventType.AUTHENTICATION_FAILURE)
                    .actor("anonymous", com.zkdoc.api.audit.ActorType.ANONYMOUS_PUBLIC)
                    .target(com.zkdoc.api.audit.TargetType.USER, request.getRequestURI())
                    .action("JWT_AUTHENTICATE")
                    .outcome(com.zkdoc.api.audit.AuditOutcome.FAILURE)
                    .reasonCode(reasonCode)
                    .correlationId(correlationId)
                    .clientIp(request.getRemoteAddr())
                    .build();
            auditService.record(auditReq);
        }
    }

    private void sendUnauthorizedError(HttpServletResponse response, String message, String path) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"path\":\"%s\"}",
                escapeJson(message), escapeJson(path)));
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
