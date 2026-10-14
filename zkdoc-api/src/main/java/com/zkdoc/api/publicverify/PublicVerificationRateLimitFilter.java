package com.zkdoc.api.publicverify;

import com.zkdoc.api.ratelimit.ClientIpResolver;
import com.zkdoc.api.ratelimit.RateLimitCategory;
import com.zkdoc.api.ratelimit.RateLimitDecision;
import com.zkdoc.api.ratelimit.RateLimitKey;
import com.zkdoc.api.ratelimit.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Legacy rate limit filter preserved for backwards compatibility.
 * Delegates to centralized RateLimitService.
 */
public class PublicVerificationRateLimitFilter extends OncePerRequestFilter {
    public static final String PUBLIC_VERIFY_PATH = "/api/public/verify/";
    public static final int CAPACITY = RateLimitCategory.PUBLIC_VERIFICATION.getCapacity();

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    public PublicVerificationRateLimitFilter(RateLimitService rateLimitService, ClientIpResolver clientIpResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
    }

    public PublicVerificationRateLimitFilter() {
        this(new com.zkdoc.api.ratelimit.BucketRateLimitService(), new ClientIpResolver(false));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().startsWith(PUBLIC_VERIFY_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = clientIpResolver.resolveClientIp(request);
        RateLimitKey key = RateLimitKey.of(RateLimitCategory.PUBLIC_VERIFICATION, "ip:" + clientIp);
        RateLimitDecision decision = rateLimitService.tryAcquire(key);

        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.getWriter().write(String.format(
                    "{\"error\":\"Too Many Requests\",\"message\":\"%s\",\"category\":\"%s\"}",
                    decision.reason() != null ? decision.reason() : "Rate limit exceeded",
                    decision.category().name()));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
