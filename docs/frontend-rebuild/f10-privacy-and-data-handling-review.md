# Phase F10: Privacy and Data Handling Review

**Project:** Zero-Knowledge Document Verification Platform  
**Component:** Single-Page Application (SPA) Web Frontend (`frontend/`)  
**Security Phase:** F10 (Frontend Security Hardening, Privacy Protection, Secure Data Handling, and Adversarial Testing)  
**Date:** October 2026  
**Status:** Privacy Baseline and Data Minimization Verified  

---

## 1. Architectural Honesty & Zero-Knowledge Scope

A core requirement of Phase F10 is maintaining complete architectural honesty regarding cryptographic guarantees:

### What the Frontend Does:
- **Minimal Disclosure Display:** Verifier workflows display strictly necessary verification assertions (e.g. `valid: true`, `status: ACTIVE`, issuer public identifier) rather than exposing cleartext document contents or sensitive holder personal identifiable information (PII).
- **Client-Side Data Sanitization:** Strips directory paths, Unicode bidi overrides, and control characters before ingestion.
- **Cryptographic Result Presentation:** Accurately visualizes backend-calculated SHA-256 digests, RSA digital signatures, and predicate evaluation outcomes without fabricating frontend proofs.

### What the Frontend Does NOT Do:
- The frontend **does not** generate general-purpose zero-knowledge SNARK/STARK proofs locally in JavaScript/WASM.
- The frontend **does not** pretend that basic document hashing or RSA digital signatures constitute zero-knowledge proofs.
- Predicate verification (such as age or income thresholds) reflects backend cryptographic evaluation, and is documented as such.

---

## 2. Minimal Disclosure & Data Minimization Practices

### 2.1 Verifier Workflows (Mode A & Mode B)
In [PublicVerificationPage.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/routes/PublicVerificationPage.tsx) and verifier cards:
1. **Public Capability Verification:** When verifying a presented capability token, only the validity boolean, active status, and protocol version are rendered. Holder names, document titles, or underlying attribute key-values are not exposed.
2. **Predicate Proof Presentation:** In [PredicateProofVerifyCard.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/admin/components/PredicateProofVerifyCard.tsx), verification evaluates whether an attribute satisfies a specified condition (e.g. `threshold >= 18`) without revealing the exact private attribute value.

### 2.2 Audit Logging & Pseudonymity
In [AuditLogsPage.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/routes/AuditLogsPage.tsx) and [AuditEventsListCard.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/audit/components/AuditEventsListCard.tsx):
1. **IP Anonymization:** Client IP addresses are displayed as cryptographic hashes (`clientIpHash: 5e884898da28...`), preventing verifiers or administrators from harvesting raw network identifiers.
2. **Pseudonymous Identifiers:** Actors are represented by uniform resource names (URNs) (e.g. `urn:zkdoc:holder:018f...`), ensuring segregation between identity systems and platform logs.

---

## 3. Client-Side Data Handling & Lifecycle Protections

| Area | Threat | Mitigation Implemented |
|:---|:---|:---|
| **Session Memory** | Data remanence in shared or public browser terminals after logout | [authStore.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/state/authStore.ts) executes synchronous teardown listener cascade, wiping credentials, activities, and audit selections from memory. |
| **Object URLs & Blobs** | Retained blob references in browser heap leaking document images | [DocumentUploadCard.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/issuer/components/DocumentUploadCard.tsx) and [QrScannerCard.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/verifier/components/QrScannerCard.tsx) revoke object URLs immediately upon completion and on component unmount. |
| **Camera Privacy** | Background camera capture when scanner dialog is closed | [QrScannerCard.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/verifier/components/QrScannerCard.tsx) explicitly stops each track on `MediaStream` before detaching elements. |
| **Private Keys** | Accidental upload of private RSA/PKCS#8 keys | [RegisterKeyModal.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/admin/components/RegisterKeyModal.tsx) enforces Zero Private Key Policy, immediately clearing inputs and blocking transmission. |
| **Async Race Leakage** | Stale response delivering sensitive data of prior session | [client.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/api/client.ts) rejects delayed responses if session epoch changed in flight. |

---

## 4. Privacy Principles & Regulatory Alignment

1. **Principle of Data Minimization (GDPR Art. 5(1)(c)):** Only the minimum required data for a specific verification step is loaded or rendered.
2. **Storage Limitation (GDPR Art. 5(1)(e)):** Session tokens and cache reside in ephemeral session storage and heap memory; all traces are purged upon session termination.
3. **Integrity and Confidentiality (GDPR Art. 5(1)(f)):** Sensitive key materials are shielded by strict client policies, and all communications require TLS with strict security headers.
