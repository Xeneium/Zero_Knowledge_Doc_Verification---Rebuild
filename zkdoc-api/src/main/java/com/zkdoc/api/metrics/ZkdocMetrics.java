package com.zkdoc.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Production metrics facade for ZKDoc operations with bounded cardinality.
 * <p>
 * Strictly enforces that metrics dimensions are static enums or bounded strings,
 * never containing user IDs, document IDs, credential IDs, raw attributes,
 * tokens, or cryptographic secrets.
 */
@Component
public class ZkdocMetrics {

    private final MeterRegistry registry;

    // Document Processing Metrics
    private final Counter documentUploadSuccess;
    private final Counter documentUploadFailure;
    private final Timer documentUploadTimer;

    // Credential Issuance & Lifecycle Metrics
    private final Counter credentialIssuedCounter;
    private final Counter credentialTransitionCounter;
    private final Timer credentialIssuanceTimer;

    // Verification Metrics
    private final Counter verificationCapabilityCreated;
    private final Counter publicVerificationCounter;
    private final Timer publicVerificationTimer;

    // ZKP Metrics
    private final Counter zkpProofGeneratedCounter;
    private final Counter zkpProofVerifiedCounter;
    private final Timer zkpProofGenerationTimer;
    private final Timer zkpProofVerificationTimer;

    // Rate Limiting & Abuse
    private final Counter rateLimitBlockedCounter;

    // Security & Auth
    private final Counter authFailureCounter;
    private final Counter accessDeniedCounter;

    public ZkdocMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.documentUploadSuccess = Counter.builder("zkdoc.document.uploads")
                .tag("outcome", "success")
                .description("Total successful document uploads")
                .register(registry);

        this.documentUploadFailure = Counter.builder("zkdoc.document.uploads")
                .tag("outcome", "failure")
                .description("Total failed document uploads")
                .register(registry);

        this.documentUploadTimer = Timer.builder("zkdoc.document.upload.duration")
                .description("Time taken to process and store document uploads")
                .register(registry);

        this.credentialIssuedCounter = Counter.builder("zkdoc.credential.issued")
                .description("Total credentials issued")
                .register(registry);

        this.credentialTransitionCounter = Counter.builder("zkdoc.credential.transitions")
                .description("Total credential lifecycle state transitions")
                .register(registry);

        this.credentialIssuanceTimer = Timer.builder("zkdoc.credential.issue.duration")
                .description("Time taken to issue a signed credential")
                .register(registry);

        this.verificationCapabilityCreated = Counter.builder("zkdoc.verification.capability.created")
                .description("Total QR verification capabilities generated")
                .register(registry);

        this.publicVerificationCounter = Counter.builder("zkdoc.verification.public.total")
                .description("Total public verification requests")
                .register(registry);

        this.publicVerificationTimer = Timer.builder("zkdoc.verification.public.duration")
                .description("Time taken for public verification execution")
                .register(registry);

        this.zkpProofGeneratedCounter = Counter.builder("zkdoc.zkp.proofs.generated")
                .description("Total ZKP predicate proofs generated")
                .register(registry);

        this.zkpProofVerifiedCounter = Counter.builder("zkdoc.zkp.proofs.verified")
                .description("Total ZKP predicate proofs verified")
                .register(registry);

        this.zkpProofGenerationTimer = Timer.builder("zkdoc.zkp.generate.duration")
                .description("Time taken to generate ZKP predicate proof")
                .register(registry);

        this.zkpProofVerificationTimer = Timer.builder("zkdoc.zkp.verify.duration")
                .description("Time taken to verify ZKP predicate proof")
                .register(registry);

        this.rateLimitBlockedCounter = Counter.builder("zkdoc.ratelimit.blocked")
                .description("Total requests rejected due to rate limits")
                .register(registry);

        this.authFailureCounter = Counter.builder("zkdoc.security.auth.failure")
                .description("Total authentication failures")
                .register(registry);

        this.accessDeniedCounter = Counter.builder("zkdoc.security.access.denied")
                .description("Total access denied / authorization rejections")
                .register(registry);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    // --- Document Metrics ---

    public void recordDocumentUpload(boolean success, Duration duration) {
        if (success) {
            documentUploadSuccess.increment();
        } else {
            documentUploadFailure.increment();
        }
        if (duration != null) {
            documentUploadTimer.record(duration);
        }
    }

    public <T> T timeDocumentUpload(Callable<T> task) throws Exception {
        return documentUploadTimer.recordCallable(task);
    }

    // --- Credential Metrics ---

    public void recordCredentialIssued(Duration duration) {
        credentialIssuedCounter.increment();
        if (duration != null) {
            credentialIssuanceTimer.record(duration);
        }
    }

    public void recordCredentialTransition(String targetStatus, String outcome) {
        String safeStatus = (targetStatus != null) ? targetStatus.toUpperCase() : "UNKNOWN";
        String safeOutcome = (outcome != null) ? outcome.toUpperCase() : "UNKNOWN";
        registry.counter("zkdoc.credential.transitions", "target_status", safeStatus, "outcome", safeOutcome)
                .increment();
    }

    public <T> T timeCredentialIssuance(Callable<T> task) throws Exception {
        return credentialIssuanceTimer.recordCallable(task);
    }

    // --- Verification Metrics ---

    public void recordCapabilityCreated(String mode) {
        String safeMode = (mode != null) ? mode.toUpperCase() : "UNKNOWN";
        registry.counter("zkdoc.verification.capability.created", "mode", safeMode).increment();
    }

    public void recordPublicVerification(String mode, String outcome, Duration duration) {
        String safeMode = (mode != null) ? mode.toUpperCase() : "UNKNOWN";
        String safeOutcome = (outcome != null) ? outcome.toUpperCase() : "UNKNOWN";
        registry.counter("zkdoc.verification.public", "mode", safeMode, "outcome", safeOutcome).increment();
        if (duration != null) {
            publicVerificationTimer.record(duration);
        }
    }

    // --- ZKP Metrics ---

    public void recordZkpProofGenerated(boolean success, Duration duration) {
        registry.counter("zkdoc.zkp.proofs.generated", "outcome", success ? "SUCCESS" : "FAILURE").increment();
        if (duration != null) {
            zkpProofGenerationTimer.record(duration);
        }
    }

    public void recordZkpProofVerified(String outcome, Duration duration) {
        String safeOutcome = (outcome != null) ? outcome.toUpperCase() : "UNKNOWN";
        registry.counter("zkdoc.zkp.proofs.verified", "outcome", safeOutcome).increment();
        if (duration != null) {
            zkpProofVerificationTimer.record(duration);
        }
    }

    // --- Rate Limit & Abuse Metrics ---

    public void recordRateLimitBlocked(String category) {
        String safeCat = (category != null) ? category.toUpperCase() : "UNKNOWN";
        registry.counter("zkdoc.ratelimit.blocked", "category", safeCat).increment();
    }

    // --- Security & Auth Metrics ---

    public void recordAuthFailure() {
        authFailureCounter.increment();
    }

    public void recordAccessDenied() {
        accessDeniedCounter.increment();
    }
}
