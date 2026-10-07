# Cryptographic Key Security Policy & Threat Analysis

## 1. Threat Vectors & Mitigations

### 1.1 Purpose Confusion & Cross-Protocol Forgery
- **Threat:** An attacker obtains a signature valid under one platform protocol (e.g., an authentication JWT or attribute commitment attestation) and replays it as a signature over a legal document or credential.
- **Mitigation:** Strict `KeyPurpose` separation enforced at both signing time (`SigningKeyProvider.getSigningKey(keyId, purpose)`) and verification time (`KeyTrustResolver.resolveTrust(...)`). Cryptographic payloads also incorporate domain-separated magic headers (e.g. `ZKDC` for canonical credentials).

### 1.2 Key Substitution & Rogue Key Registration
- **Threat:** An attacker generates their own key pair, registers the public key under an arbitrary or spoofed name, and presents signed documents claiming to originate from an authentic issuer.
- **Mitigation:** Public keys must be registered with an accredited `ownerId` and `ownerType`. Verification requires the caller to specify the expected `ownerId` (e.g., matching the verifiable credential's canonical `issuerId`). Key substitution fails closed during owner matching.

### 1.3 Key Reuse Across Issuer Domains
- **Threat:** Multiple issuers share or reuse a single key pair, leading to cross-issuer impersonation and repudiation.
- **Mitigation:** The trust registry models `ownerId` as a strict partition key. `findActiveForSigning(purpose, ownerId)` isolates keys by owner, preventing one issuer from signing with another's key.

### 1.4 Private Key Exposure in Storage or Memory
- **Threat:** Private keys are accidentally serialized to JSON responses, logged in debug statements, written to disk in plain text, or stored in relational database tables alongside public keys.
- **Mitigation:** 
  - `RegisteredKey` contains no private key fields whatsoever.
  - Database schema contains columns exclusively for `public_key_pem`, `public_key_der`, and metadata.
  - Private key custody is fully decoupled behind `SigningKeyProvider`.
  - In development, key pairs are verified in memory with non-exportable references. In production, keys reside in KMS or HSM.

### 1.5 Padding Oracle and Weak RSA Vulnerabilities
- **Threat:** Use of RSA PKCS#1 v1.5 padding leaves signatures vulnerable to Bleichenbacher-style chosen-ciphertext attacks; weak 1024-bit moduli allow factorization by attackers.
- **Mitigation:** 
  - Strict enforcement of RSASSA-PSS (RFC 8017) with SHA-256 and 32-byte salt.
  - `KeyAlgorithmPolicy` enforces a strict minimum RSA key length of 2048 bits, immediately rejecting sub-2048-bit keys at registration and verification boundaries.

---

## 2. Cryptographic Invariants & Defensive Controls

1. **Fail-Closed Default:** Any missing parameter, unresolvable key ID, unexpected algorithm, mismatched owner, or unparseable public key results in an immediate `SecurityException` or verification rejection.
2. **Deterministic Fingerprints:** Every public key is indexed and validated by its SHA-256 fingerprint over standard SubjectPublicKeyInfo DER encoding.
3. **Immutable Lifecycle Audit:** Every key registration, rotation, and revocation creates an append-only entry in `key_lifecycle_audit_events`.
