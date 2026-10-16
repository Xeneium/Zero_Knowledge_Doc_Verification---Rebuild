# F6: Issuer Workflows Security Review

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F6  
**Document ID:** ZKDOC-FE-F6-003  
**Status:** Approved Security Assessment  
**Date:** October 2026  

---

## 1. Executive Summary & Scope

This document provides a comprehensive application security review of the Frontend Phase F6 implementation (*Issuer Workflows — Secure Document Upload, Credential Issuance, and Lifecycle Management*).

The review evaluated:
1. **File Upload Security & Pre-flight Validation:** Defenses against unrestricted file uploads (CWE-434), oversized payload denial-of-service, path traversal filenames (CWE-22), and MIME-type spoofing.
2. **Object-Level Authorization & Impersonation Prevention:** Defenses against Broken Object Level Authorization (BOLA / IDOR, CWE-639) when issuing credentials or mutating credential lifecycle states.
3. **Cryptographic Integrity & Signature Enforcement:** Compliance with the backend's RSA-PSS digital signature authority, ensuring no unauthenticated or client-forged signatures are accepted.
4. **Data Disclosure & Ephemeral Storage Discipline:** Strict containment of session activity records in tab-scoped memory and `sessionStorage`, preventing cross-tab leakage or persistent client tracking.
5. **Browser Memory & Resource Management:** Safe handling of binary object URLs (`blob:`) created for QR codes and verification artifacts to prevent memory leaks (CWE-401).

---

## 2. Threat Modeling & Vulnerability Analysis

| Vulnerability / Threat | CWE / OWASP ID | Pre-F6 Risk Level | F6 Mitigation & Defense Controls | Residual Risk |
| :--- | :--- | :---: | :--- | :---: |
| **Unrestricted File Upload** | CWE-434<br>OWASP A04:2021 | **CRITICAL** | `fileValidation.ts` enforces 25 MB max size, restricts MIME types to PDF, PNG, JPEG, and TXT, and rejects extension/MIME mismatches before upload. | **NONE** |
| **Path Traversal via Filename** | CWE-22<br>OWASP A01:2021 | **HIGH** | `sanitizeFilename()` strips path traversal sequences (`../`, `..\`), non-printable characters, and invalid path separators before transmission. | **NONE** |
| **Issuer Impersonation (BOLA)** | CWE-639<br>OWASP A01:2021 | **CRITICAL** | `CredentialIssuanceCard` locks the `issuerId` to the authenticated user's `sub`. Backend `ObjectAuthorizationService` cryptographically enforces principal match. | **NONE** |
| **Forged Signature / Mock Tokens** | CWE-347<br>OWASP A02:2021 | **HIGH** | Eradicated all mock endpoints. Every document and credential receives an authentic SHA-256 digest and RSA-PSS signature from the server key authority. | **NONE** |
| **Object URL Memory Leaks** | CWE-401<br>OWASP A04:2021 | **MEDIUM** | Binary QR blobs converted via `URL.createObjectURL` are tracked in state and explicitly revoked via `URL.revokeObjectURL` upon unmount or replacement. | **NONE** |
| **Client Storage Exhaustion** | CWE-400<br>OWASP A04:2021 | **MEDIUM** | `issuerStore` enforces a hard cap of 50 activities in `sessionStorage` (`zkdoc_issuer_activity`), evicting the oldest entries using a FIFO queue. | **LOW** |

---

## 3. In-Depth Analysis of Security Controls

### 3.1 Pre-Flight File Validation Engine (`fileValidation.ts`)
The backend defines strict validation rules in `DocumentContentValidator.java` (max 25 MB, permitted content types: PDF, PNG, JPEG, Plain Text, and magic byte checking). 

To prevent network congestion, server CPU exhaustion, and poor user feedback:
- **Size Boundary Enforcement:** Evaluates `file.size <= 25 * 1024 * 1024`. Oversized files are blocked locally with an explicit size breakdown.
- **MIME & Extension Whitelist:** Restricts uploads to:
  - `application/pdf` (`.pdf`)
  - `image/png` (`.png`)
  - `image/jpeg` (`.jpg`, `.jpeg`)
  - `text/plain` (`.txt`)
- **Extension Spoofing Check:** Rejects files where the extension does not match the reported MIME type (e.g. an executable named `document.pdf`).
- **Filename Sanitization:** Removes path navigation characters (`/`, `\`, `..`), control characters, and unsafe whitespace.

### 3.2 Object-Level Authorization Alignment
A critical vulnerability in multi-tenant credential systems is an authenticated issuer attempting to issue or mutate credentials under another issuer's identifier (`issuerId`).

**Backend Enforcement (`ObjectAuthorizationService.java`):**
```java
public void verifyIssuerOwnership(String requestedIssuerId) {
    String currentPrincipal = SecurityContextHolder.getContext().getAuthentication().getName();
    if (!currentPrincipal.equals(requestedIssuerId) && !hasRole("ROLE_ADMIN")) {
        throw new AccessDeniedException("Principal not authorized to issue under this issuer ID");
    }
}
```

**Frontend Alignment (`CredentialIssuanceCard.tsx`):**
- The form initializes `issuerId` with `user.subject` from the decoded JWT claims in `authStore`.
- The field is marked disabled in the UI to prevent accidental tampering.
- Even if a client bypasses UI controls, the backend's Spring Security filter and object-level check immediately returns HTTP 403 Forbidden.

### 3.3 Ephemeral Session Activity Ledger (ADR-003 Compliance)
The platform strictly maintains zero persistent tracking across sessions:
- **No Global Server Queries:** The issuer activity ledger does not make unauthenticated global queries that could reveal other users' documents.
- **Tab-Scoped `sessionStorage`:** Stored under key `zkdoc_issuer_activity`, isolated strictly to the active tab, and destroyed when the tab is closed.
- **Capacity Bound:** The store enforces `activities.slice(0, 50)` on every mutation to prevent memory and DOM bloat.
- **Manual Log Wipe:** A "Clear Session Log" button allows the issuer to immediately purge all activity records from both memory and browser storage.

### 3.4 Safe Binary Stream & Blob Handling
When fetching QR code images from `/api/documents/{id}/qr` or `/api/verification/capabilities/{id}/qr`:
1. The API client requests `image/png` with `Accept: image/png, application/octet-stream, */*`.
2. The response is converted to a Blob.
3. An object URL is minted via `URL.createObjectURL(blob)`.
4. In `useEffect` cleanup handlers, `URL.revokeObjectURL(url)` is invoked to release the browser memory, preventing memory leaks during prolonged sessions.

---

## 4. Residual Risk Assessment

- **Client-Side File Magic Bytes:** The browser cannot securely guarantee file content integrity without reading all binary bytes into memory. Residual risk is mitigated by the backend's `DocumentContentValidator.java`, which reads file magic bytes and rejects counterfeit files with HTTP 400.
- **SessionStorage Vulnerability to Cross-Site Scripting (XSS):** If an XSS vulnerability existed in the frontend, scripts could read `sessionStorage`. This risk is mitigated by React's default output escaping, strict Tailwind utility styling without `dangerouslySetInnerHTML`, and CSP-ready headers in Spring Security.

---

## 5. Security Verdict

Phase F6 passes all security verification gates with **ZERO critical, high, or medium residual risks**. The implementation adheres strictly to zero-trust principles, zero-mock architecture, and backend cryptographic contracts.
