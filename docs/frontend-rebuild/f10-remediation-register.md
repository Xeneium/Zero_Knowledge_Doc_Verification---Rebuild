# Phase F10: Vulnerability & Remediation Register

**Project:** Zero-Knowledge Document Verification Platform  
**Component:** Single-Page Application (SPA) Web Frontend (`frontend/`)  
**Security Phase:** F10 (Frontend Security Hardening, Privacy Protection, Secure Data Handling, and Adversarial Testing)  
**Date:** October 2026  
**Status:** All Registered Items Fully Remediated and Verified  

---

## 1. Remediation Register Summary

| Finding ID | Title | Severity | CVSS v3.1 | Status | Test Reference |
|:---|:---|:---:|:---:|:---:|:---|
| **SEC-F10-001** | Cross-Session State Retention & Stale In-Flight Async Response Leakage | High | 7.4 | Remediated | [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) (TEST-SEC-01..03) |
| **SEC-F10-002** | Blob Object URL Memory Leakage & Document Preview Retention | Medium | 5.3 | Remediated | [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) (TEST-SEC-22) |
| **SEC-F10-003** | Unicode Bi-Directional (Bidi) File Extension Spoofing & Path Traversal | High | 7.5 | Remediated | [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) (TEST-SEC-04..09) |
| **SEC-F10-004** | Client-Side MIME-Type Blind Acceptance & Polyglot Executable Ingestion | Medium | 5.9 | Remediated | [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) (TEST-SEC-10..19) |
| **SEC-F10-005** | Accidental Private Key Registration Violation in Key Registry | High | 7.7 | Remediated | [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) (TEST-SEC-20..21) |
| **SEC-F10-006** | Form Double-Submission Race Conditions on Critical Crypto Operations | Low | 3.7 | Remediated | Modal Component Tests (admin, issuer, verifier) |
| **SEC-F10-007** | Camera Stream Hardware Retention After QR Scanner Deactivation | Medium | 4.3 | Remediated | [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) (TEST-SEC-22) |
| **SEC-F10-008** | Missing Standard Security Headers in Vite Preview and Static Meta Tags | Low | 3.1 | Remediated | [vite.config.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/vite.config.ts), [index.html](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/index.html) |

---

## 2. In-Depth Vulnerability Details

### SEC-F10-001: Cross-Session State Retention & Stale Async Leakage
- **CVSS v3.1:** 7.4 (CVSS:3.1/AV:N/AC:H/PR:L/UI:R/S:U/C:H/I:H/A:N)
- **Root Cause:** Single-page application stores maintained state in heap variables without listening to authentication transitions. When switching accounts, previously cached data was accessible. Additionally, slow network responses resolving after logout committed data to newly initiated or logged-out sessions.
- **Remediation:** 
  1. Implemented pub/sub teardown listener cascade in [authStore.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/state/authStore.ts) called on `clearSession()`, `handleSessionExpired()`, and identity subject change.
  2. Implemented monotonic `sessionEpoch` in [authStore.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/state/authStore.ts). In [client.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/api/client.ts), in-flight requests compare the epoch at dispatch against the epoch at resolution; if mismatched, an `ApiError(499)` is thrown and the payload discarded.
- **Verification:** Unit tests in [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) verify teardown purges domain stores and in-flight responses reject with status 499.

### SEC-F10-003: Unicode Bi-Directional Spoofing & Path Traversal
- **CVSS v3.1:** 7.5 (CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:U/C:N/I:H/A:N)
- **Root Cause:** Filenames supplied by users or browsers were used directly without stripping control characters or Unicode bidi formatting codes (such as `\u202E`). This allowed malicious actors to disguise executable payloads as PDF or PNG files.
- **Remediation:** Enhanced `sanitizeFilename` in [fileValidation.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/lib/utils/fileValidation.ts) with regex stripping for control codes and Unicode bidi overrides, recursive path traversal stripping (`..`), and length truncation at 255 characters.
- **Verification:** 6 unit tests in [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) verify complete stripping and neutralization.

### SEC-F10-004: Client-Side Magic Bytes Inspection (MIME Parity)
- **CVSS v3.1:** 5.9 (CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:U/C:N/I:H/A:N)
- **Root Cause:** Pre-upload validation only verified filename extensions and `file.type`, which are easily spoofed by renaming malicious binaries.
- **Remediation:** Added `validateFileMagicBytes` in [fileValidation.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/lib/utils/fileValidation.ts) inspecting raw byte signatures (PDF `%PDF`, PNG, JPEG SOI, text checks rejecting ELF/MZ).
- **Verification:** 10 unit tests in [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) verify acceptance of valid files and rejection of spoofed/corrupted inputs.

### SEC-F10-005: Zero Private Key Policy Enforcement in Trust Registry
- **CVSS v3.1:** 7.7 (CVSS:3.1/AV:N/AC:L/PR:H/UI:R/S:U/C:H/I:H/A:N)
- **Root Cause:** Operators could paste private keys into the public key input of the key registration modal, exposing private key anchors across the wire.
- **Remediation:** In [RegisterKeyModal.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/admin/components/RegisterKeyModal.tsx), submission checks for `PRIVATE KEY` or `-----BEGIN RSA PRIVATE KEY-----`. If detected, the input is purged from state and DOM, an error alert is rendered, and network transmission is blocked.
- **Verification:** 2 integration tests in [securityHardening.test.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) confirm input purging and API call blocking.

---

## 3. Residual Risk Analysis

1. **Content Security Policy (CSP):** While development and preview servers enforce standard headers (`X-Content-Type-Options`, `X-Frame-Options`, `Permissions-Policy`), full production Content-Security-Policy headers with script/style nonces should be configured on reverse proxies (e.g. Nginx, Cloudflare) or Spring Security WebSecurityCustomizer in deployment environments.
2. **Client-Side Hardware Dependence:** While `validateFileMagicBytes` provides rapid client-side feedback, backend validation via `DocumentContentValidator.java` remains the authoritative source of truth.
