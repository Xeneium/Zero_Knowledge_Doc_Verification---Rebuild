# Credential Issuance Architecture & Lifecycle

**Document Version:** 1.0.0  
**Phase:** Phase 4 — Credential Issuance & RSA-PSS Trust Chain  
**Status:** Approved Architectural Specification  

---

## 1. Overview

Credential issuance is the core domain operation by which an authoritative issuing entity stamps identity or qualification claims into an immutable, deterministically formatted, and cryptographically signed verifiable credential.

The platform's credential issuance architecture enforces strict separation between:
1. **Authenticated Actor Authority:** Verification of the caller's role (`ROLE_ISSUER`) and identity match.
2. **Domain Credential Draft Validation:** Construction through `CredentialDraft` ensuring all invariants (identifiers, schema, non-empty attributes, unique attribute names, strict `expiresAt > issuedAt` temporal logic) are satisfied before serialization.
3. **Deterministic Canonicalization (`zkdoc-canonical-v1`):** Conversion of domain claims into standard Big-Endian binary bytes via `CanonicalCredentialEncoder`.
4. **SHA-256 Digesting:** Generating a fixed 32-byte cryptographic hash of canonical bytes via `CredentialSha256Hasher`.
5. **RSASSA-PSS Signing via Custody Abstraction:** Obtaining a digital signature through `KeyRegistryService` without exposing raw private key material to application domain logic.
6. **Immutable Persistence:** Storing `StoredCredential` records in relational storage alongside canonical bytes, digest, and digital signature.

---

## 2. Issuance Pipeline Flow

```text
[Caller / Client]
       │
       ▼ (1) POST /api/credentials (Bearer JWT)
[CredentialController]
       │
       ▼ (2) Validate Issuer Role & Identity Binding
[CredentialDraft] ───► Validated Credential Domain Entity
       │
       ▼ (3) Encode
[CanonicalCredentialEncoder] ───► zkdoc-canonical-v1 Binary Payload
       │
       ▼ (4) Digest
[CredentialSha256Hasher] ───► 32-byte SHA-256 Digest
       │
       ▼ (5) Sign
[KeyRegistryService] ───► Check Owner, Status, & Purpose (CREDENTIAL_SIGNING)
       │              └──► Call SigningKeyProvider (HSM / KMS / Dev)
       ▼ (6) Digital Signature
[CredentialStore] ───► Persist StoredCredential
       │
       ▼ (7) 201 Created Response
[Caller / Client] ◄─── (credentialId, digestHex)
```

---

## 3. Strict Boundary Rules

1. **No Ephemeral In-Memory Keys:** Production signing keys must never be generated on the fly inside constructors or services. All keys must be pre-registered with `KeyRegistry` and guarded by `SigningKeyProvider`.
2. **Key Purpose Separation:** Credential signing requires `KeyPurpose.CREDENTIAL_SIGNING`. Any attempt to use keys registered for `JWT_SIGNING`, `DOCUMENT_SIGNING`, or `COMMITMENT_ATTESTATION_SIGNING` fails closed immediately.
3. **Owner Identity Binding:** The key owner ID registered in `KeyRegistry` must match the credential's `issuerId`. Cross-issuer signing is cryptographically and logically prohibited.
4. **Immutable Representation:** The database row stores the exact canonical bytes signed by the issuer. If claims are modified, the signature is rendered invalid.
