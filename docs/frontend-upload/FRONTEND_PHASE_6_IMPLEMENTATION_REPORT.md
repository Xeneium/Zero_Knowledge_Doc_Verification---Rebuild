# FRONTEND PHASE 6 IMPLEMENTATION REPORT
## Document Upload Experience
### Zero-Knowledge Document Verification Platform

---

## 1. Executive Summary

Phase 6 implements a secure, accessible, production-ready document upload and registration experience integrated with the platform's Spring Boot backend and Phase 5 authentication architecture.

In strict adherence to the project's **Inspect-First** doctrine and non-negotiable workflow requirements:
1. **Plain Document Upload**: Users can upload any standard existing document (PDF, PNG, JPEG, Plain Text) without requiring a QR code to be embedded within that document.
2. **Workflow Separation**: Document registration and QR-based verification are kept strictly separate. Uploading registers document metadata and computes an authoritative SHA-256 digest on the server; public capability verification tokens and standalone verification QR codes are generated post-registration.
3. **Cryptographic Ground Truth**: The frontend never fabricates digests or digital signatures. Spring Boot computes authoritative SHA-256 digests and signs artifacts using RSASSA-PSS with keys managed in `KeyRegistry`.
4. **Dedicated Experience**: Created a dedicated `DocumentUploadPage.tsx` at `/upload` (role-gated to `ROLE_ISSUER` and `ROLE_ADMIN`) while keeping it fully integrated into the existing `IssuerDashboardPage.tsx` workspace.

All 296 tests across 25 test files passed (100% pass rate), static type checking completed with 0 errors (`tsc --noEmit`), linting completed with 0 warnings (`eslint . --max-warnings 0`), and the production build succeeded in 2.48s.

---

## 2. Existing Upload Architecture & Backend Contract

### 2.1 Verified REST Endpoint
- **Path**: `POST /api/documents`
- **Controller**: `com.zkdoc.api.document.DocumentController.java`
- **Service**: `com.zkdoc.document.DocumentUploadService.java`
- **Security**: Requires authenticated caller with `ROLE_ISSUER` (or `ROLE_ADMIN` if configured in `SecurityConfiguration.java`). Unauthenticated requests yield `401 Unauthorized`; insufficient roles yield `403 Forbidden`.

### 2.2 Multipart Request Contract
- **Method**: `POST`
- **Content-Type**: `multipart/form-data` with browser-generated boundary (Axios/fetch auto-detects `FormData`).
- **Form Part**:
  - `file`: The binary document file (`MultipartFile`).
- **No Deceptive / Fake Metadata**: The backend controller method signature:
  ```java
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UploadResponse> upload(
          @RequestParam("file") MultipartFile file,
          Authentication authentication) throws IOException
  ```
  accepts **only** the `file` multipart part. Caller identity is securely derived from Spring Security's `Authentication` object (`authentication.getName()`). In strict compliance with Section 4.5 of the engineering directives, no fake metadata fields (e.g., description, category, tags) were added to the frontend interface.

### 2.3 Authoritative Response Contract
On HTTP 201 Created:
```json
{
  "id": "987fcdeb-51a2-43d7-9012-345678901234",
  "digest": "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
  "publicToken": "c0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ff",
  "verificationUrl": "https://zkdoc.example.com/verify?token=c0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ff"
}
```

### 2.4 QR Code Capability Contract
- **Path**: `GET /api/documents/{id}/qr`
- **Content-Type**: `image/png`
- **Payload**: Contains the encoded public verification capability URL (`/api/public/verify/{publicToken}`).

---

## 3. Supported File Formats & Size Constraints

Constraints were verified directly from backend source code:
- `com.zkdoc.document.storage.DocumentContentValidator.java`
- `application.yml` (`spring.servlet.multipart.max-file-size: 25MB`)

| Constraint | Limit / Rule | Client Validation | Backend Enforcement |
| :--- | :--- | :--- | :--- |
| **Max File Size** | 25 MB (`26,214,400` bytes) | Validated in `validateDocumentFile()` before network flight | Enforced by Tomcat multipart parser and `DocumentUploadService` |
| **Empty Files** | 0 bytes forbidden | Rejects 0-byte selection | Enforced with HTTP 400 Bad Request |
| **Filename Length**| $\le 255$ characters | Validated in `validateDocumentFile()` | Sanitized by `DocumentContentValidator.sanitizeFilename()` |
| **Allowed Formats**| PDF, PNG, JPEG, Plain Text | Checks extension and reported MIME type | Magic byte verification (`%PDF`, PNG header, JPEG header, null-byte checks) |

---

## 4. Implemented Upload Components & Architecture

### 4.1 Dedicated Route: `DocumentUploadPage.tsx`
- **Location**: `frontend/src/routes/DocumentUploadPage.tsx`
- **Route**: `/upload` (Protected under `<RequireRole allowedRoles={['ISSUER', 'ADMIN']}>`)
- **Structure**:
  - `PageHeader` with title "Document Registration & Integrity Anchoring", role badge `ROLE_ISSUER ACTIVE`, and back-link to `/issuer`.
  - Informational alert emphasizing that **no embedded QR is required** in uploaded files.
  - Architectural breakdown:
    1. SHA-256 Digest computation
    2. RSA-PSS signature anchoring
    3. Public capability token issuance
    4. Minimal disclosure guarantee
  - Embeds the core `DocumentUploadCard`.
  - Technical constraints reference card detailing accepted formats, 25 MB limit, and magic byte content inspection policies.

### 4.2 Enhanced Interactive Card: `DocumentUploadCard.tsx`
- **Location**: `frontend/src/features/issuer/components/DocumentUploadCard.tsx`
- **Capabilities**:
  - Drag-and-drop dropzone with visual highlight during drag-over events.
  - Native file picker with keyboard access (`tabIndex={0}`, Enter / Space triggers file dialog).
  - Selected file card displaying original filename, formatted size, and MIME type.
  - File replacement without requiring prior deletion ("Replace" button opens file picker; new selection smoothly swaps the file).
  - File removal ("Remove" button clears selection and resets form state).
  - Duplicate submission guard: Button is disabled during in-flight upload (`disabled={!selectedFile || isUploading}`); handler drops concurrent triggers.
  - Indeterminate accessible progress bar (`role="progressbar"`, `aria-label="Document upload progress"`).
  - Error recovery: On network failure or server error, the selected file is **preserved** in UI state, allowing the user to immediately retry.
  - Success display:
    - Non-sensitive document label: original filename and formatted file size.
    - Document UUID with `CopyToClipboard`.
    - Authoritative SHA-256 digest formatted with `FormattedHash` and `CopyToClipboard`.
    - 64-char public verification token with `CopyToClipboard`.
    - Direct verification URL with clickable external link and `CopyToClipboard`.
    - Real QR Code image fetched from `/api/documents/{id}/qr` with download button.
    - Workflow separation callout: Explains that the original document did not need a QR code and that public verification can proceed without revealing private document contents.
    - Direct navigation: "Open in Public Verification" button linking to `/verify?token={token}`.
    - "Upload Another Document" action that safely revokes created object URLs and resets component state.

---

## 5. Upload Lifecycle & State Model

The component implements a clean, uncontradictory state machine:

```
[Empty] 
   |-- (File selected) --> [Selected]
                              |-- (Invalid size/type) --> [Validation Error]
                              |-- (Valid file) --> [Ready]
                                                     |-- (Submit clicked) --> [Uploading (Progress Bar)]
                                                                                |-- (HTTP Error) --> [Upload Error (File Preserved)]
                                                                                |-- (HTTP 201)   --> [Success (Metadata & QR Displayed)]
```

- **Cancellation & Teardown**: Component cleanup revokes blob URLs created for the QR code via `URL.revokeObjectURL(qrBlobUrl)` to prevent browser memory leaks.
- **Teardown Listeners**: In-flight requests are automatically invalidated if the user logs out during upload via `authStore.getSessionEpoch()`.

---

## 6. Authentication Integration

- **Route Guard**: The upload page is protected by `<RequireAuth>` and `<RequireRole allowedRoles={['ISSUER', 'ADMIN']}>`. Unauthenticated users are redirected to `/login?returnUrl=/upload`.
- **Bearer Token Attachment**: `issuerApi.uploadDocument(file)` routes through `client.ts`, which automatically injects:
  ```
  Authorization: Bearer <active_jwt_token>
  ```
- **Session Expiration**: If the issuer's token expires during an upload, `client.ts` catches the `401 Unauthorized`, clears local session state, and smoothly redirects the user to `/login` with an expiration warning banner.

---

## 7. Files Created and Modified

### Created Files:
- `frontend/src/routes/DocumentUploadPage.tsx`: Dedicated document registration page.
- `frontend/tests/uploadComponents.test.tsx`: Comprehensive test suite (12 tests) for Phase 6 upload workflows, validations, accessibility, and security.

### Modified Files:
- `frontend/src/app/App.tsx`: Registered `/upload` under `<RequireRole allowedRoles={['ISSUER', 'ADMIN']}>`.
- `frontend/src/features/issuer/components/DocumentUploadCard.tsx`: Added file replacement, accessible progress bar, non-sensitive document label in success view, workflow separation notice, and public verification link.

### Dependencies Added or Avoided:
- **No external upload libraries added**: Avoided heavyweight dependencies (`dropzone.js`, `uppy`, `react-dropzone`). All interactions leverage browser-native HTML5 File APIs and standard React hooks.

---

## 8. Features Deferred to Later Phases

1. **Direct Document-Upload Verification**: Assigned to Verifier workflows in Phase 8 (`/api/documents/verify-upload`).
2. **QR Code Scanning from Camera / Image**: Assigned to Verifier workflows in Phase 7 (`ZXing` / browser video scanning).
3. **Verification History & Audit Dashboards**: Assigned to Verifier and Admin phases.
