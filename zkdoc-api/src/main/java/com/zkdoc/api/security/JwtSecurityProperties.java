package com.zkdoc.api.security;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration properties for JWT validation, issuer/audience policies,
 * algorithm restrictions, and clock-skew tolerance.
 */
@Component
@ConfigurationProperties(prefix = "zkdoc.security.jwt")
public class JwtSecurityProperties {

    /** Expected trusted issuer URI / identifier (validated against 'iss' claim). */
    private String issuer = "https://auth.zkdoc.internal";

    /** Expected trusted API audience (validated against 'aud' claim). */
    private String audience = "zkdoc-api";

    /** Strictly allowlisted JWS signing algorithms (default RS256, PS256). */
    private Set<String> allowedAlgorithms = Set.of("RS256", "PS256");

    /** Maximum allowed clock skew in seconds for timestamp validation. */
    private long clockSkewSeconds = 60;

    /** Whether to require strict audience match. */
    private boolean strictAudience = true;

    /** Whether to require strict issuer match. */
    private boolean strictIssuer = true;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Set<String> getAllowedAlgorithms() {
        return allowedAlgorithms;
    }

    public void setAllowedAlgorithms(Set<String> allowedAlgorithms) {
        this.allowedAlgorithms = allowedAlgorithms;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public boolean isStrictAudience() {
        return strictAudience;
    }

    public void setStrictAudience(boolean strictAudience) {
        this.strictAudience = strictAudience;
    }

    public boolean isStrictIssuer() {
        return strictIssuer;
    }

    public void setStrictIssuer(boolean strictIssuer) {
        this.strictIssuer = strictIssuer;
    }
}
