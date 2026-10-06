# Zero-Knowledge Document Verification Platform
## Security Claims & Verification Evidence Register

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### 1. Mandatory Security Invariant Rule

> **CRITICAL RULE:** Every security claim must correspond directly to:
> $$\text{Security Claim} \longrightarrow \text{Concrete Mechanism} \longrightarrow \text{Correct Implementation} \longrightarrow \text{Adversarial Test} \longrightarrow \text{Operational Evidence}$$
> 
> A class name, database column, API flag, configuration property, or documentation statement is **not** a security mechanism by itself. If the architecture cannot demonstrate how a claim is enforced, that claim is invalid.

---

### 2. Formal Security Claims Register

| Claim ID | Security Claim | Technical Mechanism | Trust Assumption | Required Verification Evidence | Implementing Phase |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **CLM-001** | **Document Content Integrity** | SHA-256 byte streaming digest directly from upload stream. | SHA-256 collision resistance ($> 128$ bits). | Automated tests asserting 1-bit modified file yields completely different digest and fails comparison. | Phase 1 |
| **CLM-002** | **Document Origin Authenticity** | RSASSA-PSS digital signature over SHA-256 digest with MGF1-SHA256 and 32-byte salt. | Issuer private key uncompromised; RSA discrete log hardness. | Adversarial tests verifying forged signatures, wrong keys, or altered digests are rejected. | Phase 1 |
| **CLM-003** | **Credential Origin Authenticity** | Versioned binary canonical encoding + SHA-256 + RSA-PSS signature. | Issuer private key custody intact; canonicalizer determinism. | Test vectors with out-of-order fields, mutated values, and mismatched schemas failing verification. | Phase 2 |
| **CLM-004** | **Commitment Authenticity & Binding** | Issuer-signed commitment attestation binding `credentialId`, `credentialDigest`, `attributeName`, and commitment point $C$. | Issuer attestation signing key uncompromised. | Adversarial test attempting to pair valid credential with independently generated $C_{fake}$ fails. | Phase 3 |
| **CLM-005** | **Attribute Privacy (Zero Knowledge)** | Ristretto255 Pedersen commitment ($C = vG + rH$) + Bulletproof range proof. | Discrete logarithm assumption on Ristretto255; SecureRandom entropy for $r$. | Mathematical proof analysis and API tests ensuring raw attribute $v$ and blinder $r$ are absent from payloads. | Phase 3 |
| **CLM-006** | **Cryptographic Predicate Enforcement** | Verifier computes shifted point $C' = C - T \cdot G$; Bulletproof verifies $(v - T) \in [0, 2^{64}-1]$. | Bulletproof soundness; modular field wrapping on negative subtraction. | Adversarial test presenting proof for $v < T$ (below threshold) is cryptographically rejected by verifier. | Phase 3 |
| **CLM-007** | **Replay Resistance** | Fresh 256-bit verifier challenge nonce absorbed into Fiat-Shamir transcript. | Verifier nonces are unguessable and session-bound. | Replay tests asserting previously verified proof submitted with new session/nonce fails verification. | Phase 3 |
| **CLM-008** | **Public Data Minimization** | Dedicated public controller exposing only `{signatureValid, documentActive}` booleans. | Controller adheres to strict DTO schema. | Response payload contract tests verifying absence of IDs, hashes, filenames, or issuer details. | Phase 4 |
| **CLM-009** | **Historical Signature Validity** | Key registry status policy allowing `ACTIVE` and `RETIRED` keys to verify signatures. | Key status cannot be downgraded except by authorized ADMIN. | Lifecycle tests showing signature created during active period remains valid after key retirement. | Phase 5 |
| **CLM-010** | **Key Revocation Fail-Closed** | Key registry status policy explicitly excluding `REVOKED` keys prior to signature check. | Database status read is consistent and untampered. | Adversarial test verifying that signatures signed by a revoked key fail immediately despite valid math. | Phase 5 |
| **CLM-011** | **Authentication & Role Segregation** | Spring Security JWT authentication filter verifying `kid`, `iss`, `aud`, and roles. | JWT signing private key uncompromised. | Security tests attempting path traversal, role escalation, forged tokens, or unsigned requests receive 401/403. | Phase 5 |
| **CLM-012** | **Public Rate Limiting & DoS Defense** | In-memory / Redis sliding window filter throttling public verification. | Gateway forwards trusted client IP headers. | Load tests verifying client making $> 30$ req/min receives HTTP 429 Too Many Requests. | Phase 6 |

---

### 3. Explicit Non-Goals & Architectural Boundaries (Section 30)

The platform explicitly **does not promise or claim** the following advanced capabilities in Phase 0:
1. **Arbitrary ZKP Predicates:** Only numeric $\text{attribute} \ge \text{threshold}$ is supported. Arbitrary algebraic circuits, set membership proofs, or polynomial predicates are non-goals.
2. **Full Unlinkability / Blind Signatures:** Bulletproofs provide zero knowledge over the attribute value, but credential IDs and public commitments are linked to their respective records. Full unlinkable anonymous credentials (e.g., BBS+ / Idemix) are non-goals.
3. **Decentralized Identity / Blockchain Trust:** The trust anchor is an authenticated public key registry backed by PostgreSQL, not a distributed ledger, blockchain, or DID method.
4. **Hardware HSM Production Custody in Phase 0:** Phase 0 establishes the interface boundary for KMS/HSM, but initial implementation uses environment-injected keys.
5. **Protection Against Compromised Holder OS:** If the holder's mobile device or computer has rootkits/malware, the platform cannot prevent client-side key theft.
6. **Universal Document Malware Scanning:** Document verification verifies cryptographic authenticity and integrity; it does not perform heuristic antivirus/sandbox inspection.
