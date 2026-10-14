package com.zkdoc.api.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic canonical serialization and cryptographic hash chaining
 * for tamper-evident audit log integrity.
 */
public final class AuditCanonicalizer {

    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    public static final String DOMAIN_PREFIX = "ZKDOC-AUDIT-V1";

    private AuditCanonicalizer() {}

    /**
     * Constructs the canonical text representation of an audit entry.
     * Uses explicit pipe-delimited fields with null-safe empty string substitution.
     */
    public static String canonicalize(
            long sequenceNumber,
            UUID eventId,
            AuditEventType eventType,
            AuditEventCategory eventCategory,
            Instant timestamp,
            String actorSubject,
            ActorType actorType,
            TargetType targetType,
            String targetId,
            String action,
            AuditOutcome outcome,
            String reasonCode,
            String correlationId,
            String clientIpHash,
            String metadataJson) {

        StringBuilder sb = new StringBuilder(512);
        sb.append(DOMAIN_PREFIX).append('|')
          .append(sequenceNumber).append('|')
          .append(eventId).append('|')
          .append(eventType.name()).append('|')
          .append(eventCategory.name()).append('|')
          .append(timestamp.toString()).append('|')
          .append(actorSubject).append('|')
          .append(actorType.name()).append('|')
          .append(targetType != null ? targetType.name() : "").append('|')
          .append(targetId != null ? targetId : "").append('|')
          .append(action).append('|')
          .append(outcome.name()).append('|')
          .append(reasonCode != null ? reasonCode : "").append('|')
          .append(correlationId).append('|')
          .append(clientIpHash).append('|')
          .append(metadataJson != null ? metadataJson : "");
        return sb.toString();
    }

    /**
     * Computes the SHA-256 event integrity hash chaining from the previous hash.
     * Hash = SHA-256(previousEventHash + "|" + canonicalString)
     */
    public static String computeHash(String previousEventHash, String canonicalString) {
        Objects.requireNonNull(previousEventHash, "previousEventHash must not be null");
        Objects.requireNonNull(canonicalString, "canonicalString must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(previousEventHash.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(canonicalString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Verifies that an event's hash matches its recomputed hash from expected previous hash.
     */
    public static boolean verify(SecurityAuditEvent event, String expectedPreviousHash) {
        if (!event.previousEventHash().equalsIgnoreCase(expectedPreviousHash)) {
            return false;
        }
        String canonical = canonicalize(
                event.sequenceNumber(),
                event.eventId(),
                event.eventType(),
                event.eventCategory(),
                event.timestamp(),
                event.actorSubject(),
                event.actorType(),
                event.targetType(),
                event.targetId(),
                event.action(),
                event.outcome(),
                event.reasonCode(),
                event.correlationId(),
                event.clientIpHash(),
                event.metadataJson());
        String recomputed = computeHash(expectedPreviousHash, canonical);
        return recomputed.equalsIgnoreCase(event.eventHash());
    }
}
