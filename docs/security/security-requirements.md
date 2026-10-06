# Zero-Knowledge Document Verification Platform
## Security Requirements Specification

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### 1. Cryptographic Security Requirements

* **SEC-REQ-001 (Document Integrity):** Every document uploaded must be digested using SHA-256 over raw binary bytes directly from the request stream.
* **SEC-REQ-002 (Document Authenticity):** The SHA-256 digest of every document must be signed using RSASSA-PSS with 2048-bit or 4096-bit RSA keys, SHA-256, MGF1-SHA256, and 32-byte salt length.
* **SEC-REQ-003 (Credential Canonicalization):** Credentials must be encoded using versioned binary canonical encoding (`zkdoc-canonical-v1`) with lexicographical attribute sorting and big-endian primitive layout prior to digest calculation.
* **SEC-REQ-004 (Commitment Authenticity):** Every attribute commitment $C = vG + rH$ must be accompanied by an issuer-signed attestation binding the commitment point, credential ID, credential digest, and attribute name.
* **SEC-REQ-005 (Independent Verifier Shifting):** The verifier must independently compute shifted commitment point $C' = C - T \cdot G$ and verify Bulletproof range proofs over $C'$ without trusting prover claims.
* **SEC-REQ-006 (Fiat-Shamir Nonce Binding):** Proof verification must require a fresh 256-bit verifier challenge nonce absorbed into the transcript to prevent replay attacks.

---

### 2. Authorization & Identity Requirements

* **SEC-REQ-010 (Strict JWT Verification):** All protected endpoints must require a Bearer JWT whose signature is validated against the active or retired `JWT_SIGNING` key in the key registry.
* **SEC-REQ-011 (Claim Constraints):** JWT validation must explicitly verify `iss` (Issuer URI), `aud` (Audience), and expiration (`exp`) with a maximum 60-second clock skew tolerance.
* **SEC-REQ-012 (Role-Based Access Control):**
  * `POST /api/documents` and `POST /api/credentials` require role `ISSUER`.
  * `POST /api/v2/zkp/credential-predicates/verify` requires role `VERIFIER`.
  * `POST /api/admin/keys/**` requires role `ADMIN`.
* **SEC-REQ-013 (Cryptographic Trust Segregation):** An application JWT identity cannot substitute for issuer cryptographic signatures. Actions claiming issuer authority must provide valid RSA-PSS signatures signed by a registered issuer key.

---

### 3. Key Management & Lifecycle Requirements

* **SEC-REQ-020 (Key Purpose Segregation):** Keys must be registered with dedicated, immutable `KeyPurpose` values (`JWT_SIGNING`, `DOCUMENT_SIGNING`, `CREDENTIAL_SIGNING`, `COMMITMENT_ATTESTATION_SIGNING`).
* **SEC-REQ-021 (Historical Validation):** Signatures created by keys currently in `ACTIVE` or `RETIRED` state must verify successfully.
* **SEC-REQ-022 (Revocation Enforcement):** Signatures associated with keys in `REVOKED` state must fail verification immediately, regardless of mathematical signature validity.
* **SEC-REQ-023 (No Server-Side Private Keys in DB):** The database must never store private keys. Private keys must be injected via secure environment variables or external KMS.

---

### 4. Operational & Abuse Protection Requirements

* **SEC-REQ-030 (Public Rate Limiting):** Public QR verification endpoint `GET /api/public/verify/{token}` must enforce a strict rate limit (default: 30 requests/minute per client IP) using a bounded in-memory sliding window or Redis token bucket.
* **SEC-REQ-031 (ZKP CPU Throttling):** ZKP verification endpoints must enforce rate limits (default: 10 requests/minute per verifier) and bounded execution timeouts (500ms max) to prevent CPU starvation.
* **SEC-REQ-032 (Payload Size Limits):** Document uploads must be bounded at 25 MB max. JSON payloads must be bounded at 256 KB max.
* **SEC-REQ-033 (No Stack Traces):** Global exception handlers must sanitize all client error responses. Internal class names, stack traces, and database errors must never be returned to clients.
* **SEC-REQ-034 (Security Audit Trail):** Every verification attempt, key registration, and status change must write an immutable audit record containing correlation ID, actor, timestamp, and result. Sensitive payloads must be redacted.
