# FRONTEND PHASE 7 HANDOVER
## Document Upload Experience to QR Code Scanning & Verification
### Zero-Knowledge Document Verification Platform

---

## 1. Phase 6 Completion Status

**Status: PASS — Phase 6 Completed.**

Phase 6 has established a fully validated, accessible, and secure document upload and registration interface. The platform supports uploading ordinary documents (PDF, PNG, JPEG, Plain Text) without requiring embedded QR codes, authoritatively registers them with Spring Boot, and displays the resulting cryptographic capability tokens and verification references.

All quality gates pass with 100% compliance across 296 tests in 25 test suites.

---

## 2. Reusable Upload Components & Integration Points

Phase 7 developers can immediately leverage or integrate with the following components:

### 2.1 Reusable Components
- **`DocumentUploadCard` (`frontend/src/features/issuer/components/DocumentUploadCard.tsx`)**:
  Complete drag-and-drop file upload component with pre-validation, progress feedback, file replacement/removal, authoritative metadata display, and QR presentation.
- **`DocumentUploadPage` (`frontend/src/routes/DocumentUploadPage.tsx`)**:
  Dedicated full-page upload route at `/upload`.
- **`validateDocumentFile` (`frontend/src/lib/utils/fileValidation.ts`)**:
  Authoritative preliminary validator enforcing the 25 MB bound, 255-character filename ceiling, and allowed extensions (`.pdf`, `.png`, `.jpg`, `.jpeg`, `.txt`).
- **`formatFileSize` (`frontend/src/lib/utils/fileValidation.ts`)**:
  Utility for human-readable byte formatting.
- **`FormattedHash` (`frontend/src/components/data/FormattedHash.tsx`)**:
  Truncated cryptographic digest display with accessible labels and copy buttons.

---

## 3. Verified Backend Upload & Capability Contracts

### 3.1 Document Upload Contract
- **Endpoint**: `POST /api/documents`
- **Request Format**: `multipart/form-data` with form field `file`.
- **Required Authority**: `ROLE_ISSUER` or `ROLE_ADMIN`.
- **Response**:
  ```json
  {
    "id": "uuid",
    "digest": "64-char-hex",
    "publicToken": "64-char-hex",
    "verificationUrl": "https://host/verify?token={token}"
  }
  ```

### 3.2 Document QR Code Endpoint
- **Endpoint**: `GET /api/documents/{id}/qr`
- **Response**: Raw PNG image bytes containing the verification link:
  `/api/public/verify/{publicToken}`.

---

## 4. Separation of Registration from Verification

> **Critical Phase 7 Architecture Rule:**
> **The original document uploaded in Phase 6 does NOT contain a QR code.**
> 
> The platform strictly separates:
> 1. **Document Registration (Phase 6)**: The issuer submits an ordinary document to the platform. The platform registers its SHA-256 hash, signs it, and produces a public capability token.
> 2. **QR Code Scanning & Verification (Phase 7)**: A verifier uses camera or file scanning to read a verification QR code (or inputs the capability token) to verify authenticity against the platform without needing the original document.
> 3. **Direct Document-Upload Verification (Phase 8)**: An authenticated verifier submits a candidate document copy to `/api/documents/verify-upload` to prove its SHA-256 hash matches the authoritative record.

Phase 7 must focus purely on **QR Code Scanning & Capability Verification**. It must not require issuers to embed QR codes into their original certificates or documents before upload.

---

## 5. Guidance for Phase 7 — QR Code Scanning & Verification

When commencing Phase 7, adhere to the following principles:

1. **Camera & Device Scanner Integration**:
   - Use browser-native `navigator.mediaDevices.getUserMedia` or an approved accessible library (e.g., ZXing) for scanning QR codes from video streams.
   - Always provide a manual fallback where users can paste or type the 64-character verification capability token or upload a QR image file.
2. **Permission Handling & Privacy**:
   - Request camera permissions only upon explicit user interaction ("Start Camera Scanner").
   - Handle permission denial gracefully with clear instructions to paste the token or upload a QR image.
   - Stop camera video tracks immediately when the scanner is closed or unmounted.
3. **Payload Parsing**:
   - The QR code contains either a full verification URL (`https://.../verify?token=...`) or a raw capability token.
   - Extract the token safely using regex: `[a-f0-9]{64}`.
4. **Public Verification Route**:
   - Phase 7 should integrate with `/verify` and `/verify/:token` (`PublicVerificationPage.tsx`), which is already configured as a public, unauthenticated route in `App.tsx`.
