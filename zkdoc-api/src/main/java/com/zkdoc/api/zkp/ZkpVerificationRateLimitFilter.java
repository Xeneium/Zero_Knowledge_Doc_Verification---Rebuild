package com.zkdoc.api.zkp;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defense-in-depth rate limiting filter specifically for CPU-intensive ZKP verification operations.
 * Protects against resource exhaustion and denial-of-service attempts by throttling requests
 * per authenticated user or remote IP address.
 */
public class ZkpVerificationRateLimitFilter extends OncePerRequestFilter {
    public static final String ZKP_VERIFY_PATH = "/api/v2/zkp/credential-predicates/verify";
    public static final int CAPACITY = 30;
    public static final int REFILL_TOKENS = 30;
    public static final Duration REFILL_DURATION = Duration.ofMinutes(1);

    private final com.zkdoc.api.ratelimit.RateLimitService rateLimitService;
    private final com.zkdoc.api.ratelimit.ClientIpResolver clientIpResolver;

    public ZkpVerificationRateLimitFilter(
            com.zkdoc.api.ratelimit.RateLimitService rateLimitService,
            com.zkdoc.api.ratelimit.ClientIpResolver clientIpResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
    }

    public ZkpVerificationRateLimitFilter() {
        this(new com.zkdoc.api.ratelimit.BucketRateLimitService(), new com.zkdoc.api.ratelimit.ClientIpResolver(false));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !ZKP_VERIFY_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String dimension = resolveRateLimitKey(request);
        com.zkdoc.api.ratelimit.RateLimitKey key = com.zkdoc.api.ratelimit.RateLimitKey.of(
                com.zkdoc.api.ratelimit.RateLimitCategory.ZKP_VERIFICATION, dimension);

        com.zkdoc.api.ratelimit.RateLimitDecision decision = rateLimitService.tryAcquire(key);

        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.getWriter().write("{\"error\":\"Too many verification requests. Rate limit exceeded.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveRateLimitKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }
        return "ip:" + clientIpResolver.resolveClientIp(request);
    }
}
