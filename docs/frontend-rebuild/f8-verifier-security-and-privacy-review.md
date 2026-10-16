# F8: Verifier Workflows — Security and Privacy Architecture Review

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F8  
**Document ID:** ZKDOC-FE-F8-004  
**Status:** Approved Security & Privacy Evaluation  
**Date:** October 2026  

---

## 1. Threat Model & Security Posture

The Verifier interface serves as the primary untrusted boundary where arbitrary public inputs (scanned QR codes, uploaded document files, manual token strings, and zero-knowledge proof tokens) enter the platform. This security review analyzes the defenses implemented in Phase F8 against key threat vectors.

---

## 2. Security Vector Analysis & Defenses

### 2.1. Untrusted QR Payload Processing (XSS, Injection, Protocol Hijacking)
- **Threat:** An attacker crafts a malicious QR code containing `javascript:eval(...)`, excessive buffer payloads, HTML tags, or arbitrary URLs intended to hijack verifier sessions.
- **Implemented Defense:**
  - Length bounding: Payload length is verified to be `<= 2048` characters in `qrPayload.ts`. Any payload exceeding this bound is immediately rejected before parsing.
  - Strict URL protocol whitelist: `parseQrPayload()` strictly admits only `http:` and `https:` schemes. Explicit checks reject `javascript:`, `data:`, `vbscript:`, and `file:` URIs.
  - Canonical JSON structure validation: Ingested JSON must adhere to the `QR_V1` schema with exact fields (`v`, `m`, `tok`, `uri`).
  - Safe token extraction: Extracted capability tokens are strictly checked against alphanumeric regex `^[A-Za-z0-9_-]+$` with length bounds `[16, 128]`.

### 2.2. Camera Hardware & Privacy Handling
- **Threat:** Unsolicited camera activation, video leak, or memory leaks resulting from unreleased media streams.
- **Implemented Defense:**
  - Explicit user gesture requirement: Camera streaming cannot start automatically on page load; it requires explicit clicking of `Start Camera Scanner`.
  - Deterministic track disposal: `stopCamera()` and component unmount hooks explicitly invoke `track.stop()` on all video tracks of the active `MediaStream`, immediately turning off camera hardware indicator lights.
  - Sandboxed file decoding: Uploaded QR images are processed entirely client-side via canvas bitmap decoding within `@zxing/browser` without uploading images to any external or third-party endpoint.

### 2.3. Proof Presentation Replay & Concurrency Exhaustion (DoS)
- **Threat:** Attackers attempting replay attacks using previously observed zero-knowledge proofs or submitting computational bursts to exhaust server CPUs during Bulletproof verification.
- **Implemented Defense:**
  - Challenge Nonce Validation: Bulletproof range proof verification strictly requires a 16-byte random challenge nonce (`nonce`) generated per verification session to tie proofs to a single evaluation context.
  - Concurrency Limiter Awareness: The backend enforces an 8-permit Semaphore (`CredentialPredicateProofController.java`). When saturated, it responds with `HTTP 429 Too Many Requests`. The frontend catches 429 status codes and presents an informative cooldown banner, preventing aggressive client retry storms.

### 2.4. Zero-Knowledge Privacy Enclave Preservation
- **Threat:** Verification results disclosing private credential attributes (e.g., actual salary or credit score) to relying parties.
- **Implemented Defense:**
  - Minimal Disclosure Architecture: The verifier UI renders only the threshold claim (e.g., `income >= 50000`) and the boolean verification status (`MATHEMATICALLY_VERIFIED` or `FAILED_PROOF`).
  - No cleartext attribute leak: Neither the frontend state (`verifierStore.ts`) nor the backend verification response (`/api/v2/zkp/credential-predicates/verify`) contains or returns the secret attribute value or blinding factor.
  - Local verification history sanitization: The in-memory verification ledger records only the claim expression (`attribute >= threshold`), credential ID, and verification timestamp.

### 2.5. Trust Preservation on Operational Failures
- **Threat:** A verifier incorrectly marks a valid user credential as fraudulent or counterfeit due to a transient network timeout, backend service restart, or rate limiting.
- **Implemented Defense:**
  - Semantic separation: `VerificationResultCard.tsx` distinguishes between:
    - *Cryptographic / Verification Failures:* Cryptographic signature invalid, document revoked, or file digest mismatch. Displayed with an authoritative `INVALID` badge and explanatory details.
    - *Operational / Connectivity Failures:* HTTP 429 (rate limit), HTTP 503 (service unavailable), or network fetch exception. Displayed with an amber `UNAVAILABLE` banner and an explicit notice:  
      *"Important Trust Notice: An operational failure, network timeout, or server unavailability does not mean the document or credential is fake or invalid. It simply means the cryptographic authority could not be contacted at this moment."*

---

## 3. Privacy Compliance Summary

| Requirement | Implementation Status | Evidence |
| :--- | :--- | :--- |
| **Zero Raw Document Exposure** | Enforced | Verification endpoints compare SHA-256 hashes; raw document streams are not displayed in verifier DOM. |
| **No Private Attribute Leak** | Enforced | Bulletproof verification only takes commitments and proofs; attributes remain private to holder. |
| **Client Memory Hygiene** | Enforced | In-memory verification history is capped at 20 items (FIFO) and cleared upon explicit session reset. |
| **No External Tracking / Analytics** | Enforced | Zero third-party telemetry, trackers, or foreign CDNs are included in the frontend bundle. |
