# Credential Authenticity & Integrity Model

**Document Version:** 1.0.0  
**Phase:** Phase 4 — Credential Issuance & RSA-PSS Trust Chain  
**Status:** Approved Security Specification  

---

## 1. Principles of Credential Authenticity

In verifiable credential verification, authenticity means that the verifiable credential was authored and cryptographically approved by a recognized authority without subsequent tampering.

The platform grounds authenticity in four pillars:
1. **Cryptographic Grounding:** Authenticity is tied to a valid RSASSA-PSS signature over a deterministic SHA-256 digest of `zkdoc-canonical-v1` bytes.
2. **Authority Grounding:** The issuer identity encoded in the credential is bound to an active domain record in `IssuerStore`.
3. **Key Trust Grounding:** The public key used to verify the signature exists in `KeyRegistry`, has owner equal to `issuerId`, and purpose equal to `CREDENTIAL_SIGNING`.
4. **Historical Verifiability:** Past credentials remain verifiable after key rotation (`RETIRED` state) as long as the key was active when issued, but become immediately invalid if the key is revoked (`REVOKED` state).

---

## 2. Invariant: Rejection of Database Column Trust

Database rows contain flags such as `status: ACTIVE`. However, a verifier must NEVER trust that a credential is valid simply because the database returned a row.
Verification must always:
- Recompute `SHA-256(stored.canonicalPayload())`.
- Confirm equality with `stored.digest()`.
- Decode the canonical payload to extract the issuer ID, timestamps, and attributes.
- Re-verify the RSASSA-PSS digital signature against the trusted public key registered to that issuer.
