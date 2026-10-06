# Zero-Knowledge Document Verification Platform
## Comprehensive STRIDE Threat Model

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### 1. Methodology & Scope

This threat model applies the **STRIDE** methodology (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege) across all six trust domains: Authentication, Credentials, Commitments, Zero-Knowledge Proofs, Documents, and Infrastructure.

---

### 2. Threat Analysis & Mitigation Matrix

| Category | Threat Description | Attack Vector | Technical Mitigation | Residual Risk & Detection |
| :--- | :--- | :--- | :--- | :--- |
| **Spoofing** | Forged or Tampered JWT | Attacker crafts self-signed JWT or alters claims (`roles: ADMIN`). | Spring Security `NimbusJwtDecoder` strictly validates signature using registered `JWT_SIGNING` key, asserts `iss`, `aud`, and expiration. | Compromise of identity provider private key. Monitored via auth failure logs. |
| **Spoofing** | Algorithm Confusion Attack | Attacker modifies JWT header to `alg: none` or symmetric `HS256`. | Explicit allowlist of `JWSAlgorithm.RS256` or `PS256` in decoder configuration; rejects `none` and symmetric keys. | None. Verified by unit tests. |
| **Spoofing** | Issuer Substitution | Attacker presents valid credential issued by untrusted 3rd party. | Verifier checks issuer against platform-trusted root list (`key_registry.owner_id`). | Trust list misconfiguration. Monitored via admin audit. |
| **Tampering** | Credential Attribute Manipulation | Holder modifies attribute value (e.g. income from 30,000 to 80,000). | Canonical binary encoding + RSA-PSS signature verification fails immediately if any attribute or byte is altered. | None. Cryptographically bounded by RSA-PSS hardness. |
| **Tampering** | Unrelated Commitment Attack | Holder creates valid commitment to fraudulent value $C_{fake}$ and proves range. | **Attestation Binding:** Verifier looks up issuer-attested commitment from database; verifier **never** accepts caller-supplied commitment. | None. Prover cannot supply custom commitment. |
| **Tampering** | Threshold Tampering | Holder generates proof for $T=30$ but claims proof is for $T=80$. | Verifier computes shifted point $C' = C - T \cdot G$ using the verifier's own threshold, and binds $T$ into Fiat-Shamir transcript. | None. Mathematical proof verification fails if $T$ changes. |
| **Tampering** | Document Byte Modification | Attacker modifies text in an uploaded PDF document. | Document verification recomputes SHA-256 and compares to stored digest; RSA-PSS signature verification over digest. | None. Cryptographically bounded by SHA-256 collision resistance. |
| **Repudiation** | Issuer Denies Issuing Credential | Issuer claims they did not author fraudulent credential. | Non-repudiation enforced via issuer's RSA-PSS digital signature over canonical binary representation. | Issuer claims private key theft. Handled via key revocation timestamps. |
| **Repudiation** | Verifier Denies Verification Outcome | Auditor disputes verification execution. | Append-only security audit log (`verification_audit_events`) records correlation ID, actor, result, and timestamp. | Database DBA tampering (mitigated via remote log shipping). |
| **Info Disclosure**| Raw Attribute Leakage in ZKP | Verifier attempts to learn exact numeric attribute. | Information-theoretic hiding of Pedersen commitment ($C = vG + rH$) and zero-knowledge property of Bulletproof range proof. | None (information-theoretic secrecy under discrete log assumption). |
| **Info Disclosure**| Secret Blinding Factor Leakage | Server logs or databases store private blinder $r$. | Strict policy: Server **never** persists $r$; $r$ exists only ephemerally on client during issuance/proving; filtered from all logs. | Malicious client-side malware on holder's device. |
| **Info Disclosure**| Document Exfiltration via QR | Casual scanner extracts document contents from public QR. | QR code contains only a 256-bit unguessable capability token; public API returns only `{signatureValid, documentActive}` booleans. | None. No document bytes, hashes, or names in public response. |
| **DoS** | ZKP Verifier CPU Exhaustion | Attacker floods `/verify` with thousands of computationally heavy Bulletproofs. | Dedicated rate limiter on ZKP endpoints (max 10 req/min per IP/token) + thread-pool bounding + proof byte length limits. | Distributed botnets bypassing IP rate limit (requires WAF CAPTCHA). |
| **DoS** | Archive Bomb / Oversized Upload | Attacker uploads 10GB file to exhaust memory/disk. | Spring Boot `max-file-size: 25MB`, streaming digestion directly from request stream to disk/S3 without buffering in RAM. | Network bandwidth saturation at gateway level. |
| **Elevation** | Privilege Escalation to ADMIN | Normal holder accesses `/api/admin/keys` to register malicious key. | Spring Security `@PreAuthorize("hasRole('ADMIN')")` enforced at method and URL security layers. | Role claim injection in JWT (prevented by strict signature check). |
| **Elevation** | Path Traversal via Uploaded Filename | Attacker submits filename `../../etc/passwd`. | Filename sanitized via UUID generation; storage key derived from UUID; original filename discarded from filesystem path. | None. Direct file paths are never constructed from user input. |

---

### 3. Residual Risks & Operational Caveats

1. **Compromised Issuer Private Key:** If an issuer's private signing key is exfiltrated, the attacker can issue fraudulent credentials until the key is marked `REVOKED` in the registry.
2. **Holder Device Compromise:** If malware on the holder's machine steals $(v, r)$, the malware can generate proofs on behalf of the holder.
3. **Database Administrator Privilege:** A rogue database superuser with write access could alter `credentials.status` from `REVOKED` to `ACTIVE` (though they cannot forge cryptographic signatures without the private key).
