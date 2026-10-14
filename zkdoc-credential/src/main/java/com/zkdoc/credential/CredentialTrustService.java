package com.zkdoc.credential;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CredentialTrustService implements CredentialTrustVerifier {
    private final CredentialStore credentialStore;
    private final CredentialSha256Hasher hasher;
    private final CredentialRsaPssSigner signer;
    private final Clock clock;

    @Autowired
    public CredentialTrustService(
            CredentialStore credentialStore,
            CredentialSha256Hasher hasher,
            CredentialRsaPssSigner signer,
            @Autowired(required = false) Clock clock) {
        this.credentialStore = credentialStore;
        this.hasher = hasher;
        this.signer = signer;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public CredentialTrustService(
            CredentialStore credentialStore,
            CredentialSha256Hasher hasher,
            CredentialRsaPssSigner signer) {
        this(credentialStore, hasher, signer, Clock.systemUTC());
    }

    @Override
    public boolean isActiveAndValid(UUID credentialId) {
        return credentialStore.findById(credentialId)
                .filter(this::isTrusted)
                .isPresent();
    }

    private boolean isTrusted(StoredCredential credential) {
        Instant now = clock.instant();
        return credential.status() == CredentialStatus.ACTIVE
                && now.isBefore(credential.expiresAt())
                && MessageDigest.isEqual(hasher.hash(credential.canonicalPayload()), credential.digest())
                && signer.verify(credential.digest(), credential.signature());
    }
}
