package com.zkdoc.api.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter enforcing endpoint-aware rate limiting across public verification and high-abuse endpoints.
 * Operates before expensive parsing or cryptographic validation.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final com.zkdoc.api.audit.SecurityAuditService auditService;
    private final com.zkdoc.api.metrics.ZkdocMetrics zkdocMetrics;

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimitingFilter(
            RateLimitService rateLimitService,
            ClientIpResolver clientIpResolver,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.audit.SecurityAuditService auditService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.metrics.ZkdocMetrics zkdocMetrics) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.auditService = auditService;
        this.zkdocMetrics = zkdocMetrics;
    }

    public RateLimitingFilter(RateLimitService rateLimitService, ClientIpResolver clientIpResolver) {
        this(rateLimitService, clientIpResolver, null, null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        // 1. Check if public verification endpoint (/api/public/verify/**)
        if (path.startsWith("/api/public/verify")) {
            String clientIp = clientIpResolver.resolveClientIp(request);
            RateLimitKey key = RateLimitKey.of(RateLimitCategory.PUBLIC_VERIFICATION, "ip:" + clientIp);
            RateLimitDecision decision = rateLimitService.tryAcquire(key);

            if (!decision.allowed()) {
                writeRateLimitError(request, response, decision);
                return;
            }
        }

        // 2. Check if ZKP verification endpoint (/api/v2/zkp/credential-predicates/verify)
        else if (path.equals("/api/v2/zkp/credential-predicates/verify") && "POST".equalsIgnoreCase(request.getMethod())) {
            String caller = resolveCallerIdentity(request);
            RateLimitKey key = RateLimitKey.of(RateLimitCategory.ZKP_VERIFICATION, caller);
            RateLimitDecision decision = rateLimitService.tryAcquire(key);

            if (!decision.allowed()) {
                writeRateLimitError(request, response, decision);
                return;
            }
        }

        // 3. Check if holder proof generation (/api/holder/credentials/proofs/generate)
        else if (path.equals("/api/holder/credentials/proofs/generate") && "POST".equalsIgnoreCase(request.getMethod())) {
            String caller = resolveCallerIdentity(request);
            RateLimitKey key = RateLimitKey.of(RateLimitCategory.ZKP_PROOF_GENERATION, caller);
            RateLimitDecision decision = rateLimitService.tryAcquire(key);

            if (!decision.allowed()) {
                writeRateLimitError(request, response, decision);
                return;
            }
        }

        // 4. Check if document upload (/api/documents)
        else if (path.equals("/api/documents") && "POST".equalsIgnoreCase(request.getMethod())) {
            String caller = resolveCallerIdentity(request);
            RateLimitKey key = RateLimitKey.of(RateLimitCategory.DOCUMENT_UPLOAD, caller);
            RateLimitDecision decision = rateLimitService.tryAcquire(key);

            if (!decision.allowed()) {
                writeRateLimitError(request, response, decision);
                return;
            }
        }

        // 5. Check if credential issuance (/api/credentials)
        else if (path.equals("/api/credentials") && "POST".equalsIgnoreCase(request.getMethod())) {
            String caller = resolveCallerIdentity(request);
            RateLimitKey key = RateLimitKey.of(RateLimitCategory.CREDENTIAL_ISSUANCE, caller);
            RateLimitDecision decision = rateLimitService.tryAcquire(key);

            if (!decision.allowed()) {
                writeRateLimitError(request, response, decision);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveCallerIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }
        return "ip:" + clientIpResolver.resolveClientIp(request);
    }

    private void writeRateLimitError(HttpServletRequest request, HttpServletResponse response, RateLimitDecision decision) throws IOException {
        if (auditService != null) {
            String caller = resolveCallerIdentity(request);
            String correlationId = org.slf4j.MDC.get(com.zkdoc.api.logging.MdcCorrelationIdFilter.MDC_KEY);
            com.zkdoc.api.audit.AuditEventRequest auditReq = com.zkdoc.api.audit.AuditEventRequest
                    .builder(com.zkdoc.api.audit.AuditEventType.RATE_LIMIT_TRIGGERED)
                    .actor(caller, caller.startsWith("user:") ? com.zkdoc.api.audit.ActorType.USER : com.zkdoc.api.audit.ActorType.ANONYMOUS_PUBLIC)
                    .target(com.zkdoc.api.audit.TargetType.SYSTEM, request.getRequestURI())
                    .action("RATE_LIMIT_CHECK")
                    .outcome(com.zkdoc.api.audit.AuditOutcome.DENIED)
                    .reasonCode(decision.category().name())
                    .correlationId(correlationId)
                    .clientIp(clientIpResolver.resolveClientIp(request))
                    .metadataJson(String.format("{\"category\":\"%s\",\"retryAfterSeconds\":%d}",
                            decision.category().name(), decision.retryAfterSeconds()))
                    .build();
            auditService.record(auditReq);
        }

        if (zkdocMetrics != null) {
            zkdocMetrics.recordRateLimitBlocked(decision.category().name());
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.getWriter().write(String.format(
                "{\"error\":\"Too Many Requests\",\"message\":\"%s\",\"category\":\"%s\"}",
                decision.reason() != null ? decision.reason() : "Rate limit exceeded",
                decision.category().name()));
    }
}
