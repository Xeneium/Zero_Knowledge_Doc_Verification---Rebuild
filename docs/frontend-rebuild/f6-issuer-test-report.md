# F6: Issuer Workflows Test & Verification Report

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F6  
**Document ID:** ZKDOC-FE-F6-002  
**Status:** Approved Test Execution Report  
**Date:** October 2026  

---

## 1. Executive Summary

This report documents the automated testing, verification, and regression coverage for Phase F6 (*Issuer Workflows — Secure Document Upload, Credential Issuance, and Lifecycle Management*).

All **10 frontend test suites comprising 123 individual test cases** passed cleanly under Vitest 5. Additionally, the backend Spring Boot integration test suite (`FrontendIntegrationTest`, 9 test cases) passed with zero errors or failures, verifying end-to-end compatibility between the Vite frontend bundle and the Spring Boot application server.

| Test Category | Suite Count | Tests Executed | Passed | Failed | Skipped | Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Issuer Specific Tests (Vitest)** | 4 | 47 | 47 | 0 | 0 | **PASSED** |
| **Existing Foundation & Auth (Vitest)** | 6 | 76 | 76 | 0 | 0 | **PASSED** |
| **Backend Spring Boot Integration (JUnit 5)** | 1 | 9 | 9 | 0 | 0 | **PASSED** |
| **Total Test Verification** | **11** | **132** | **132** | **0** | **0** | **PASSED** |

---

## 2. Issuer-Specific Frontend Test Suite Breakdown

### 2.1 `tests/fileValidation.test.ts` (14 Tests)
Validates file size constraints, MIME type checks, extension matching, and filename sanitization mirroring `DocumentContentValidator.java`:

| Test Case Name | Objective / Assertion | Result |
| :--- | :--- | :---: |
| `accepts valid PDF file under 25 MB` | Confirms valid `.pdf` passes all checks | PASS |
| `accepts valid PNG image under 25 MB` | Confirms valid `.png` passes all checks | PASS |
| `accepts valid JPEG image under 25 MB` | Confirms valid `.jpeg` / `.jpg` passes all checks | PASS |
| `accepts valid plain text file under 25 MB` | Confirms valid `.txt` passes all checks | PASS |
| `rejects file exceeding 25 MB limit` | Enforces 25 * 1024 * 1024 byte boundary with descriptive error | PASS |
| `rejects unsupported MIME type (e.g. executable)` | Blocks `application/x-msdownload` and unsupported types | PASS |
| `rejects mismatched extension and MIME type` | Flags spoofed files (e.g. `.exe` renamed as `.pdf`) | PASS |
| `rejects empty file (0 bytes)` | Rejects files with zero bytes | PASS |
| `rejects null or undefined file input` | Safely handles missing file parameters | PASS |
| `sanitizes dangerous characters from filename` | Strips path traversal sequences (`../`, `..\`) and controls | PASS |
| `formats bytes into human readable sizes` | Validates B, KB, MB formatting utility | PASS |
| `extracts valid file extensions` | Correctly extracts extensions from multi-dot filenames | PASS |
| `identifies allowed MIME types` | Verifies allowed MIME type predicate | PASS |
| `rejects oversized file with boundary value` | Confirms exactly 25 MB + 1 byte is rejected | PASS |

### 2.2 `tests/issuerStore.test.ts` (7 Tests)
Tests the observer-based session activity ledger, deduplication, capacity capping, and tab-scoped `sessionStorage` persistence:

| Test Case Name | Objective / Assertion | Result |
| :--- | :--- | :---: |
| `initializes with empty activities` | Verifies clean initialization state | PASS |
| `adds activity and persists to sessionStorage` | Verifies memory addition and sessionStorage sync | PASS |
| `deduplicates and brings re-added activity to top` | Moves existing item to head with updated status | PASS |
| `enforces maximum capacity of 50 activities` | Evicts oldest items when exceeding 50 entries | PASS |
| `updates activity status dynamically` | Modifies existing activity state without reordering | PASS |
| `clears activity log and wipes sessionStorage` | Empties memory store and removes storage key | PASS |
| `hydrates state from existing sessionStorage on init` | Restores previous session records on reload | PASS |

### 2.3 `tests/issuerApi.test.ts` (13 Tests)
Tests all 13 authoritative backend REST client functions in `src/api/issuerApi.ts`:

| Test Case Name | Target Endpoint | HTTP Method | Result |
| :--- | :--- | :---: | :---: |
| `uploadDocument` | `/api/documents` | POST (multipart) | PASS |
| `getDocument` | `/api/documents/{id}` | GET | PASS |
| `verifyStoredDocument` | `/api/documents/{id}/verify` | GET | PASS |
| `verifyUploadedDocument` | `/api/documents/{id}/verify-upload` | POST (multipart) | PASS |
| `getDocumentQrBlob` | `/api/documents/{id}/qr` | GET (blob) | PASS |
| `issueCredential` | `/api/credentials/issue` | POST (json) | PASS |
| `verifyCredential` | `/api/credentials/{id}/verify` | GET | PASS |
| `getCredentialLifecycle` | `/api/credentials/{id}/lifecycle` | GET | PASS |
| `suspendCredential` | `/api/credentials/{id}/lifecycle/suspend` | POST (json) | PASS |
| `resumeCredential` | `/api/credentials/{id}/lifecycle/resume` | POST (json) | PASS |
| `revokeCredential` | `/api/credentials/{id}/lifecycle/revoke` | POST (json) | PASS |
| `getCredentialLifecycleHistory` | `/api/credentials/{id}/lifecycle/history` | GET | PASS |
| `createCapability` | `/api/verification/capabilities` | POST (json) | PASS |

### 2.4 `tests/issuerComponents.test.tsx` (13 Tests)
Tests issuer UI components, dialogs, drag-and-drop dropzone, state transitions, and tabbed workspace integration:

| Test Case Name | Target Component | Objective / Assertion | Result |
| :--- | :--- | :--- | :---: |
| `renders upload card with dropzone and 25 MB bound` | `DocumentUploadCard` | Dropzone presence and file size notice | PASS |
| `uploads valid document and displays UUID and digest` | `DocumentUploadCard` | Successful upload, hash formatting, and store update | PASS |
| `displays validation error for oversized file` | `DocumentUploadCard` | Client-side pre-flight block on >25 MB file | PASS |
| `adds and removes dynamic credential attributes` | `CredentialIssuanceCard` | Dynamic attribute list addition/removal | PASS |
| `validates required fields, opens review dialog, issues` | `CredentialIssuanceCard` | Pre-flight validation, confirmation dialog, issuance | PASS |
| `binds authenticated issuer ID and prevents editing` | `CredentialIssuanceCard` | Principal auto-populated from session | PASS |
| `looks up credential lifecycle and displays status` | `CredentialLifecycleCard` | Status fetching, metadata, and version display | PASS |
| `triggers suspend modal and transitions status` | `CredentialLifecycleCard` | Confirmation modal, reason input, suspend call | PASS |
| `runs Mode A stored copy verification` | `DocumentVerifyQrCard` | Signature verification against database copy | PASS |
| `mints capability token and renders preview` | `DocumentVerifyQrCard` | Token minting, QR code blob rendering | PASS |
| `displays empty state when no activities recorded` | `IssuerActivityTable` | Zero-activity placeholder | PASS |
| `displays activities and filters by document/credential` | `IssuerActivityTable` | Tab filtering and log clear action | PASS |
| `renders workspace with role badge and switches tabs` | `IssuerDashboardPage` | Tab switching, searchParams synchronization | PASS |

---

## 3. Regression & Foundation Test Suite Breakdown

All 6 pre-existing test suites passed without regressions:

- `tests/authStore.test.ts` (11 tests) — Token decoding, role resolution, auto-logout.
- `tests/security.test.ts` (7 tests) — CWE-601 open redirect defense.
- `tests/authComponents.test.tsx` (12 tests) — Login form, route protection, token masking.
- `tests/client.test.ts` (4 tests) — Bearer token injection, error mapping.
- `tests/App.test.tsx` (8 tests) — Route resolution, deep linking, 404 views.
- `tests/components.test.tsx` (34 tests) — Shared design system primitives.

---

## 4. Quality Gates, Linters, and Production Build

| Quality Check | Command | Exit Code | Notes |
| :--- | :--- | :---: | :--- |
| **TypeScript Typecheck** | `npm --prefix frontend run typecheck` | `0` | Zero type errors (`tsc --noEmit`) |
| **ESLint Check** | `npm --prefix frontend run lint` | `0` | Zero errors, zero warnings (`--max-warnings 0`) |
| **Prettier Format Check** | `npm --prefix frontend run format:check` | `0` | All files conform to Prettier code style |
| **Vite Production Build** | `npm --prefix frontend run build` | `0` | Clean bundle compiled to `zkdoc-api/src/main/resources/static` |
| **Backend Integration Test** | `mvnw.cmd test -pl zkdoc-api -Dtest=FrontendIntegrationTest` | `0` | 9 tests passed, Spring Boot resources served cleanly |

---

## 5. Verification Conclusion

Phase F6 meets all technical, architectural, and security requirements. The issuer workspace is fully functional, type-safe, tested, and ready for Phase F7 (*Holder Workflows*).
