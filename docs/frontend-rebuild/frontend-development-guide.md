# Frontend Development & Operations Guide

**Project:** Zero-Knowledge Document Verification Platform  
**Target Audience:** Frontend Engineers, Platform Developers & QA  
**Document ID:** ZKDOC-FE-DEV-001  
**Status:** Approved Operational Guide  

---

## 1. Prerequisites & Environment

Before developing or building the frontend, ensure the following tools are installed:

- **Node.js:** `v24.21.0` (LTS) — *Note: Also automatically downloaded by Maven during reactor builds.*
- **npm:** `11.19.0`
- **Java Development Kit:** OpenJDK `21.0.12.1` LTS or newer
- **Maven Wrapper:** Apache Maven 3.9.16 (invoked via `.\mvnw.cmd` on Windows or `./mvnw` on Linux/macOS)

---

## 2. Dependency Installation

To install frontend dependencies cleanly from the lockfile:

```powershell
# From the repository root:
npm --prefix frontend ci
```

*Note:* In sandboxed or offline development environments, all packages are pre-installed in `frontend/node_modules/`.

---

## 3. Development Workflow & Commands

All frontend development commands are run inside the `frontend/` directory or using `npm --prefix frontend`:

| Workflow Task | Command | Description |
| :--- | :--- | :--- |
| **Start Development Server** | `npm --prefix frontend run dev` | Boots Vite dev server at `http://localhost:5173/` with HMR. |
| **Run Unit Tests (Watch)** | `npm --prefix frontend run test` | Launches Vitest in interactive watch mode. |
| **Run Unit Tests (Single Run)** | `npm --prefix frontend run test:run` | Executes all 233 unit, component, and integration tests across 21 suites. |
| **Type Check** | `npm --prefix frontend run typecheck` | Executes `tsc --noEmit` to verify type safety. |
| **Linter Check** | `npm --prefix frontend run lint` | Runs ESLint flat config with 0 warnings tolerance. |
| **Format Check** | `npm --prefix frontend run format:check` | Verifies code conforms to Prettier standards. |
| **Format Write** | `npm --prefix frontend run format` | Automatically formats files using Prettier. |
| **Production Build** | `npm --prefix frontend run build` | Compiles SPA into `../zkdoc-api/src/main/resources/static`. |

---

## 4. Reusable Component Import Structure

Phase F4 and F5 established a centralized, accessible component system organized by functional domain:

```typescript
// Authentication & Route Protection (Phase F5)
import { RequireAuth, RequireRole } from '../components/auth';
import { useAuthStore } from '../state/authStore';
import { validateReturnUrl } from '../lib/utils/security';

// Foundation UI Primitives (Phase F4)
import { Button, Input, TextArea, Select, Checkbox, RadioGroup, FormField, Badge, Card, Divider, Tooltip } from '../components/common';

// Feedback & Overlay Components (Phase F4)
import { Alert, Dialog, ConfirmationModal, Spinner, Skeleton, EmptyState, ErrorState, ToastProvider, useToast } from '../components/feedback';

// Tabular & Metadata Data Presentation (Phase F4)
import { Table, Pagination, SearchFilterBar, CopyToClipboard, FormattedHash, KeyValueList, DetailPanel } from '../components/data';

// Cryptographic Verification Status (Phase F4)
import { VerificationStatusCard, VerificationBadge } from '../components/verification';

// Application Layout & Shell (Phase F4)
import { AppLayout, Header, Footer, SkipLink, Breadcrumbs, PageHeader, MobileNavDrawer } from '../components/layout';
```

---

## 5. Authentication, Roles, and Development Session Setup

Phase F5 integrates frontend authentication with the backend's Spring Security OAuth2 Resource Server.

### 5.1 Token Architecture & ADR-003 Storage Policy
- **No Mock Tokens:** Fabricated signatures (`demo-signature-*`) are completely disabled. All tokens must be real Bearer JWTs matching backend key authority standards.
- **Storage Policy:** Tokens are stored in **in-memory Zustand state** with tab-isolated `sessionStorage` fallback. Tokens are **NEVER stored in `localStorage`**.
- **Proactive Expiration:** An automated timer triggers session logout immediately when the JWT `exp` timestamp is reached.

### 5.2 Connecting a Session in Development
1. Navigate to `http://localhost:5173/login` or click **"Connect Session"** in the navigation header.
2. Paste a signed Bearer JWT issued by the key authority (`https://auth.zkdoc.internal`, audience `zkdoc-api`).
3. The real-time claims inspector verifies the token algorithm (`RS256` or `PS256`), extracts user subject (`sub`), role authorities (`roles`), and computes remaining expiration time.
4. Click **"Connect Session"**. The store activates the session and safely redirects to the requested return URL.

### 5.3 Programmatic Testing Token Injection
During automated tests or console evaluation, sessions can be injected directly into the store:
```typescript
import { useAuthStore } from './state/authStore';

// Set a valid testing token:
useAuthStore.getState().setToken(signedJwt);

// Query user permissions:
const isIssuer = useAuthStore.getState().hasRole('ISSUER');
const isAdmin = useAuthStore.getState().hasRole('ADMIN');

// Terminate session:
useAuthStore.getState().logout();
```

---

## 6. Development Ports & Reverse Proxy Configuration

- **Frontend Dev Server:** `http://localhost:5173/`
- **Backend Spring Boot API:** `http://localhost:8080/`
- **Reverse Proxy:** Vite's dev server (`vite.config.ts`) automatically forwards all calls to `/api` and `/actuator` to `http://localhost:8080`, eliminating CORS issues during development:
  ```typescript
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  }
  ```

---

## 7. Combined Spring Boot Integration & Production Packaging

The production frontend is served directly by the Spring Boot standalone JAR.

### Building the Combined System:
```powershell
# Execute full Maven compile and static bundle packaging:
.\mvnw.cmd clean package -DskipTests
```
This triggers `frontend-maven-plugin` which:
1. Pins Node `v24.21.0` and npm `11.19.0`.
2. Runs `npm ci --no-audit --no-fund`.
3. Runs `npm run build`.
4. Copies the compiled assets into `zkdoc-api/target/classes/static/`.

### Running the Combined System:
```powershell
# Start Spring Boot standalone JAR:
java -jar zkdoc-api/target/zkdoc-api-0.1.0-SNAPSHOT.jar
```
The application will be accessible at `http://localhost:8080/`.

---

## 8. Testing the Backend Static Integration

To verify that Spring Boot's `SpaForwardController` correctly forwards deep links to the compiled SPA:

```powershell
# Run the dedicated FrontendIntegrationTest:
.\mvnw.cmd test -pl zkdoc-api -am -o "-Dtest=FrontendIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```
Expected output:
```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- in com.zkdoc.api.FrontendIntegrationTest
BUILD SUCCESS
```

---

## 9. Troubleshooting & Common Pitfalls

1. **`Property 'toBeInTheDocument' does not exist`:**  
   Ensure `import '@testing-library/jest-dom/vitest';` is present in `tests/setup.ts`.
2. **`No tests matching pattern 'FrontendIntegrationTest'`:**  
   When running Maven tests across reactor modules with `-am`, always supply `"-Dsurefire.failIfNoSpecifiedTests=false"` so modules without the specified test class do not fail the build.
3. **`npm ci` network timeouts in offline environments:**  
   Do not run `npm install` without access to npm registries; the lockfile is committed and offline-reproducible.

---

## 10. Phase F6 Issuer Workflows & API Reference

Phase F6 introduces comprehensive issuer capabilities for document uploading, credential issuance, and lifecycle management.

### 10.1 Feature Imports
```typescript
// Issuer Domain Components (Phase F6)
import {
  DocumentUploadCard,
  CredentialIssuanceCard,
  CredentialLifecycleCard,
  DocumentVerifyQrCard,
  IssuerActivityTable,
} from '../features/issuer';

// Issuer Session Store & Activities (Phase F6)
import { issuerStore, useIssuerActivities } from '../state/issuerStore';

// Issuer REST API Client (Phase F6)
import { issuerApi } from '../api/issuerApi';

// File Validation Engine (Phase F6)
import { validateUploadFile, formatBytes, sanitizeFilename } from '../lib/utils/fileValidation';
```

### 10.2 REST API Methods (`issuerApi`)
The typed `issuerApi` client wraps all 13 authoritative backend issuer endpoints:
- `issuerApi.uploadDocument(file)`: `POST /api/documents` (multipart)
- `issuerApi.getDocument(id)`: `GET /api/documents/{id}`
- `issuerApi.verifyStoredDocument(id)`: `GET /api/documents/{id}/verify` (Mode A)
- `issuerApi.verifyUploadedDocument(id, file)`: `POST /api/documents/{id}/verify-upload` (Mode B)
- `issuerApi.getDocumentQrBlob(id)`: `GET /api/documents/{id}/qr` (image/png)
- `issuerApi.issueCredential(payload)`: `POST /api/credentials/issue`
- `issuerApi.verifyCredential(id)`: `GET /api/credentials/{id}/verify`
- `issuerApi.getCredentialLifecycle(id)`: `GET /api/credentials/{id}/lifecycle`
- `issuerApi.suspendCredential(id, reason)`: `POST /api/credentials/{id}/lifecycle/suspend`
- `issuerApi.resumeCredential(id, reason)`: `POST /api/credentials/{id}/lifecycle/resume`
- `issuerApi.revokeCredential(id, reason)`: `POST /api/credentials/{id}/lifecycle/revoke`
- `issuerApi.getCredentialLifecycleHistory(id)`: `GET /api/credentials/{id}/lifecycle/history`
- `issuerApi.createCapability(payload)`: `POST /api/verification/capabilities`
- `issuerApi.getCapabilityQrBlob(id)`: `GET /api/verification/capabilities/{id}/qr`

### 10.3 Programmatic Activity Recording
Components record actions into the tab-scoped activity ledger:
```typescript
issuerStore.addActivity({
  id: response.id,
  type: 'document',
  title: file.name,
  digest: response.sha256Digest,
  status: 'STORED & SIGNED',
});
```

---

## 11. Phase F7 Holder Workflows & API Reference

Phase F7 introduces privacy-preserving holder capabilities for managing credentials, self-managing lifecycle states, and generating zero-knowledge range proofs.

### 11.1 Feature Imports
```typescript
// Holder Domain Components (Phase F7)
import {
  HolderCredentialsListCard,
  ProvisionCredentialCard,
  HolderStatusActionModal,
  HolderCredentialDetailModal,
  PredicateProofGeneratorCard,
  HolderCapabilityQrCard,
} from '../features/holder';

// Holder Reactive State & Cache (Phase F7)
import {
  holderStore,
  useHolderStore,
  useHolderCredentials,
  useHolderSelectedCredential,
  useHolderProofCache,
  useHolderFilterState,
} from '../state/holderStore';

// Holder REST API Client (Phase F7)
import { holderApi } from '../api/holderApi';
```

### 11.2 REST API Methods (`holderApi`)
The typed `holderApi` client integrates with all 11 backend holder endpoints without mocks:
- `holderApi.getCredentials()`: `GET /api/holder/credentials` (fetches holder credentials)
- `holderApi.getCredentialById(id)`: `GET /api/holder/credentials/{id}` (single credential)
- `holderApi.provisionCredential(payload)`: `POST /api/holder/credentials/provision` (wallet provisioning)
- `holderApi.suspendCredential(id, reason)`: `POST /api/holder/credentials/{id}/suspend` (self-suspend)
- `holderApi.activateCredential(id, reason)`: `POST /api/holder/credentials/{id}/activate` (reactivate)
- `holderApi.revokeCredential(id, reason)`: `POST /api/holder/credentials/{id}/revoke` (permanent revocation)
- `holderApi.generatePredicateProof(payload)`: `POST /api/holder/credentials/proofs/generate` (Bulletproof ZKP)
- `holderApi.verifyCredentialSignature(id)`: `GET /api/credentials/{id}/verify` (digital signature check)
- `holderApi.getCredentialLifecycleHistory(id)`: `GET /api/credentials/{id}/lifecycle/history` (audit events)
- `holderApi.createCapability(payload)`: `POST /api/verification/capabilities` (mint verification token)
- `holderApi.getCapabilityQrBlob(id)`: `GET /api/verification/capabilities/{id}/qr` (PNG QR code stream)

### 11.3 State Management & Proof Cache
Components subscribe to reactive holder state via `useSyncExternalStore`:
```typescript
// Subscribe to holder credentials list
const credentials = useHolderCredentials();

// Subscribe to generated ZKP presentation proofs (capped at 20 entries)
const proofCache = useHolderProofCache();

// Record a newly generated Bulletproof artifact
holderStore.addProofArtifact(proofResponse);
```

### 11.4 Cryptographic Nonce Generation
To prevent proof-replay attacks, zero-knowledge proofs require fresh nonces:
```typescript
const array = new Uint8Array(16);
window.crypto.getRandomValues(array);
const freshNonce = Array.from(array, (b) => b.toString(16).padStart(2, '0')).join('');
```

---

## 12. Phase F8 Verifier Workflows & API Reference

Phase F8 introduces public and authenticated verifier capabilities, live QR-code scanning via `@zxing/browser`, candidate file upload integrity checks, Bulletproof range-proof verification, and cryptographic vs. operational error demarcation.

### 12.1 Feature Imports
```typescript
// Verifier Domain Components (Phase F8)
import {
  QrScannerCard,
  ManualReferenceCard,
  DocumentUploadVerifyCard,
  PredicateProofVerifyCard,
  VerificationResultCard,
  VerificationHistoryCard,
} from '../features/verifier';

// Public Verification Page Route (Phase F8)
import { PublicVerificationPage } from '../routes/PublicVerificationPage';

// Verifier Reactive State & Ledger (Phase F8)
import {
  verifierStore,
  useVerifierStore,
  useVerifierHistory,
  useVerifierActiveResult,
  useVerifierSelectedRecord,
} from '../state/verifierStore';

// Verifier REST API Client (Phase F8)
import { verifierApi } from '../api/verifierApi';

// QR Code Payload Parser & Sanitizer (Phase F8)
import {
  parseQrPayload,
  extractTokenCandidate,
  hexToBase64,
  base64ToHex,
  isUuid,
} from '../lib/utils/qrPayload';
```

### 12.2 REST API Methods (`verifierApi`)
The typed `verifierApi` client integrates with all 7 authoritative backend verification endpoints without mocks or simulated trust results:
- `verifierApi.verifyPublicToken(token)`: `GET /api/public/verify/{token}` (public document verification by token)
- `verifierApi.verifyCapabilityToken(token)`: `GET /api/public/verify/capability/{token}` (public capability-based verification)
- `verifierApi.verifyUploadedDocument(id, file)`: `POST /api/documents/{id}/verify-upload` (Mode B candidate file upload verification against stored SHA-256 and RSA-PSS signature)
- `verifierApi.verifyStoredDocument(id)`: `GET /api/documents/{id}/verify` (Mode A stored document record cryptographic signature & digest audit)
- `verifierApi.verifyCredential(id)`: `GET /api/credentials/{id}/verify` (credential RSA digital signature check & operational status)
- `verifierApi.getCredentialLifecycle(id)`: `GET /api/credentials/{id}/lifecycle` (credential lifecycle state & revocation check)
- `verifierApi.verifyCredentialPredicate(payload)`: `POST /api/v2/zkp/credential-predicates/verify` (authenticated Bulletproof range-proof verification)

### 12.3 QR Code Scanning Architecture & Error Demarcation
- **Explicit Gesture Activation:** Camera streams are requested only upon explicit user interaction (`Start Camera Scanner`), preventing unsolicited device prompts or background activation.
- **Hardware Resource Release:** Video tracks are deterministically terminated upon user stop or component unmount via `stream.getTracks().forEach((track) => track.stop())`.
- **Offline File Upload Fallback:** Decodes QR codes from user-uploaded images (`.png`, `.jpg`, `.jpeg`, `.webp`, `.svg`) using `@zxing/browser` without requiring webcam hardware.
- **Untrusted Payload Sanitization:** The `parseQrPayload()` parser enforces a 2048-character limit, safely decodes canonical `QR_V1` JSON schemas, extracts tokens from HTTPS URLs, validates hexadecimal tokens and UUIDs, and strictly rejects dangerous URI schemes (`javascript:`, `data:`, `vbscript:`).
- **Cryptographic vs. Operational Separation:** When a verification fails, the UI distinguishes between:
  1. *Cryptographic / Integrity / Revocation Failures* (`HTTP 200` with `verified: false` or `status: SUSPENDED/REVOKED`): Highlighted as an explicit tamper alert.
  2. *Operational / Network / Availability Failures* (`HTTP 404`, `HTTP 500`, `HTTP 503`): Accompanied by a prominent **Trust Preservation Notice** clarifying that the document cannot be confirmed due to an infrastructure outage or missing record, explicitly preventing false-tamper conclusions.

### 12.4 Verification Metrics & Automated Test Suite
- **17 Test Suites, 188 Unit & Integration Tests (100% Pass Rate):**
  - `src/features/verifier/__tests__/verifierStore.test.ts` (Store initialization, reactive subscriptions, history ledger FIFO rotation)
  - `src/features/verifier/__tests__/qrPayload.test.ts` (Schema parsing, dangerous URL rejection, bounds checking, hex/base64 conversions)
  - `src/features/verifier/__tests__/ManualReferenceCard.test.ts` (Format detection, token extraction, form submission)
  - `src/features/verifier/__tests__/PublicVerificationPage.test.ts` (Route parameter loading, tab navigation, end-to-end verification workflows)
  - Comprehensive coverage across existing F4–F7 components and security utilities.

---

## 13. Administrator & Security Audit Workflows (Phase F9)

### 13.1 Administrative Component Import Structure
```typescript
// Administrator Dashboard & Components (Phase F9)
import {
  SystemOverviewCard,
  KeyRegistryCard,
  RegisterKeyModal,
  KeyStatusModal,
  KeyRotateModal,
  KeyPemModal,
  CredentialOversightCard,
  PredicateInspectionCard,
} from '../features/admin';

// Security Audit & Hash Chain Components (Phase F9)
import {
  AuditIntegrityCard,
  AuditEventsListCard,
  AuditEventDetailModal,
} from '../features/audit';

// Administrator & Audit Page Routes (Phase F9)
import { AdminDashboardPage } from '../routes/AdminDashboardPage';
import { AuditLogsPage } from '../routes/AuditLogsPage';

// Administrator Reactive Store (Phase F9)
import {
  adminStore,
  useAdminStore,
  useAdminKeys,
  useAdminRecoveryReport,
  useAdminAuditQueryResult,
  useAdminAuditFilter,
  useAdminIntegrityResult,
  useAdminCredentialOversight,
} from '../state/adminStore';

// Administrator & Audit REST API Client (Phase F9)
import { adminApi } from '../api/adminApi';
```

### 13.2 REST API Methods (`adminApi`)
The typed `adminApi` client integrates with all 17 authoritative backend administrative and audit endpoints:
- `adminApi.getRecoveryStatus()`: `GET /api/admin/recovery/status` (Flyway schema, artifact counts, active keys, credential integrity)
- `adminApi.reconcilePlatform()`: `POST /api/admin/recovery/reconcile` (Triggers post-recovery reconciliation across all platform anchors)
- `adminApi.listKeys()`: `GET /api/admin/keys` (Retrieves registered public keys and trust anchors)
- `adminApi.getKey(keyId)`: `GET /api/admin/keys/{keyId}` (Queries single key specification and status)
- `adminApi.getKeyPem(keyId)`: `GET /api/admin/keys/{keyId}/pem` (Retrieves X.509 certificate and SHA-256 fingerprint)
- `adminApi.registerKey(req)`: `POST /api/admin/keys` (Enrolls new RSA public key anchor with Zero Private Key Policy guard)
- `adminApi.changeKeyStatus(keyId, req)`: `POST /api/admin/keys/{keyId}/status` (Modifies key lifecycle status with REVOKED protection)
- `adminApi.rotateKey(keyId, req)`: `POST /api/admin/keys/{keyId}/rotate` (Rotates trust anchor to replacement key identifier)
- `adminApi.queryAuditEvents(filter)`: `GET /api/admin/audit/events` (Paginated, filtered security audit event retrieval)
- `adminApi.getAuditEvent(eventId)`: `GET /api/admin/audit/events/{eventId}` (Fetches single audit record with hash-chain linkage)
- `adminApi.verifyAuditIntegrity(fromSeq, toSeq)`: `GET /api/admin/audit/verify-integrity` (Validates continuous SHA-256 audit hash chain)
- `adminApi.getCredentialLifecycle(id)`: `GET /api/credentials/{id}/lifecycle` (Inspects authoritative credential status and version)
- `adminApi.getCredentialLifecycleHistory(id)`: `GET /api/credentials/{id}/lifecycle/history` (Chronological lifecycle transition timeline)
- `adminApi.verifyCredential(id)`: `GET /api/credentials/{id}/verify` (Validates RSASSA-PSS digital signature and issuer state)
- `adminApi.suspendCredential(id, reason)`: `POST /api/credentials/{id}/suspend` (Administrative credential suspension)
- `adminApi.resumeCredential(id, reason)`: `POST /api/credentials/{id}/resume` (Reinstates suspended credential to active status)
- `adminApi.revokeCredential(id, reason)`: `POST /api/credentials/{id}/revoke` (Irreversible administrative revocation)

### 13.3 Cryptographic Trust & Hash-Chain Verification Principles
- **Zero Private Key Policy:** The trust registry exclusively accepts and stores public key certificates. Private keys reside solely with issuing authorities and holders.
- **Terminal Key Compromise:** Revocation to `REVOKED` is treated as a terminal compromise event; the UI permanently disables re-activation.
- **Continuous SHA-256 Hash Chain:** Audit events form an unbroken cryptographic chain `H(n) = SHA-256(H(n-1) || ...)` enabling mathematical tamper detection across arbitrary sequence windows.
- **Honest Authorization Demarcation:** Informs operators that Zero-Knowledge Bulletproof predicate verification strictly requires `ROLE_VERIFIER` on the backend, cleanly separating administrative oversight from relying verifier evaluation.

### 13.4 Test Suite Totals (Phase F1–F9)
- **21 Test Suites, 233 Unit & Integration Tests (100% Pass Rate):**
  - `tests/adminApi.test.ts` (18 tests: all 17 admin endpoints + network error handling)
  - `tests/adminStore.test.ts` (11 tests: state mutators, subscribers, snapshots)
  - `tests/adminComponents.test.tsx` (10 tests: system overview, key registry, modals, credential oversight, predicate inspector)
  - `tests/auditComponents.test.tsx` (6 tests: hash chain verification, audit table, detail modal, JSON export, audit page)
  - Comprehensive coverage across existing F4–F8 features (188 tests across 17 suites)
- **Backend Integration Test:** `FrontendIntegrationTest.java` (9/9 tests pass with Spring Boot v3.4.3 and Flyway v13 migrations)

---

## 14. Phase F10: Frontend Security Hardening, Privacy Protection & Adversarial Testing

Phase F10 solidifies the security perimeter of the rebuilt frontend across all operational roles without altering backend Spring Boot contracts.

### 14.1 Session Teardown & Cross-Session Isolation
The frontend guarantees that user identity transitions (logout, session expiration, or account switching) immediately purge all confidential data from browser memory and `sessionStorage`:
```typescript
// Register domain teardown in authStore.ts
import { registerTeardownListener } from '../state/authStore';

registerTeardownListener(() => {
  holderStore.clearState();
  issuerStore.clearState();
  adminStore.clearState();
  verifierStore.clearState();
});
```

### 14.2 Monotonic Session Epoch & Stale Async Rejection
Slow or delayed API responses that resolve after an account switch or logout are prevented from polluting store memory via a monotonically increasing `sessionEpoch` in [authStore.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/state/authStore.ts). The API client in [client.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/api/client.ts) verifies that the epoch at request time matches the epoch at resolution; if mismatched, it throws `ApiError(499, 'Client Session Terminated')`.

### 14.3 Filename Sanitization & Unicode Bidi Spoofing Defense
In [fileValidation.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/lib/utils/fileValidation.ts), `sanitizeFilename()` eliminates:
- Unicode Right-To-Left Overrides (`\u202E`) and zero-width format characters used for extension disguise.
- Null bytes (`\0`) and ASCII control characters.
- Recursive nested path traversal sequences (`..`).
- Buffer boundaries exceeding 255 characters.

### 14.4 Client-Side Magic Bytes Inspection (MIME Parity)
The utility `validateFileMagicBytes()` in [fileValidation.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/lib/utils/fileValidation.ts) inspects raw binary signatures to detect polyglot executables before transmission:
- PDF: `%PDF` (`[0x25, 0x50, 0x44, 0x46]`)
- PNG: 8-byte PNG signature (`[0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]`)
- JPEG: SOI marker (`[0xFF, 0xD8, 0xFF]`)
- Text: Rejection of ELF (`0x7F, 0x45, 0x4C, 0x46`) / MZ (`0x4D, 0x5A`) and non-text binary null bytes.

### 14.5 Zero Private Key Policy Enforcement
In [RegisterKeyModal.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/admin/components/RegisterKeyModal.tsx), user inputs containing `PRIVATE KEY` or `-----BEGIN RSA PRIVATE KEY-----` are intercepted immediately, purged from component state and DOM, and blocked with a prominent security violation notice.

### 14.6 Object URL & MediaStream Track Termination
- Blob URLs created via `URL.createObjectURL()` are tracked and revoked deterministically on component unmount and inside `finally` blocks in [QrScannerCard.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/verifier/components/QrScannerCard.tsx) and [DocumentUploadCard.tsx](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/issuer/components/DocumentUploadCard.tsx).
- Scanner camera streams explicitly iterate `MediaStream.getTracks().forEach(track => track.stop())` to release physical camera hardware.

### 14.7 Security Headers & Parity
Configured in [vite.config.ts](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/vite.config.ts) and [index.html](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/index.html):
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: camera=(self), microphone=(), geolocation=()`

### 14.8 Test Suite Totals (F1–F10 Baseline)
- **22 Test Suites, 255 Automated Unit & Integration Tests** covering all individual role components and security protections.

---

## 15. Backend Integration, Cross-Workflow Testing & Runtime Diagnostics (Phase F11)

### 15.1 Integration Architecture Overview
Phase F11 reconciles the rebuilt React/TypeScript frontend with the real Spring Boot backend (`zkdoc-api`), validating end-to-end data flows, role boundaries, cryptographic operations, and error handling across:
- **Issuer Workflows:** Multipart upload to `/api/documents`, Mode B capability minting at `/api/capabilities`, and signed credential issuance at `/api/credentials/issue`.
- **Holder Workflows:** Wallet provisioning at `/api/holder/credentials/provision`, inventory listing, and Bulletproof zero-knowledge predicate proof generation at `/api/holder/credentials/proofs/generate`.
- **Verifier Workflows:** Unauthenticated public capability verification at `/api/public/verify/capability/{token}`, authenticated credential verification at `/api/credentials/{id}/verify`, and zero-knowledge predicate evaluation at `/api/v2/zkp/credential-predicates/verify`.
- **Administrator Workflows:** Recovery status inspection and platform reconciliation at `/api/admin/recovery`, trust anchor registration at `/api/admin/keys`, and security audit hash-chain integrity verification at `/api/admin/audit/verify-integrity`.

### 15.2 Cross-Workflow Integration Test Suite
The automated test suite in [`crossWorkflowIntegration.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/crossWorkflowIntegration.test.ts) exercises the full multi-role lifecycle:
1. **Issuer Role:** Creates documents, mints verification capabilities, and issues signed verifiable credentials.
2. **Session Teardown Cascade:** Transitions identities between Issuer, Holder, Verifier, and Admin, verifying synchronous state purges across all domain stores.
3. **Holder Role:** Provisions credentials into personal wallet, verifies IDOR rejection against other users' credentials, and generates zero-knowledge Bulletproof range proofs ($GPA \ge 300$) without disclosing the underlying value ($380$).
4. **Verifier Role:** Verifies capability tokens anonymously with minimal disclosure (zero PII leaked), evaluates cryptographic signatures, verifies Bulletproof proofs via strict 6-field JSON payloads, and confirms rejection of tampered documents.
5. **Administrator Role:** Inspects recovery health, triggers reconciliation, registers RSA trust anchors, inspects audit logs, and evaluates continuous SHA-256 hash chains.
6. **RBAC & Security Boundaries:** Verifies 403 Forbidden for unauthorized issuance and 401 Unauthorized for unauthenticated administrative queries.
7. **Resilience & Concurrency:** Tests network timeouts, 500 server errors, 502 Bad Gateway fallback, and in-flight session epoch invalidation (HTTP 499).

### 15.3 Running the Complete Validation Pipeline
Developers must execute the following commands to ensure all quality gates are satisfied:

```powershell
# 1. Linting (Zero warnings allowed)
npm --prefix frontend run lint

# 2. TypeScript compilation check
npm --prefix frontend run typecheck

# 3. Unit and integration tests
npm --prefix frontend run test -- --run

# 4. Production build (Outputs to zkdoc-api/src/main/resources/static/)
npm --prefix frontend run build

# 5. Backend integration tests (Verify Spring Boot & database integrity)
.\mvnw.cmd test -pl zkdoc-api
```

### 15.4 Known Backend Routing Considerations (DEF-F11-001)
`SpaForwardController.java` maps GET requests for `/`, `/verify`, `/credentials`, `/verification`, `/predicate-verification`, `/audit-logs`, and `/administration` to forward to `index.html`. Direct browser address-bar entry to `/login`, `/issuer`, or `/holder` from an external window produces a 404 from Spring Boot until the backend forwarder is updated. In-app navigation via client-side links works across all routes without issue.

### 15.5 Final Comprehensive Test Suite Totals (F1–F11 Complete)
- **23 Test Suites, 259 Automated Unit & Integration Tests (100% Pass Rate):**
  - [`tests/crossWorkflowIntegration.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/crossWorkflowIntegration.test.ts) (4 tests: multi-role lifecycle, network errors, predicate rejection, epoch guard)
  - [`tests/securityHardening.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/securityHardening.test.tsx) (22 tests)
  - [`tests/authComponents.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/authComponents.test.tsx) (12 tests)
  - [`tests/authStore.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/authStore.test.ts) (11 tests)
  - [`tests/adminApi.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/adminApi.test.ts) (18 tests)
  - [`tests/adminStore.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/adminStore.test.ts) (11 tests)
  - [`tests/adminComponents.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/adminComponents.test.tsx) (10 tests)
  - [`tests/auditComponents.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/auditComponents.test.tsx) (6 tests)
  - [`tests/issuerApi.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/issuerApi.test.ts) (13 tests)
  - [`tests/issuerStore.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/issuerStore.test.ts) (7 tests)
  - [`tests/issuerComponents.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/issuerComponents.test.tsx) (13 tests)
  - [`tests/holderApi.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/holderApi.test.ts) (11 tests)
  - [`tests/holderStore.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/holderStore.test.ts) (8 tests)
  - [`tests/holderComponents.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/holderComponents.test.tsx) (9 tests)
  - [`tests/verifierApi.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/verifierApi.test.ts) (7 tests)
  - [`tests/verifierStore.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/verifierStore.test.ts) (7 tests)
  - [`tests/verifierComponents.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/verifierComponents.test.tsx) (9 tests)
  - [`tests/fileValidation.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/fileValidation.test.ts) (14 tests)
  - [`tests/qrPayload.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/qrPayload.test.ts) (14 tests)
  - [`tests/security.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/security.test.ts) (7 tests)
  - [`tests/client.test.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/client.test.ts) (4 tests)
  - [`tests/components.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/components.test.tsx) (34 tests)
  - [`tests/App.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/App.test.tsx) (8 tests)
- **Quality Gates:** 0 TypeScript compile errors (`tsc --noEmit`), 0 ESLint warnings (`eslint . --max-warnings 0`), 100% green Vitest execution (`vitest run`).
- **Backend Spring Boot Integration:** 204 backend tests passing across all 6 Maven modules (`mvnw.cmd test -o`). Zero backend modifications.

---

## 16. Production Acceptance, Deployment Guide, and Operational Handover (Phase F12)

### 16.1 Production Architecture & Topologies
The platform operates as a unified full-stack application where the static React 19 frontend is bundled directly inside the Spring Boot 3.4 JAR at `classpath:/static/` and served over HTTP port 8080.

Two production topologies are supported:
1. **Containerized Deployment (Recommended):**
   - Built via the multi-stage [`Dockerfile`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/Dockerfile) (`eclipse-temurin:21-jre-alpine`).
   - Runs as unprivileged user `10001:10001` (`zkdoc:zkdoc`) with a read-only root filesystem (`read_only: true`), capability dropping (`cap_drop: ALL`), and tmpfs storage for `/tmp` and `/app/tmp`.
   - Coordinated via [`docker-compose.yml`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docker-compose.yml) connecting to PostgreSQL 16 on an isolated internal network (`zkdoc-backend-net`).
2. **Standalone Executable JAR:**
   - Package via `mvnw clean package -DskipTests`.
   - Run via `java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar target/zkdoc-api-0.1.0-SNAPSHOT.jar`.

### 16.2 Essential Production Environment Variables
| Variable | Production Value | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `postgres` or `prod` | Enables PostgreSQL datasource and Flyway migrations. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<HOST>:5432/<DB>` | PostgreSQL JDBC connection URL. |
| `SPRING_DATASOURCE_USERNAME` | `<RESTRICTED_APP_USER>` | Least-privilege PostgreSQL user. |
| `SPRING_DATASOURCE_PASSWORD` | `<STRONG_SECRET>` | Externalized password injected via secret manager. |
| `ZKDOC_PUBLIC_VERIFICATION_BASE_URL` | `https://verify.yourdomain.com` | External base URL used to construct public verification QR codes. |
| `ZKDOC_STORAGE_BASE_DIR` | `/var/lib/zkdoc/artifacts` | Volume mount for encrypted document payload storage. |
| `SERVER_PORT` | `8080` | Internal server port for Spring Boot. |

### 16.3 Edge Reverse Proxy & TLS Termination
Spring Boot runs on HTTP port 8080. A production reverse proxy (Nginx, Caddy, Cloudflare, AWS ALB) must terminate TLS 1.3, enforce HSTS (`Strict-Transport-Security: max-age=31536000; includeSubDomains`), enforce `client_max_body_size 25M`, and forward client IP headers (`X-Forwarded-Proto`, `X-Forwarded-For`). Refer to Section 7 of [`f12-deployment-readiness.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/frontend-rebuild/f12-deployment-readiness.md) for a ready-to-deploy Nginx configuration.

### 16.4 Operational Probes & Health Checks
- **Liveness:** `GET /actuator/health/liveness` returns `{"status":"UP"}`.
- **Readiness:** `GET /actuator/health/readiness` returns `{"status":"UP"}` when database and filesystem storage are available.
- **Application Health:** `GET /api/health` returns operational subsystem health.
- **Platform Reconciliation:** `POST /api/admin/recovery/reconcile` (restricted to `ROLE_ADMIN`) re-evaluates all document digests, public key states, credential signatures, and audit hash chains after a disaster recovery event.

### 16.5 Backup & Disaster Recovery Runbook
Execute automated database dumps and artifact backups via:
- Docker Compose: `docker compose run --rm backup`
- PowerShell: `.\scripts\backup.ps1 -BackupDir ".\backups" -RetentionDays 7`
- Bash: `./scripts/backup.sh`

### 16.6 Final Release Decision & Operational Status
The application is certified as **`READY WITH GAPS`**. All core application workflows, cryptographic proofs, accessibility standards, and security controls are 100% operational. The identified gaps (reverse proxy TLS termination, external IdP provisioning, and expanding `@GetMapping` routes in `SpaForwardController.java`) are documented with clear operational mitigations in [`f12-known-limitations.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/frontend-rebuild/f12-known-limitations.md).



