# Zero-Knowledge Document Verification Platform
## Phase 8B: Secure Predicate Verification API & Adversarial Verification Boundary

**Document Version:** 1.0.0  
**Phase:** Phase 8B — Secure Predicate Verification API & Adversarial Verification Boundary  
**Status:** Approved Implementation Baseline  

---

### 1. Architectural Scope & Objectives

Phase 8B exposes and secures the predicate-proof verification boundary against external callers and potential adversaries. The objective is to provide a robust, rate-limited, and privacy-preserving verification interface (`POST /api/v2/zkp/credential-predicates/verify`) that evaluates Bulletproof range proofs against authoritative server-resolved cryptographic commitments.

Key security guarantees implemented in this phase:
1. **Zero Caller Control Over Trust Metadata:** Verifier requests contain only identifier parameters (`proofVersion`, `credentialId`, `attributeName`, `threshold`, `nonce`, `proofBytes`). No commitment points, digests, keys, attestations, raw attributes, or witness variables are accepted from the caller.
2. **Strict Schema & Bounds Enforcement:** Payloads with unknown fields or unbounded values (e.g., proof size $>64\text{ KB}$, nonce size outside $[1, 64]$ bytes, negative threshold) are rejected with HTTP 400 Bad Request before hitting the cryptographic engine.
3. **Denial-of-Service / Resource-Exhaustion Protection:** Token-bucket rate limiting (`ZkpVerificationRateLimitFilter` backed by Bucket4j) throttles CPU-intensive verification requests per authenticated user / remote IP to prevent verification queue saturation.
4. **Authoritative Trust Resolution:** Verification resolves the credential's lifecycle state (`ACTIVE`), expiration timestamp, canonical payload digest, RSA-PSS signature, and the attribute commitment's cryptographic attestation before Bulletproof proof evaluation.
5. **Thread-Safe & Isolated Concurrency:** High-throughput concurrent verifications operate independently without shared mutable state or security context bleed.

---

### 2. Verification API Specification

#### Endpoint
`POST /api/v2/zkp/credential-predicates/verify`

#### Authorization
Requires role `ROLE_VERIFIER` via JWT authentication.

#### Request Schema
```json
{
  "proofVersion": "v1.0",
  "credentialId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "attributeName": "age",
  "threshold": 18,
  "nonce": "base64EncodedNonce",
  "proofBytes": "base64EncodedBulletproof"
}
```

#### Strict Field Policy
Any extraneous field in the request JSON (such as `"commitment"`, `"blindingFactor"`, `"value"`, or `"attestation"`) causes immediate rejection with HTTP 400 Bad Request.

#### Response Schema
```json
{
  "verified": true
}
```
* **Data Minimization:** Response discloses only whether the predicate mathematically holds and the credential is valid (`verified: boolean`). No internal keys, blinding factors, or raw attribute values are ever leaked.

---

### 3. Rate Limiting Architecture

To guard against algorithmic complexity and CPU exhaustion attacks from proof verification requests:
* **Filter:** `ZkpVerificationRateLimitFilter` placed before authentication in the Spring Security filter chain.
* **Algorithm:** Token bucket algorithm (Bucket4j).
* **Capacity:** 30 requests per minute burst capacity with greedy refill.
* **Response on Exhaustion:** HTTP 429 Too Many Requests with header `Retry-After: 60`.

---

### 4. Adversarial Protection Matrix

| Threat Vector | Mitigation Strategy | Result |
| :--- | :--- | :--- |
| **Commitment Injection / Tampering** | Server resolves commitment strictly from `attribute_commitments` table; verifies issuer RSA-PSS attestation against key registry. | Rejected (`verified: false` or 400) |
| **Credential Status Bypass (Revoked/Expired)** | Checked against authoritative `credentials` table before proof evaluation. | Rejected (`verified: false`) |
| **Proof Substitution / Cross-Credential Replay** | Nonce and credential context bound into Fiat-Shamir transcript; proof checked against authentic commitment. | Rejected (`verified: false`) |
| **Oversized Proof / Memory Exhaustion** | Strict boundary checking: proof bytes bounded to $[1, 65536]$ bytes. | HTTP 400 Bad Request |
| **Cryptographic DoS Bursting** | Token bucket rate limiting throttles requests per client IP / principal. | HTTP 429 Too Many Requests |
| **Information Leakage via Errors** | Generic error messages returned; stack traces suppressed; minimal audit logs recorded. | Safe HTTP response |

---

### 5. Verification & Validation

The test suite includes 21 comprehensive integration tests in `CredentialPredicateSecurityIntegrationTest` and dedicated unit tests in `ZkpVerificationRateLimitFilterTest`:
* Happy path verification for exact threshold ($v = T$) and above threshold ($v > T$).
* Rejection of invalid proofs, tampered commitments, expired/revoked credentials, and cross-credential proof replaying.
* Adversarial rejection of client-injected commitment fields, oversized proofs, oversized nonces, empty nonces, and negative thresholds.
* Thread-safe concurrent execution under high-volume load.
* Full test suite clean run across all 7 Maven reactor modules.
