# Phase 2 — UX Acceptance Criteria & Verification Matrix
## Zero-Knowledge Document Verification Platform

---

## 1. Overview & Acceptance Methodology

This document establishes the measurable, testable acceptance criteria for the user experience and interface behavior of the **Zero-Knowledge Document Verification Platform**, fulfilling the requirements of **Phase 2: UX Research & User Journey Planning**.

### 1.1 Acceptance Principles
- **No Subjective Criteria:** Criteria must describe observable, testable system behaviors (e.g. HTTP status responses, DOM elements, keyboard interactions, ARIA announcements) rather than subjective quality statements.
- **Explicit Backend Dependencies:** Every criterion identifies the exact backend endpoint or client module required for fulfillment.
- **Priority Gating:** Criteria are prioritized as **P0 (Critical / Blocker)**, **P1 (High)**, or **P2 (Medium)**.

---

## 2. Document Registration & Upload Criteria (Journey A)

| ID | User Journey | Expected System Behavior | Verification Method | Backend Dependency | Priority |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **AC-REG-001** | Journey A (Registration) | The document dropzone accepts PDF, PNG, JPG, and JSON files up to 25.0 MB (26,214,400 bytes). | Automated test with mock files of 5 MB, 24.9 MB, and 25.1 MB. | `fileValidation.ts` | **P0** |
| **AC-REG-002** | Journey A (Registration) | If a file exceeding 25.0 MB is selected, the dropzone immediately rejects it with a plain-language error and does not initiate an HTTP request. | Component test asserting error text and 0 HTTP calls. | Client-side only | **P0** |
| **AC-REG-003** | Journey A (Registration) | During upload of valid documents, the interface displays an active upload and hashing progress indicator; the submit button is disabled to prevent duplicate submission. | Vitest interaction test asserting disabled state during in-flight promise. | `DocumentUploadCard.tsx` | **P0** |
| **AC-REG-004** | Journey A (Registration) | Upon successful registration (HTTP 201), the interface displays a receipt card with Document ID, formatted SHA-256 hash, and embedded QR code. | Integration test verifying receipt DOM elements upon mock 201 response. | `POST /api/documents`, `GET /api/documents/{id}/qr` | **P0** |
| **AC-REG-005** | Journey A (Registration) | If the backend returns HTTP 422 (inactive or suspended signing key), the interface alerts the user with guidance to contact the platform administrator. | Component test mocking 422 response asserting specific warning copy. | `POST /api/documents` | **P1** |
| **AC-REG-006** | Journey A (Registration) | If the network connection drops during upload, the interface displays a retry button that re-attempts submission without forcing the user to re-select the file. | Simulated offline network test asserting retry handler preserves `selectedFile`. | Client state management | **P1** |

---

## 3. QR-Code Document Verification Criteria (Journey B)

| ID | User Journey | Expected System Behavior | Verification Method | Backend Dependency | Priority |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **AC-QR-001** | Journey B (QR Verify) | The public QR verification console is accessible anonymously at `/verify` without requiring an authenticated session or login. | Route test asserting `/verify` renders under `unauthenticated` state. | `AppLayout.tsx`, Public routing | **P0** |
| **AC-QR-002** | Journey B (QR Verify) | Clicking "Start Camera Scanner" prompts for camera permissions with `{ facingMode: "environment" }`. | Spy test on `navigator.mediaDevices.getUserMedia`. | ZXing Browser API | **P0** |
| **AC-QR-003** | Journey B (QR Verify) | If the user denies camera permission, the interface displays browser permission recovery steps and provides an immediate alternative to upload a QR image. | Mock `NotAllowedError` test asserting fallback UI and file picker button. | `QrScannerCard.tsx` | **P0** |
| **AC-QR-004** | Journey B (QR Verify) | When a valid QR code is decoded, the camera hardware stream is stopped immediately (`track.stop()`) to preserve battery and privacy. | Spy test on `MediaStreamTrack.stop()` upon successful decode. | `@zxing/browser` | **P0** |
| **AC-QR-005** | Journey B (QR Verify) | Valid verification responses display an emerald "Authentic & Verified" badge with Issuer Name and Date, while keeping raw document contents completely hidden. | Component assertion test verifying absence of document body text. | `GET /api/public/verify/capability/{token}` | **P0** |
| **AC-QR-006** | Journey B (QR Verify) | If the capability token has been revoked, the interface displays an amber warning banner indicating the revocation date and reason. | Component test mocking revoked capability response. | `GET /api/public/verify/capability/{token}` | **P0** |
| **AC-QR-007** | Journey B (QR Verify) | When navigating to deep link `/verify/:token`, the platform automatically initiates verification for the URL token without requiring manual scan. | Integration test mounting `/verify/cap_test123` asserting automated fetch. | `PublicVerificationPage.tsx` | **P1** |

---

## 4. Direct Document Verification Criteria (Journey C — Independent Modality)

| ID | User Journey | Expected System Behavior | Verification Method | Backend Dependency | Priority |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **AC-DIR-001** | Journey C (Direct Verify) | Direct document verification is presented as an independent, primary tab on `/verify` and does not require or mention QR scanning. | DOM inspection asserting Tab 2 exists independently of Tab 1. | `PublicVerificationPage.tsx` | **P0** |
| **AC-DIR-002** | Journey C (Direct Verify) | The interface clearly communicates that any authentic digital file can be verified without containing an embedded QR code. | Copy inspection test verifying instructional banner text. | `DocumentUploadVerifyCard.tsx` | **P0** |
| **AC-DIR-003** | Journey C (Direct Verify) | Submitting an authentic document file to `/api/public/verify/upload` displays "Authentic & Untampered" with matching issuer metadata. | Integration test mocking HTTP 200 verification match. | `POST /api/public/verify/upload` | **P0** |
| **AC-DIR-004** | Journey C (Direct Verify) | Submitting an altered or unregistered file displays "Integrity Verification Failed: No matching registered record" without confusing it with a network failure. | Integration test mocking HTTP 404/422 mismatch response. | `POST /api/public/verify/upload` | **P0** |
| **AC-DIR-005** | Journey C (Direct Verify) | Technical cryptographic details (computed SHA-256 hash, key ID) are concealed inside an expandable accordion drawer to prevent visual clutter. | Vitest interaction test asserting accordion defaults to collapsed state. | `VerificationResultCard.tsx` | **P1** |

---

## 5. Operator Authentication & Session Criteria (Journey D)

| ID | User Journey | Expected System Behavior | Verification Method | Backend Dependency | Priority |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **AC-AUTH-001** | Journey D (Authentication) | JWT tokens with `alg: "none"` or symmetric `alg: "HS256"` are strictly rejected by the client parser before any session is created. | Unit test passing `alg: "none"` token asserting rejection message. | `authStore.ts` | **P0** |
| **AC-AUTH-002** | Journey D (Authentication) | Tokens whose `exp` claim is in the past are rejected with the formatted expiration timestamp. | Unit test passing expired token asserting expiration message. | `authStore.ts` | **P0** |
| **AC-AUTH-003** | Journey D (Authentication) | Valid tokens display live decoded claims (subject, roles, issuer, expiration) in a preview card as the token is typed or pasted. | Component test asserting live claim elements in `LoginPage.tsx`. | `LoginPage.tsx` | **P1** |
| **AC-AUTH-004** | Journey D (Authentication) | Successful connection stores session exclusively in `sessionStorage` (`zkdoc_auth_session`) and never writes to `localStorage`. | Storage assertion test checking `sessionStorage` vs `localStorage`. | `authStore.ts` | **P0** |
| **AC-AUTH-005** | Journey D (Authentication) | Malicious redirect targets (e.g. `//attacker.com` or `javascript:alert(1)`) are neutralized, defaulting to internal authorized workspace. | Adversarial redirect test asserting URL sanitization in `LoginPage.tsx`. | `security.ts` | **P0** |
| **AC-AUTH-006** | Journey D (Authentication) | Clicking "Disconnect Session" purges `sessionStorage`, triggers multi-store state teardown, and redirects to `/login`. | E2E store teardown test asserting `authStore.clearToken()`. | `authStore.ts`, custom DOM event | **P0** |

---

## 6. Holder Wallet & Lifecycle Criteria (Journey E)

| ID | User Journey | Expected System Behavior | Verification Method | Backend Dependency | Priority |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **AC-MGT-001** | Journey E (Holder Wallet) | Access to `/holder` and `/credentials` is restricted to authenticated sessions with `ROLE_HOLDER` or `ROLE_ADMIN`; unauthenticated visits redirect to `/login`. | Route guard test asserting redirect with `returnUrl=%2Fholder`. | `RequireRole.tsx` | **P0** |
| **AC-MGT-002** | Journey E (Holder Wallet) | Credentials list renders active status badges (Green = `ACTIVE`, Amber = `SUSPENDED`, Red = `REVOKED`) with accessible text. | Component render test asserting badge colors and aria labels. | `GET /api/holder/credentials` | **P0** |
| **AC-MGT-003** | Journey E (Holder Wallet) | Clicking "Suspend" on a credential prompts an explicit confirmation modal before executing the status change. | Interaction test asserting confirmation modal appears before API dispatch. | `HolderStatusActionModal.tsx` | **P0** |
| **AC-MGT-004** | Journey E (Holder Wallet) | Opening credential detail modal fetches and renders the complete lifecycle history audit timeline. | Integration test mocking `GET .../lifecycle/history`. | `GET /api/credentials/{id}/lifecycle/history` | **P1** |

---

## 7. Platform Administration & Audit Criteria (Journey F)

| ID | User Journey | Expected System Behavior | Verification Method | Backend Dependency | Priority |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **AC-ADM-001** | Journey F (Administration) | Access to `/administration` is restricted exclusively to authenticated users with `ROLE_ADMIN`. Unauthorized roles redirect to `/forbidden`. | Role guard test asserting `ROLE_ISSUER` redirects to `/forbidden`. | `RequireRole.tsx` | **P0** |
| **AC-ADM-002** | Journey F (Administration) | Pasting an RSA private key into the public key registration textarea triggers an immediate red security block and hard-disables submission. | Adversarial component test pasting `-----BEGIN RSA PRIVATE KEY-----`. | `RegisterKeyModal.tsx` | **P0** |
| **AC-ADM-003** | Journey F (Administration) | Key rotation modal requires confirmation of key decommissioning and displays active public key fingerprint. | Component test asserting fingerprint display and rotation dispatch. | `POST /api/admin/keys/{id}/rotate` | **P1** |
| **AC-ADM-004** | Journey F (Audit Chain) | Clicking "Verify Hash Chain Integrity" on `/audit-logs` validates the Merkle log chain and displays exact untampered event count. | Integration test mocking `POST /api/admin/audit/verify-integrity`. | `AuditAdminController` | **P1** |

---

## 8. Zero-Knowledge Predicate Range Proof Criteria (Journey G)

| ID | User Journey | Expected System Behavior | Verification Method | Backend Dependency | Priority |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **AC-ZKP-001** | Journey G (ZKP Predicate) | Verifying a valid Bulletproofs range proof displays "Predicate Proven" and explicitly states that underlying values were not revealed. | Component test asserting mathematical disclosure disclaimer. | `POST /api/v2/zkp/credential-predicates/verify` | **P0** |
| **AC-ZKP-002** | Journey G (ZKP Predicate) | Submitting an invalid or corrupted proof vector returns a plain-language failure notice without leaking backend stack traces. | Component test asserting normalized `ApiError` display. | `CredentialPredicateController` | **P1** |
| **AC-ZKP-003** | Journey G (ZKP Predicate) | Holder can generate Bulletproofs range proof payload from numerical credential attributes with a single click. | Integration test asserting generated proof JSON structure. | `POST /api/holder/credentials/proofs/generate` | **P1** |

---

## 9. Accessibility & Universal Usability Criteria (WCAG 2.1 AA)

| ID | Category | Expected System Behavior | Verification Method | Priority |
| :--- | :--- | :--- | :--- | :---: |
| **AC-A11Y-001** | Focus Trapping | All modals (`ConfirmationModal`, `KeyPemModal`, `HolderCredentialDetailModal`) trap keyboard focus (`Tab` / `Shift+Tab`) and close on `Esc`. | Automated axe-core / userEvent test asserting focus remains within modal container. | **P0** |
| **AC-A11Y-002** | Focus Restoration | When a modal or drawer is dismissed, keyboard focus programmatically returns to the exact button element that triggered the modal. | userEvent test asserting `document.activeElement` equals trigger element after modal unmount. | **P0** |
| **AC-A11Y-003** | Color Independence | Every verification outcome (Authentic, Tampered, Revoked) uses distinct iconography and explicit text labels in addition to border color. | Visual inspection asserting non-color sensory cues. | **P0** |
| **AC-A11Y-004** | Form ARIA Linking | All input fields in upload, issuance, and login forms are programmatically associated with error messages via `aria-describedby="{id}-error"`. | Automated DOM query checking `aria-describedby` points to valid error `<span>`. | **P0** |
| **AC-A11Y-005** | Color Contrast | All body text achieves at least 4.5:1 contrast ratio against backgrounds; large headers and active UI boundaries achieve at least 3.0:1. | Automated color contrast analyzer check across `tokens.css`. | **P1** |
| **AC-A11Y-006** | Screen Reader Live Regions | Verification progress and outcome changes are announced dynamically via `aria-live="polite"` or `role="status"`. | DOM inspection asserting presence of `aria-live` containers during verification transitions. | **P1** |

---

## 10. Responsive Design & Hardware Adaptation Criteria

| ID | Category | Expected System Behavior | Verification Method | Priority |
| :--- | :--- | :--- | :--- | :---: |
| **AC-RESP-001** | Mobile Viewports | On viewports < 768px, navigation collapses into an accessible slide-over drawer with min touch target sizes of 48x48 px. | Viewport emulation test at 375x667 px checking touch bounding box. | **P0** |
| **AC-RESP-002** | Camera Viewfinder | On mobile devices, the camera viewfinder renders in a responsive 1:1 square aspect ratio without overflowing or horizontal scroll. | CSS inspection verifying `overflow-x: hidden` and `aspect-square`. | **P0** |
| **AC-RESP-003** | Desktop Dashboards | On viewports >= 1024px, feature cards arrange in a balanced two-column grid (`grid-cols-2`), keeping related actions adjacent. | Viewport emulation test at 1440x900 px asserting grid layout. | **P1** |
| **AC-RESP-004** | Hardware Degradation | When camera hardware is absent (e.g. desktop workstation without webcam), QR tab automatically displays file upload dropzone. | Mocking empty `navigator.mediaDevices.enumerateDevices` array. | **P1** |
