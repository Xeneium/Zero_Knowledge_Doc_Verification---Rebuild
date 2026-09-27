package com.zkdoc.credential;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CredentialTrustService implements CredentialTrustVerifier {
    private final CredentialStore credentialStore;
    private final CredentialSha256Hasher hasher;
    private final CredentialRsaPssSigner signer;

    public CredentialTrustService(
            CredentialStore credentialStore, CredentialSha256Hasher hasher, CredentialRsaPssSigner signer) {
        this.credentialStore = credentialStore;
        this.hasher = hasher;
        this.signer = signer;
    }

    @Override
    public boolean isActiveAndValid(UUID credentialId) {
        return credentialStore.findById(credentialId)
                .filter(this::isTrusted)
                .isPresent();
    }

    private boolean isTrusted(StoredCredential credential) {
        return credential.status() == CredentialStatus.ACTIVE
                && credential.expiresAt().isAfter(Instant.now())
                && MessageDigest.isEqual(hasher.hash(credential.canonicalPayload()), credential.digest())
                && signer.verify(credential.digest(), credential.signature());
    }
}
