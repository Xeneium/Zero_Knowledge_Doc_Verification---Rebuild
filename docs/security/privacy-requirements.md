# Zero-Knowledge Document Verification Platform
## Privacy Requirements & Minimal Disclosure Specification

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### 1. Minimal Disclosure Architecture

The primary privacy objective is ensuring that **relying parties (verifiers) receive strictly the minimum information required to make a trust decision**, and nothing more.

```
+-------------------------------------------------------------------------------+
|                             Data Classification                               |
+-------------------------------------------------------------------------------+
| Level 1: Public Metadata          | QR Tokens, Verification Codes, Health     |
| Level 2: Verifier Scope           | Predicate Outcome (true/false), Issuer ID |
| Level 3: Subject/Holder Private   | Raw Document Bytes, Text, Attribute Values|
| Level 4: Cryptographic Secrets    | Blinding Factors (r), Prover Witnesses    |
+-------------------------------------------------------------------------------+
```

---

### 2. Concrete Privacy Requirements

#### PRIV-REQ-001 (Zero-Knowledge Attribute Secrecy)
* When proving $\text{attribute} \ge \text{threshold}$, the verifier receives **zero knowledge** of:
  1. The exact attribute value $v$.
  2. The secret blinding factor $r$.
  3. The prover witness or intermediate polynomial evaluations.
* The API payload for predicate verification must forbid accepting $v$ or $r$.

#### PRIV-REQ-002 (Blinding Factor Custody Isolation)
* Blinding factors ($r$) must be generated on the client or in an isolated transient issuance session.
* The platform database **must never persist plaintext blinding factors**.
* Only a one-way salt hash (`blinding_factor_salt_hash`) is retained for duplicate detection.

#### PRIV-REQ-003 (Public Verification Data Minimization)
* The public endpoint `GET /api/public/verify/{token}` must return strictly:
  ```json
  {
    "signatureValid": boolean,
    "documentActive": boolean
  }
  ```
* Prohibited disclosures in public endpoints:
  * Document filenames or extensions
  * SHA-256 hashes or signature bytes
  * Issuer names or key identifiers
  * Uploader identities or upload timestamps
  * Total file sizes or mime types

#### PRIV-REQ-004 (Audit Trail Redaction & PII Protection)
* The audit log (`verification_audit_events`) must **never** record:
  * Raw document bytes, file text, or OCR content.
  * Credential attribute values or plaintext payloads.
  * Proof bytes, blinding factors, or witnesses.
  * Plaintext client IP addresses (IPs must be truncated or hashed with a rotating daily salt).

#### PRIV-REQ-005 (Client-Side Camera & QR Processing)
* In the frontend SPA, camera video streams for QR scanning must be processed **entirely client-side** in the browser using WebAssembly / local JS decoders.
* Video frames, camera images, or intermediate canvas captures must **never be transmitted** to backend servers.
* Camera hardware streams must be explicitly terminated immediately upon successful scan, manual cancellation, or page navigation.

#### PRIV-REQ-006 (Right to Erasure & Secure Deletion)
* When an issuer deletes or revokes a document:
  1. Relational database status is updated to `REVOKED` (historical non-repudiation).
  2. Document bytes in object storage are securely deleted using S3 lifecycle rules / cryptographic erasure.
