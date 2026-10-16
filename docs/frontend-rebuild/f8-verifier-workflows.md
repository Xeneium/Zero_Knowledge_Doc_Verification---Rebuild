# F8: Verifier Workflows — Public Document Verification, QR-Code Scanning, Cryptographic Verification Results, and Privacy-Preserving Verification Experience

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F8  
**Document ID:** ZKDOC-FE-F8-001  
**Status:** Approved Implementation Report  
**Date:** October 2026  

---

## 1. Executive Summary & Mission

Frontend Phase F8 implements the production-grade **Verifier Workflows** for the Zero-Knowledge Document Verification Platform. It establishes a multi-channel cryptographic verification hub enabling public verifiers, authenticated relying parties, auditors, and system administrators to verify digital documents, credential authenticity, and zero-knowledge predicate range proofs without exposing underlying sensitive private data.

In strict alignment with the Spring Boot backend architecture and cryptographic protocols:
1. **Zero Fake Verifications or Simulated Guarantees:** Verification operations execute directly against authoritative backend controllers (`/api/public/verify/{token}`, `/api/public/verify/capability/{token}`, `/api/documents/{id}/verify-upload`, `/api/documents/{id}/verify`, `/api/credentials/{id}/verify`, and `/api/v2/zkp/credential-predicates/verify`). The frontend never manufactures trust decisions or simulates cryptographic signatures.
2. **Camera-Based & Upload-Based QR Scanning:** Integrates `@zxing/browser` for camera feed stream processing with explicit user-gesture camera initialization, multi-camera device switching, video track disposal, and a silent client-side image file decoding fallback.
3. **Safe Untrusted QR Payload Parsing:** Strictly parses and sanitizes incoming QR strings (Canonical JSON `QR_V1`, direct URL, and opaque hex/alphanumeric tokens), enforcing a 2,048-character length limit, scheme sandboxing (rejecting `javascript:`, `data:`, `vbscript:`), and structured format validation.
4. **Trust-Preserving Error Handling:** Clear distinction between cryptographic/validity failures (e.g. signature invalid, document inactive, hash mismatch, proof rejected) and operational failures (e.g., HTTP 429 concurrency limit, HTTP 503 service unavailable, network timeout). Operational errors display prominent trust notices explaining that server unreachability does not imply a fake or invalid credential.
5. **Zero-Knowledge Privacy Enclave Preservation:** Verifier interfaces operate strictly on cryptographic digests, public keys, Bulletproof commitments, challenge nonces, and mathematical evaluation results. Raw document contents, cleartext private attribute values, and opening secrets are never rendered or leaked to the DOM.
6. **Reactive Verifier Session State:** Powered by `verifierStore.ts` utilizing `useSyncExternalStore` for reactive UI updates and maintaining an in-memory chronological verification history ledger bounded to 20 items.

---

## 2. Backend Endpoint Matrix & Access Rules

All verifier workflows map directly to verified Spring Boot REST endpoints in `SecurityConfiguration.java` and domain controllers:

| Functional Area | HTTP Method & Path | Controller / Service | Security & Auth Constraint | Payload / Query |
| :--- | :--- | :--- | :--- | :--- |
| **Public Capability Verify** | `GET /api/public/verify/capability/{token}` | `VerificationCapabilityController.java` | Public (`permitAll()`) | Path: 16–128 char capability token |
| **Public Document Token Verify** | `GET /api/public/verify/{token}` | `DocumentController.java` | Public (`permitAll()`) | Path: 16–128 char document verification token |
| **Upload Document Verify (Mode B)** | `POST /api/documents/{id}/verify-upload` | `DocumentController.java` | Authenticated (`ROLE_VERIFIER`, `ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_ADMIN`) | Multipart form: `file` (candidate copy up to 25 MB) |
| **Stored Document Verify (Mode A)** | `GET /api/documents/{id}/verify` | `DocumentController.java` | Authenticated (`ROLE_VERIFIER`, `ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_ADMIN`) | Path: UUID |
| **Credential Signature Verify** | `GET /api/credentials/{id}/verify` | `CredentialController.java` | Authenticated (`ROLE_VERIFIER`, `ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_ADMIN`) | Optional query: `?asOf=ISO8601` |
| **Credential Lifecycle Query** | `GET /api/credentials/{id}/lifecycle` | `CredentialController.java` | Authenticated (`ROLE_VERIFIER`, `ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_ADMIN`) | Path: UUID |
| **ZKP Predicate Proof Verify** | `POST /api/v2/zkp/credential-predicates/verify` | `CredentialPredicateProofController.java` | Authenticated (`ROLE_VERIFIER`, `ROLE_ADMIN`) | JSON body: Bulletproof range proof and challenge nonce |

---

## 3. Architecture & Modular Structure

The verifier feature is organized into modular components under `frontend/src/features/verifier/` and exposed via `frontend/src/routes/PublicVerificationPage.tsx`:

```
frontend/src/
├── api/
│   ├── verifierApi.ts                           # Typed client for all 7 verifier backend endpoints
│   └── types.ts                                 # Shared TypeScript contracts and responses
├── lib/utils/
│   └── qrPayload.ts                             # QR payload parser, sanitizer, hex/base64 converters
├── state/
│   └── verifierStore.ts                         # useSyncExternalStore reactive store with 20-item history FIFO
├── features/verifier/
│   ├── components/
│   │   ├── QrScannerCard.tsx                    # Camera QR scanner with device selection & file upload fallback
│   │   ├── ManualReferenceCard.tsx              # Manual entry with real-time format detection & syntax preview
│   │   ├── DocumentUploadVerifyCard.tsx         # Mode B candidate file upload integrity verification
│   │   ├── PredicateProofVerifyCard.tsx         # ZKP Bulletproof proof verifier with quick-paste JSON parser
│   │   ├── VerificationResultCard.tsx           # Authoritative verification outcome visualizer & audit checklist
│   │   └── VerificationHistoryCard.tsx          # Session-scoped verification ledger
│   └── index.ts                                 # Barrel export
└── routes/
    └── PublicVerificationPage.tsx               # Tabbed verification hub routing /verify and /predicate-verification
```

---

## 4. Key Component Implementation Details

### 4.1. Camera & Upload QR Scanner (`QrScannerCard.tsx`)
- **Explicit User Activation:** Camera streaming begins only upon explicit user gesture (`Start Camera Scanner`), complying with mobile browser privacy policies.
- **Hardware Selection:** Enumerates video input devices via `navigator.mediaDevices.enumerateDevices()`, enabling users to select back/front cameras.
- **Track Lifecycle Management:** On component unmount, camera stop, or device switch, active video tracks (`stream.getTracks().forEach(t => t.stop())`) are properly closed to release hardware lights.
- **Image File Fallback:** If camera access is denied, unsupported, or physically absent, verifiers can upload a QR image (PNG, JPEG, WebP) decoded client-side via `BrowserQRCodeReader.decodeFromImageUrl()`.

### 4.2. Safe QR Payload Parser (`qrPayload.ts`)
- **Length Constraint:** Strictly bounds input payloads to 2,048 characters to mitigate denial-of-service and buffer allocation attacks.
- **Scheme Sandboxing:** URL payloads are validated to ensure only `http:` and `https:` protocols are processed; dangerous schemes such as `javascript:`, `data:`, `vbscript:`, or `file:` are categorically rejected.
- **Multi-Format Ingestion:** Seamlessly parses:
  1. *Canonical JSON (`QR_V1`):* JSON containing `{ v: 'QR_V1', m: '...', tok: '...', uri: '...' }`.
  2. *Direct Verification URLs:* URLs matching `/api/public/verify/capability/{token}` or `/api/public/verify/{token}`.
  3. *Opaque Tokens:* Raw alphanumeric or hex tokens (16–128 characters).

### 4.3. Mode B Document Upload Verification (`DocumentUploadVerifyCard.tsx`)
- **Candidate Integrity Check:** Allows relying parties holding a physical or digital document copy to submit it for comparison against the stored cryptographic digest and RSA-PSS signature.
- **Client-Side Guardrails:** Enforces the backend's 25 MB max file size constraint and allowed MIME types (`application/pdf`, `image/png`, `image/jpeg`, `text/plain`) prior to network transmission.
- **RBAC Awareness:** Informs unauthenticated users of the requirement for `ROLE_VERIFIER` or administrative session to access Mode B verification.

### 4.4. Zero-Knowledge Predicate Range Proofs (`PredicateProofVerifyCard.tsx`)
- **Mathematical Evaluation:** Verifies Bulletproof range proofs against committed attributes without disclosing the holder's private value (e.g., verifying `income >= 50000`).
- **Quick-Paste Presentation Parser:** Supports one-click ingestion of JSON presentation tokens exported from holder wallets, automatically extracting `credentialId`, `attributeName`, `threshold`, and converting hex-encoded proofs/nonces to standard Base64 for Spring Boot Jackson deserialization.
- **Concurrency Rate-Limiting Protection:** Detects backend semaphore saturation (HTTP 429 from `CredentialPredicateProofController.java`'s 8-permit limit) and presents clear backoff guidance.

### 4.5. Authoritative Result Visualizer (`VerificationResultCard.tsx`)
- **Verification Audit Checklist:** Deconstructs the verification outcome into 4 granular verification checks:
  1. *Digital Signature Check:* Validates RSA-PSS cryptographic signature against the active issuer public key.
  2. *Operational Status Check:* Verifies whether the credential or document is active, expired, or revoked.
  3. *SHA-256 Digest Integrity:* Confirms that the presentation content hash matches the issuer's committed digest.
  4. *Protocol Mode:* Confirms whether verification was executed via presented capability, stored record, or zero-knowledge proof.
- **Trust Warning on Operational Errors:** If an operational error (network timeout, 429 rate limit, 503 unavailable) occurs, a dedicated alert makes explicit that network failure does not indicate an invalid or forged credential.

---

## 5. Automated Verification & Quality Gates

The implementation passes all project quality gates:

1. **Vitest Unit & Integration Suites:**
   - `tests/qrPayload.test.ts` (14 tests) — payload bounds, dangerous scheme rejection, canonical JSON parsing, hex/base64 conversions.
   - `tests/verifierApi.test.ts` (7 tests) — typed contract checks across all 7 verifier API endpoints.
   - `tests/verifierStore.test.ts` (7 tests) — store reactivity, 20-item history FIFO capping, deduplication, state resets.
   - `tests/verifierComponents.test.tsx` (9 tests) — end-to-end component rendering, user interactions, QR fallback, file upload validation, and proof evaluation.
   - **Total Frontend Suite:** 17 test suites, 188 tests passing (100% green).
2. **TypeScript Compilation:** `tsc --noEmit` passed with 0 errors.
3. **ESLint Static Analysis:** `eslint . --max-warnings 0` passed with 0 warnings and 0 errors.
4. **Prettier Formatting:** `prettier --check .` passed with 100% code style compliance.
5. **Vite Production Build:** Successfully emitted production static bundle into `zkdoc-api/src/main/resources/static/`.
6. **Spring Boot Integration Test:** `FrontendIntegrationTest` passed 9/9 tests against local SQLite/Flyway migration database.
