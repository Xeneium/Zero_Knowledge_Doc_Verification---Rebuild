package com.zkdoc.api.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP address considering proxy trust boundaries.
 * In production, X-Forwarded-For is only parsed if trusted proxy mode is explicitly enabled.
 * Blindly trusting arbitrary headers is strictly prevented to defend against IP spoofing.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedHeaders;

    public ClientIpResolver(
            @Value("${zkdoc.security.rate-limit.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // First IP in chain is client IP from trusted gateway
                String clientIp = forwarded.split(",")[0].trim();
                return NetworkAddressNormalizer.normalize(clientIp);
            }

            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return NetworkAddressNormalizer.normalize(realIp.trim());
            }
        }

        return NetworkAddressNormalizer.normalize(request.getRemoteAddr());
    }

    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }
}
