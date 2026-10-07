# Credential Signing Architecture & RSASSA-PSS Baseline

**Document Version:** 1.0.0  
**Phase:** Phase 4 — Credential Issuance & RSA-PSS Trust Chain  
**Status:** Approved Architectural Specification  

---

## 1. Cryptographic Baseline Specification

The Zero-Knowledge Document Verification Platform mandates **RSASSA-PSS** in accordance with RFC 8017 / PKCS #1 v2.2.

| Parameter | Specification | Compliance Rationale |
|:---|:---|:---|
| **Signature Scheme** | RSASSA-PSS | Provably secure probabilistic signature padding; immune to Bleichenbacher oracle attacks |
| **Hash Function** | SHA-256 | FIPS 180-4 standard, 256-bit collision resistance |
| **Mask Generation Function** | MGF1 with SHA-256 | Strict alignment with digest algorithm |
| **Salt Length** | 32 bytes (256 bits) | Equal to hash length (`hLen = 32`), maximizing provable security bounds |
| **Trailer Field** | `0xBC` (Trailer Field 1) | RFC 8017 standard |
| **Modulus Size** | ≥ 2048 bits (3072+ recommended) | NIST SP 800-57 Part 1 Rev 5 minimum security strength |

---

## 2. Signing Invariants & Custody Abstraction

Digital signing operations never receive raw private keys through controllers or API DTOs.
All signing requests are routed through `KeyRegistryService.sign(keyId, purpose, expectedOwnerId, digest)`:

1. **Existence Check:** `keyRegistry.findByKeyId(keyId)` ensures the key is known.
2. **Purpose Verification:** `key.purpose() == KeyPurpose.CREDENTIAL_SIGNING`.
3. **Owner Verification:** `key.ownerId().equals(expectedOwnerId)`.
4. **Lifecycle Check:** `key.status().canSign()` ensures the key is currently `ACTIVE`. Keys in `RETIRING`, `RETIRED`, or `REVOKED` states are strictly forbidden from signing new credentials.
5. **Custody Resolution:** `signingKeyProvider.getPrivateKey(keyId, purpose)` retrieves the private key from local custody or external KMS.
6. **Execution:** `RsaPssSignatureService.signRsaPss(digest, privateKey)` executes the signature.
