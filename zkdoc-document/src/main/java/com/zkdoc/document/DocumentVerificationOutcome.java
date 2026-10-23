package com.zkdoc.document;

import java.util.UUID;

/**
 * Authoritative, minimal-disclosure verification result for direct document uploads.
 * Discloses only cryptographic verification outcome and status, never leaking raw document bytes
 * or private holder identity.
 */
public record DocumentVerificationOutcome(
        DocumentVerificationStatus status,
        boolean verified,
        UUID documentId,
        String digest,
        boolean signatureValid,
        boolean documentActive,
        String message,
        long timestamp
) {
    public static DocumentVerificationOutcome verified(UUID documentId, String digest) {
        return new DocumentVerificationOutcome(
                DocumentVerificationStatus.VERIFIED,
                true,
                documentId,
                digest,
                true,
                true,
                "Document integrity, authoritative digital signature, and active registration verified successfully.",
                System.currentTimeMillis()
        );
    }

    public static DocumentVerificationOutcome integrityMismatch(UUID documentId, String digest) {
        return new DocumentVerificationOutcome(
                DocumentVerificationStatus.INTEGRITY_MISMATCH,
                false,
                documentId,
                digest,
                false,
                true,
                "Document content has been modified or does not match the registered cryptographic digest.",
                System.currentTimeMillis()
        );
    }

    public static DocumentVerificationOutcome invalidSignature(UUID documentId, String digest) {
        return new DocumentVerificationOutcome(
                DocumentVerificationStatus.INVALID_SIGNATURE,
                false,
                documentId,
                digest,
                false,
                true,
                "Cryptographic digital signature verification failed against trusted public keys.",
                System.currentTimeMillis()
        );
    }

    public static DocumentVerificationOutcome revoked(UUID documentId, String digest) {
        return new DocumentVerificationOutcome(
                DocumentVerificationStatus.REVOKED,
                false,
                documentId,
                digest,
                true,
                false,
                "Document registration has been revoked or deactivated by the issuer.",
                System.currentTimeMillis()
        );
    }

    public static DocumentVerificationOutcome notFound(String digest) {
        return new DocumentVerificationOutcome(
                DocumentVerificationStatus.NOT_FOUND,
                false,
                null,
                digest,
                false,
                false,
                "No registered document matching this cryptographic fingerprint was found in the registry.",
                System.currentTimeMillis()
        );
    }

    public static DocumentVerificationOutcome unableToVerify(String message) {
        return new DocumentVerificationOutcome(
                DocumentVerificationStatus.UNABLE_TO_VERIFY,
                false,
                null,
                null,
                false,
                false,
                message != null ? message : "Unable to complete cryptographic verification.",
                System.currentTimeMillis()
        );
    }
}
