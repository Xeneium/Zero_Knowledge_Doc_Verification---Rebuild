package com.zkdoc.keys;

/**
 * Formal four-state cryptographic key lifecycle.
 *
 * <pre>
 *   ACTIVE ────► RETIRING ────► RETIRED ────► REVOKED
 *     │             │                       ▲
 *     └─────────────┴───────────────────────┘
 * </pre>
 *
 * - ACTIVE: Authorized for new digital signatures and verification.
 * - RETIRING: Grace period during key rotation. Signatures should transition to replacement key.
 * - RETIRED: Disallowed for new signatures. Trusted for historical verification of artifacts
 *            issued prior to retirement.
 * - REVOKED: Terminal invalidation due to suspected or confirmed key compromise / emergency action.
 *            Disallowed for signing and invalidates cryptographic trust.
 */
public enum KeyStatus {
    ACTIVE,
    RETIRING,
    RETIRED,
    REVOKED;

    /**
     * Whether a key in this state is authorized to produce new digital signatures.
     */
    public boolean canSign() {
        return this == ACTIVE;
    }

    /**
     * Whether a key in this state may be used to verify historical signatures,
     * subject to verification context time and compromise status.
     */
    public boolean canVerifyHistorical() {
        return this == ACTIVE || this == RETIRING || this == RETIRED;
    }

    /**
     * Validates whether a state transition from this status to {@code next} is permitted.
     */
    public boolean canTransitionTo(KeyStatus next) {
        if (this == next) {
            return true;
        }
        return switch (this) {
            case ACTIVE -> next == RETIRING || next == RETIRED || next == REVOKED;
            case RETIRING -> next == RETIRED || next == REVOKED;
            case RETIRED -> next == REVOKED; // Compromise discovered post-retirement
            case REVOKED -> false;          // Terminal state
        };
    }
}
