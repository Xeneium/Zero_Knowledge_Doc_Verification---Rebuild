package com.zkdoc.api.qr;

import java.util.Optional;

public interface VerificationCapabilityStore {
    void save(VerificationCapability capability);

    Optional<VerificationCapability> findByToken(String token);

    Optional<VerificationCapability> findById(CapabilityId id);

    void updateStatus(CapabilityId id, CapabilityStatus status, java.time.Instant revokedAt, String reason);
}
