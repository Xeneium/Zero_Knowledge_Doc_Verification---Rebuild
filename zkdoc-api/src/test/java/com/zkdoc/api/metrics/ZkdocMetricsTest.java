package com.zkdoc.api.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ZkdocMetricsTest {

    private MeterRegistry meterRegistry;
    private ZkdocMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ZkdocMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Document upload metrics record success, failure, and timing")
    void testDocumentUploadMetrics() {
        metrics.recordDocumentUpload(true, Duration.ofMillis(120));
        metrics.recordDocumentUpload(false, Duration.ofMillis(50));

        double successCount = meterRegistry.get("zkdoc.document.uploads").tag("outcome", "success").counter().count();
        double failureCount = meterRegistry.get("zkdoc.document.uploads").tag("outcome", "failure").counter().count();
        long timerCount = meterRegistry.get("zkdoc.document.upload.duration").timer().count();

        assertThat(successCount).isEqualTo(1.0);
        assertThat(failureCount).isEqualTo(1.0);
        assertThat(timerCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Credential issuance and lifecycle transitions record bounded dimensions")
    void testCredentialMetrics() {
        metrics.recordCredentialIssued(Duration.ofMillis(250));
        metrics.recordCredentialTransition("SUSPENDED", "SUCCESS");
        metrics.recordCredentialTransition("ACTIVE", "SUCCESS");

        double issuedCount = meterRegistry.get("zkdoc.credential.issued").counter().count();
        assertThat(issuedCount).isEqualTo(1.0);

        double suspendedCount = meterRegistry.get("zkdoc.credential.transitions")
                .tag("target_status", "SUSPENDED")
                .tag("outcome", "SUCCESS")
                .counter().count();
        assertThat(suspendedCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Public verification records mode and outcome safely without sensitive tokens")
    void testPublicVerificationMetrics() {
        metrics.recordCapabilityCreated("SELECTIVE_DISCLOSURE");
        metrics.recordPublicVerification("MODE_B", "SUCCESS", Duration.ofMillis(30));

        double capCount = meterRegistry.get("zkdoc.verification.capability.created")
                .tag("mode", "SELECTIVE_DISCLOSURE")
                .counter().count();
        assertThat(capCount).isEqualTo(1.0);

        double verifyCount = meterRegistry.get("zkdoc.verification.public")
                .tag("mode", "MODE_B")
                .tag("outcome", "SUCCESS")
                .counter().count();
        assertThat(verifyCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("ZKP proof generation and verification timers and counters update accurately")
    void testZkpMetrics() {
        metrics.recordZkpProofGenerated(true, Duration.ofMillis(400));
        metrics.recordZkpProofVerified("SUCCESS", Duration.ofMillis(15));

        double proofGenSuccess = meterRegistry.get("zkdoc.zkp.proofs.generated")
                .tag("outcome", "SUCCESS")
                .counter().count();
        assertThat(proofGenSuccess).isEqualTo(1.0);

        double proofVerifySuccess = meterRegistry.get("zkdoc.zkp.proofs.verified")
                .tag("outcome", "SUCCESS")
                .counter().count();
        assertThat(proofVerifySuccess).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Abuse and security counters increment on rate limit blocks and auth failures")
    void testSecurityMetrics() {
        metrics.recordRateLimitBlocked("PUBLIC_VERIFICATION");
        metrics.recordAuthFailure();
        metrics.recordAccessDenied();

        double blocked = meterRegistry.get("zkdoc.ratelimit.blocked")
                .tag("category", "PUBLIC_VERIFICATION")
                .counter().count();
        assertThat(blocked).isEqualTo(1.0);

        double authFail = meterRegistry.get("zkdoc.security.auth.failure").counter().count();
        assertThat(authFail).isEqualTo(1.0);

        double denied = meterRegistry.get("zkdoc.security.access.denied").counter().count();
        assertThat(denied).isEqualTo(1.0);
    }
}
