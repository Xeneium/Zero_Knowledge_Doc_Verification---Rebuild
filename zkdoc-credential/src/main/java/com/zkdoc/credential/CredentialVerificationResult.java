package com.zkdoc.credential;

import java.util.UUID;

/**
 * Detailed cryptographic and operational verification result for an issued credential.
 * Encapsulates the complete Phase 4 trust evaluation chain:
 * - Recomputed deterministic canonical digest match
 * - RSASSA-PSS digital signature verification against authoritative public key
 * - Issuer existence, authorization, and active status check
 * - Key registry trust anchor validity and purpose verification
 * - Credential operational lifecycle and expiration check
 */
public record CredentialVerificationResult(
        UUID credentialId,
        boolean verified,
        boolean digestMatches,
        boolean signatureValid,
        boolean issuerActive,
        boolean keyTrusted,
        boolean operationalStatusValid,
        String failureReason) {

    public static CredentialVerificationResult success(UUID credentialId) {
        return new CredentialVerificationResult(
                credentialId,
                true,
                true,
                true,
                true,
                true,
                true,
                null);
    }

    public static CredentialVerificationResult failure(
            UUID credentialId,
            boolean digestMatches,
            boolean signatureValid,
            boolean issuerActive,
            boolean keyTrusted,
            boolean operationalStatusValid,
            String failureReason) {
        return new CredentialVerificationResult(
                credentialId,
                false,
                digestMatches,
                signatureValid,
                issuerActive,
                keyTrusted,
                operationalStatusValid,
                failureReason);
    }
}
