# FRONTEND PHASE 6 UPLOAD SECURITY REVIEW
## Document Upload Experience
### Zero-Knowledge Document Verification Platform

---

## 1. Executive Summary

This Security Review analyzes the attack surface, input validation controls, sensitive data handling, and authorization boundaries associated with the Document Upload Experience implemented in Phase 6.

In accordance with zero-trust principles:
> **The browser client is an untrusted environment. Frontend file checks exist purely to enhance user experience; the Spring Boot backend remains the single authoritative security and validation boundary.**

---

## 2. Threat Modeling & Defensive Analysis

### 2.1 File-Size & DoS Protection
- **Threat**: An attacker attempts to upload an excessively large file (e.g., 5 GB) to cause server out-of-memory errors, exhaust disk storage, or saturate network bandwidth.
- **Defensive Posture**:
  - **Frontend**: `validateDocumentFile()` intercepts files larger than 25 MB (`26,214,400` bytes) prior to network transmission and disables the submission button.
  - **Backend Layer 1 (Servlet Container)**: Spring Boot / Tomcat enforces `spring.servlet.multipart.max-file-size: 25MB` and `max-request-size: 25MB`, rejecting requests with HTTP 413 Payload Too Large before buffering.
  - **Backend Layer 2 (Application Service)**: `DocumentContentValidator.MAX_ALLOWED_FILE_SIZE` strictly validates byte length in `DocumentUploadService.java`.

### 2.2 MIME Spoofing & Polyglot File Attacks
- **Threat**: An attacker uploads an executable binary or malicious shell script renamed to `document.pdf` to bypass client-side checks and attempt execution on server or downstream clients.
- **Defensive Posture**:
  - **Frontend Limitation**: The client checks file extension and browser-reported MIME types. The frontend **never assumes** that a `.pdf` extension guarantees safety.
  - **Backend Magic Byte Verification**: `DocumentContentValidator.java` inspects raw initial bytes:
    - PDF: Checks `%PDF` (`0x25 0x50 0x44 0x46`).
    - PNG: Checks `\x89PNG\r\n\x1a\n` (`0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A`).
    - JPEG: Checks `\xFF\xD8\xFF`.
    - Plain Text: Rejects binary null bytes (`0x00`) and executable headers (`ELF`, `MZ`).

### 2.3 Filename Handling & Directory Traversal
- **Threat**: A user uploads a file with a malicious filename like `../../etc/passwd` or `<script>alert(1)</script>.pdf` to execute directory traversal or Cross-Site Scripting (XSS).
- **Defensive Posture**:
  - **Frontend**: React JSX renders filenames as pure strings, automatically escaping HTML characters and preventing DOM XSS injection (tested and verified in `tests/uploadComponents.test.tsx`).
  - **Backend Sanitization**: `DocumentContentValidator.sanitizeFilename()` strips path traversal sequences (`..`, `/`, `\`), null bytes (`\0`), control characters (`\r`, `\n`, `\t`), and bounds length to 255 characters.

### 2.4 Sensitive Document Data Protection & Privacy
- **Threat**: Confidential document contents (e.g., health records, diplomas, identity documents) linger in unencrypted browser storage or appear in telemetry/analytics logs.
- **Defensive Posture**:
  - **Zero Storage Persistence**: Document files and raw byte streams are **never** written to `localStorage` or `sessionStorage` (verified via automated regression tests in `tests/uploadComponents.test.tsx`).
  - **Memory Hygiene**: Files are kept in ephemeral browser memory only for the duration of the upload.
  - **Zero-Logging Policy**: Request interceptors in `client.ts` do not log raw payload contents.
  - **Object URL Revocation**: Blob URLs created for QR code rendering are explicitly revoked via `URL.revokeObjectURL()` on component unmount and session reset.

### 2.5 Duplicate Submissions & Re-entrancy
- **Threat**: Double-clicking or network lag causes multiple concurrent uploads of the same file, resulting in duplicate records or race conditions.
- **Defensive Posture**:
  - `DocumentUploadCard` guards the submit handler with `if (!selectedFile || isUploading) return;` and disables the submit button immediately upon invocation.
  - If a network error occurs, the user's selected file is safely preserved in the component state, allowing controlled, intentional retry without duplicate network requests.

### 2.6 Cryptographic Integrity & Workflow Separation
- **Threat**: The frontend fabricates or computes digital signatures client-side, risking private key exposure in the browser.
- **Defensive Posture**:
  - The frontend never performs digital signing and holds no RSA private keys.
  - All cryptographic digests (SHA-256) and RSASSA-PSS digital signatures are generated authoritatively by the Spring Boot backend using keys in `KeyRegistry`.
  - The UI explicitly clarifies that original documents do **not** need to contain embedded QR codes; document registration and QR capability generation are distinct architectural steps.

---

## 3. Findings Matrix

| Finding ID | Component | Severity | Description | Status / Resolution |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-UP-F01** | `DocumentUploadCard.tsx` | Informational | Client validation relies on extension and browser MIME. | **Documented Boundary**: Client validation is for UX feedback only. Backend magic byte verification is authoritative. |
| **SEC-UP-F02** | `DocumentUploadCard.tsx` | Low | Object URLs for QR images could leak memory if unmanaged. | **Remediated**: `URL.revokeObjectURL(qrBlobUrl)` executed on unmount and reset. |
| **SEC-UP-F03** | Backend Storage | Medium | Static file storage does not run external antivirus scanning. | **Documented Backend Recommendation**: Recommend integrating ClamAV or equivalent containerized scanner for high-risk production deployments. |
| **SEC-UP-F04** | API Client | Low | Ambiguous network timeout could leave server registration status uncertain. | **Documented Boundary**: Frontend preserves file and displays error message; avoids blind automatic retries that could duplicate records. |

---

## 4. Verified Findings vs. Assumptions

### Verified Facts:
1. Backend enforces 25 MB max size via Tomcat and `DocumentContentValidator.java`.
2. Backend inspects magic byte headers for PDF, PNG, JPEG, and plain text.
3. Backend rejects unauthenticated requests with HTTP 401 and non-issuer roles with HTTP 403.
4. Client-side XSS injection via malicious filenames is completely prevented by JSX string escaping.
5. Zero document bytes are persisted to browser local/session storage.

### Assumptions & Non-Guarantees:
1. The platform does not scan documents for active malware/macros (e.g. embedded JavaScript in PDFs); it verifies integrity and format headers.
2. The platform relies on secure TLS transport to protect document bytes in flight.
