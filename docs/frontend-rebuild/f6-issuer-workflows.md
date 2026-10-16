# F6: Issuer Workflows — Secure Document Upload, Credential Issuance, and Lifecycle Management

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F6  
**Document ID:** ZKDOC-FE-F6-001  
**Status:** Approved Implementation Report  
**Date:** October 2026  

---

## 1. Executive Summary & Objectives

Frontend Phase F6 implements production-ready issuer workflows for the Zero-Knowledge Document Verification Platform. 

In strict alignment with the platform's backend implementation (Spring Boot, PostgreSQL, Spring Security OAuth2 Resource Server, and cryptographic signing authorities):
1. **Zero Mock Endpoints or Fabricated Statistics:** Every workflow integrates directly with verified backend controllers (`DocumentController.java`, `CredentialController.java`, `VerificationCapabilityController.java`). No synthetic platform metrics, dummy success messages, or unverified mock responses exist.
2. **Authoritative Cryptographic Operations:** Document uploads invoke the backend's SHA-256 digest computation and RSA-PSS digital signing. The frontend surfaces the authoritative UUID, SHA-256 digest, and QR verification artifacts.
3. **Multi-Attribute Credential Issuance:** Provides dynamic attribute definition (STRING, INTEGER, DATE, BOOLEAN), automated issuer DID binding to the authenticated user principal, and two-step review dialogs prior to cryptographic commitment.
4. **Complete Credential Lifecycle Management:** Implements audited status transitions (`ACTIVE` -> `SUSPENDED` -> `ACTIVE` or `REVOKED`) with mandatory justification reasons, real-time lifecycle event timelines, and cryptographic signature verification.
5. **Dual-Mode Document Verification & Capabilities:** Integrates Mode A (stored-copy signature verification) and Mode B (upload-verification against stored hash), alongside time-bounded verification capability token minting with QR generation.
6. **Ephemeral Session Activity Ledger:** Maintains a zero-mock, tab-scoped activity history (capped at 50 entries) stored in `sessionStorage` (`zkdoc_issuer_activity`), offering quick navigation, status updates, and copyable fingerprints without persistent server leakage.

---

## 2. Backend Contract Alignment & Ground Truth

All frontend issuer operations strictly map to verified backend REST endpoints:

| Functional Area | HTTP Method & Path | Controller / Service | Security & Auth Constraints |
| :--- | :--- | :--- | :--- |
| **Document Upload** | `POST /api/documents` | `DocumentController.java` | `ROLE_ISSUER` or `ROLE_ADMIN`<br>Multipart with `"file"` param<br>Max 25 MB; PDF, PNG, JPEG, TXT |
| **Document Metadata** | `GET /api/documents/{id}` | `DocumentController.java` | `ROLE_ISSUER`, `ROLE_ADMIN`, or Owner |
| **Mode A Verify (Stored)** | `GET /api/documents/{id}/verify` | `DocumentController.java` | Authenticated / Owner check |
| **Mode B Verify (Upload)** | `POST /api/documents/{id}/verify-upload` | `DocumentController.java` | Multipart with `"file"` param |
| **Document QR Image** | `GET /api/documents/{id}/qr` | `DocumentController.java` | Produces `image/png` binary stream |
| **Issue Credential** | `POST /api/credentials/issue` | `CredentialController.java` | `ROLE_ISSUER` or `ROLE_ADMIN`<br>Enforces object auth: `issuerId == user.subject` |
| **Verify Credential** | `GET /api/credentials/{id}/verify` | `CredentialController.java` | Evaluates signature, status, and expiration |
| **Lifecycle Status** | `GET /api/credentials/{id}/lifecycle` | `CredentialController.java` | Returns current state, version, and timestamps |
| **Suspend Credential** | `POST /api/credentials/{id}/lifecycle/suspend` | `CredentialController.java` | Requires audit reason; increments version |
| **Resume Credential** | `POST /api/credentials/{id}/lifecycle/resume` | `CredentialController.java` | Restores `ACTIVE` status from `SUSPENDED` |
| **Revoke Credential** | `POST /api/credentials/{id}/lifecycle/revoke` | `CredentialController.java` | Terminal state transition; requires audit reason |
| **Lifecycle History** | `GET /api/credentials/{id}/lifecycle/history` | `CredentialController.java` | Chronological audit log of state transitions |
| **Mint Capability** | `POST /api/verification/capabilities` | `VerificationCapabilityController.java` | Generates time-bounded verification token |
| **Capability QR Code** | `GET /api/verification/capabilities/{id}/qr` | `VerificationCapabilityController.java` | Produces `image/png` capability QR stream |

---

## 3. Architecture & Modular Structure

The issuer feature is encapsulated within `frontend/src/features/issuer/` and orchestrated by `frontend/src/routes/IssuerDashboardPage.tsx`:

```
frontend/src/
├── api/
│   ├── client.ts                     # Enhanced fetch wrapper with Blob & Accept support
│   ├── endpoints.ts                  # Centralized endpoint URI constants
│   ├── issuerApi.ts                  # Typed client for all 13 issuer backend endpoints
│   └── types.ts                      # Authoritative TypeScript domain interfaces
├── features/issuer/
│   ├── components/
│   │   ├── DocumentUploadCard.tsx    # Drag-and-drop upload, SHA-256 preview, Mode A verify
│   │   ├── CredentialIssuanceCard.tsx # Multi-attribute credential builder & review dialog
│   │   ├── CredentialLifecycleCard.tsx# Status inspector, suspend/resume/revoke modal, history
│   │   ├── DocumentVerifyQrCard.tsx  # Dual-mode verification & capability QR minting
│   │   └── IssuerActivityTable.tsx   # Tab-scoped session activity ledger & filter tabs
│   └── index.ts                      # Barrel exports for issuer features
├── lib/utils/
│   └── fileValidation.ts             # MIME type, extension, size, and filename validation
├── routes/
│   └── IssuerDashboardPage.tsx       # Tabbed workspace (overview, upload, issue, lifecycle, verify-qr)
└── state/
    ├── authStore.ts                  # useAuth() hook and session state
    └── issuerStore.ts                # Session activity store with observer pattern
```

---

## 4. Detailed Component & Workflow Specifications

### 4.1 Document Upload Workflow (`DocumentUploadCard.tsx`)
- **Drag-and-Drop Dropzone:** Accessible keyboard- and mouse-friendly dropzone supporting PDF, PNG, JPEG, and Plain Text files.
- **Client-Side Pre-flight Validation:** Enforces the backend's 25 MB limit (`DocumentContentValidator.java`), checks MIME type and filename extension consistency, and sanitizes filenames before dispatching.
- **Authoritative Cryptographic Output:** Once uploaded via `POST /api/documents`, the card displays:
  - Authoritative Document UUID (with one-click copy button).
  - Authoritative SHA-256 Digest (formatted with truncated middle and copy helper).
  - Digital Signature Algorithm banner (RSA-PSS with SHA-256).
- **Inline Post-Upload Actions:** Allows immediate Mode A verification of the stored server copy and retrieves the signed QR code image without page reload.
- **Activity Recording:** Automatically records the document in `issuerStore` with `STORED & SIGNED` status.

### 4.2 Multi-Attribute Credential Issuance (`CredentialIssuanceCard.tsx`)
- **Authenticated Authority Binding:** Pre-populates and locks the `issuerId` field to the authenticated user's principal (e.g. `urn:zkdoc:issuer:alice`), preventing object-level authorization rejection (`ObjectAuthorizationService.java`).
- **Dynamic Attribute Builder:** Allows dynamic addition and removal of credential attributes with type selection (`STRING`, `INTEGER`, `DATE`, `BOOLEAN`).
- **Two-Step Review & Confirmation:** A confirmation dialog displays the complete attribute schema, target subject, and validity period before the issuer commits to cryptographic issuance.
- **Authoritative Digest & Navigation:** Displays the generated `credentialId` and cryptographic `digest` upon issuance, with a direct action to inspect its lifecycle.

### 4.3 Credential Lifecycle & State Management (`CredentialLifecycleCard.tsx`)
- **Lifecycle Status Inspection:** Fetches status, version, issuance timestamp, expiration, and last suspension/revocation details.
- **Audited State Transitions:** Supports `suspend`, `resume`, and `revoke` actions via `<ConfirmationModal>`. Requires a mandatory justification reason which is logged to the backend's audit trail.
- **Live Transition History Timeline:** Fetches and displays the full chronological event log from `GET /api/credentials/{id}/lifecycle/history` with actor identity, timestamp, and state delta (`ACTIVE` -> `SUSPENDED`).
- **Cryptographic Signature Verification:** Directly executes `GET /api/credentials/{id}/verify` to validate the integrity of the credential's cryptographic signature against the key authority.

### 4.4 Verification & Capability Tools (`DocumentVerifyQrCard.tsx`)
- **Mode A Verification:** Verifies the stored document copy and its RSA-PSS signature against the database record (`GET /api/documents/{id}/verify`).
- **Mode B Verification:** Accepts a local document copy from the user and verifies that its computed SHA-256 hash matches the stored document record (`POST /api/documents/{id}/verify-upload`).
- **QR Code Retrieval:** Fetches and displays the public verification QR code (`GET /api/documents/{id}/qr`) with download support.
- **Verification Capability Minting:** Integrates with `VerificationCapabilityController.java` to mint time-bounded capability tokens (`POST /api/verification/capabilities`) and renders the associated capability QR code.

### 4.5 Ephemeral Session Activity Ledger (`IssuerActivityTable.tsx` & `issuerStore.ts`)
- **Zero Mock Architecture:** Does not query unauthenticated global databases or invent platform statistics. Instead, tracks real actions performed by the issuer during their active session.
- **Tab-Scoped Storage:** Synchronized with `window.sessionStorage` under key `zkdoc_issuer_activity`. Automatically purged on tab close or session disconnect.
- **Bounded Capacity:** Enforces a hard cap of 50 activities to avoid unbounded memory growth.
- **Filtering & Quick Navigation:** Provides filter tabs (`All`, `Documents`, `Credentials`) and one-click navigation to inspect any item in the lifecycle or verification workspace.

---

## 5. Security & Architectural Compliance

1. **Strict Object-Level Authorization (No Impersonation):** The frontend ensures that the credential `issuerId` matches the authenticated subject (`user.subject`). If mismatched, the backend's `ObjectAuthorizationService` rejects the operation with HTTP 403.
2. **Authoritative File Constraints:** All upload operations strictly comply with the backend limits (`DocumentContentValidator.java`): 25 MB max size and MIME type restrictions (PDF, PNG, JPEG, Plain Text).
3. **No Secret or Key Material on Client:** Digital signatures are generated exclusively by the backend key authority. The frontend handles only public IDs, hashes, and verification tokens.
4. **Binary Stream Handling:** Binary QR codes are fetched via `apiBlobRequest` with appropriate `Accept` headers (`image/png`), converted to object URLs via `URL.createObjectURL`, and revoked on unmount to prevent browser memory leaks.
