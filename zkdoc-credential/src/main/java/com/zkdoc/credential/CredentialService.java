package com.zkdoc.credential;

import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CredentialService {
    private final CredentialStore credentialStore;
    private final CanonicalCredentialEncoder encoder;
    private final CredentialSha256Hasher hasher;
    private final CredentialRsaPssSigner signer;

    public CredentialService(
            CredentialStore credentialStore,
            CanonicalCredentialEncoder encoder,
            CredentialSha256Hasher hasher,
            CredentialRsaPssSigner signer) {
        this.credentialStore = credentialStore;
        this.encoder = encoder;
        this.hasher = hasher;
        this.signer = signer;
    }

    public SignedCredential issue(Credential credential) {
        byte[] canonicalPayload = encoder.encode(credential);
        byte[] digest = hasher.hash(canonicalPayload);
        SignedCredential signedCredential = new SignedCredential(
                credential, canonicalPayload, digest, signer.sign(digest));
        credentialStore.save(signedCredential);
        return signedCredential;
    }

    public CredentialVerificationResult verify(UUID credentialId) {
        StoredCredential credential = credentialStore.findById(credentialId)
                .orElseThrow(() -> new CredentialNotFoundException(credentialId));
        boolean digestMatches = MessageDigest.isEqual(hasher.hash(credential.canonicalPayload()), credential.digest());
        return new CredentialVerificationResult(
                credentialId, digestMatches && signer.verify(credential.digest(), credential.signature()));
    }
}
