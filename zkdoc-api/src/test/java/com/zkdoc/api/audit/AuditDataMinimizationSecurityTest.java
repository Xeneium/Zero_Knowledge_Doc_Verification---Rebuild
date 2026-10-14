package com.zkdoc.api.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditDataMinimizationSecurityTest {

    @Test
    @DisplayName("JWT tokens in metadata are strictly rejected")
    void jwtTokenInMetadataRejected() {
        String leakedJwt = "{\"token\":\"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c\"}";
        assertThatThrownBy(() -> AuditDataMinimizer.sanitizeMetadata(leakedJwt))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Prohibited secret pattern detected");
    }

    @Test
    @DisplayName("Bearer authorization headers in metadata are strictly rejected")
    void bearerHeaderInMetadataRejected() {
        String leakedBearer = "{\"auth\":\"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9\"}";
        assertThatThrownBy(() -> AuditDataMinimizer.sanitizeMetadata(leakedBearer))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Private key headers in metadata are strictly rejected")
    void privateKeyHeaderInMetadataRejected() {
        String leakedKey = "{\"key\":\"-----BEGIN RSA PRIVATE KEY-----\\nMIIEowIBAAKCAQEA0...\"}";
        assertThatThrownBy(() -> AuditDataMinimizer.sanitizeMetadata(leakedKey))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Log injection CRLF characters are stripped and replaced with spaces")
    void logInjectionCrlfSanitized() {
        String maliciousInput = "admin-user\r\nINJECTED_LOG_ENTRY: FAKE_SUCCESS\r\n";
        String sanitized = AuditDataMinimizer.sanitizeString(maliciousInput, 128);

        assertThat(sanitized).doesNotContain("\r");
        assertThat(sanitized).doesNotContain("\n");
        assertThat(sanitized).isEqualTo("admin-user  INJECTED_LOG_ENTRY: FAKE_SUCCESS");
    }

    @Test
    @DisplayName("Raw client IP addresses are hashed and never stored directly")
    void rawClientIpHashed() {
        String rawIp = "192.168.1.100";
        String hash = AuditDataMinimizer.hashClientIp(rawIp);

        assertThat(hash).isNotNull();
        assertThat(hash).doesNotContain("192.168.1.100");
        assertThat(hash).hasSize(64); // SHA-256 hex length

        // Same IP produces identical pseudonymized correlation hash
        String hash2 = AuditDataMinimizer.hashClientIp(rawIp);
        assertThat(hash).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Oversized string inputs are cleanly bounded to maximum allowed length")
    void oversizedInputsAreBounded() {
        String veryLongString = "A".repeat(300);
        String bounded = AuditDataMinimizer.sanitizeString(veryLongString, 64);

        assertThat(bounded).hasSize(64);
    }
}
