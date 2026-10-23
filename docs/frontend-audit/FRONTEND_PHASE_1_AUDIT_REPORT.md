# Phase 1 — Frontend Technical Audit Report
## Zero-Knowledge Document Verification Platform

---

## 1. Executive Summary & Audit Mission

### 1.1 Engagement Purpose
This document presents the authoritative findings of **Phase 1: Frontend Audit & Skill Discovery**, executed as the foundational stage of the 18-phase frontend modernization initiative for the **Zero-Knowledge Document Verification Platform**.

The objective of Phase 1 is to establish a rigorous, evidence-based baseline of the current application without introducing premature architectural rewrites or superficial visual modifications. Every finding, metric, dependency declaration, and integration contract presented in this report was verified against the active codebase and reproducible test environments.

### 1.2 Core Architectural Verdict
- **Baseline Stability:** **STABLE & VERIFIED.** The existing codebase compiles cleanly with strict TypeScript checks (`tsc --noEmit`), conforms to zero-warning ESLint rules (`eslint . --max-warnings 0`), and passes all 23 unit and integration test suites (**259 passing tests**).
- **Technology Reality:** The frontend runs on a cutting-edge modern runtime: **React 19.3.0**, **React Router 8.4.0**, **Vite 8.3.1**, and **Tailwind CSS 4.3.3**. This supersedes legacy documentation claims (which referenced React 18).
- **Core Security Posture:** Strong client-side security architecture. Session management strictly uses `sessionStorage` (preventing persistent token leakage across browser sessions), parses JWT claims following RFC 8725 / RFC 7519, rejects unsigned or symmetric tokens (`alg: "none"` / `HS*`), and includes adversarial test coverage blocking private key submissions.
- **Key Bottlenecks Identified:** The primary engineering debts are **bundle bloat** (a single 1,038.55 kB monolithic JavaScript chunk due to lack of dynamic code splitting for `@zxing/browser` and Lucide icons), minor **accessibility (a11y) gaps** in modal focus trapping, and **missing upload progress indicators** for large documents approaching the 25 MB boundary.

---

## 2. Repository Layout & Architecture

The project is structured as a Maven multi-module system housing both the Java 21 Spring Boot 3.4.3 backend and the standalone React / TypeScript frontend workspace.

```
Zero Knowledge Document Verification/
├── pom.xml                                  # Root Maven reactor POM (Java 21 / Spring Boot 3.4.3)
├── zkdoc-core-crypto/                       # Cryptographic primitives (SHA-256, RSA, Bulletproofs)
├── zkdoc-document/                          # Document metadata, hashing, and storage logic
├── zkdoc-keys/                              # Key registry, rotation, and trust anchors
├── zkdoc-credential/                        # W3C Verifiable Credentials lifecycle management
├── zkdoc-zkp/                               # Bulletproofs range proof generators & verifiers
├── zkdoc-api/                               # Spring Boot REST API application entry point
│   └── src/main/resources/
│       ├── application.yml                  # Backend configuration
│       └── static/                          # Target directory for built frontend production assets
├── frontend/                                # Frontend application workspace
│   ├── package.json                         # Node dependency declarations (React 19.3.0, Vite 8.3.1)
│   ├── vite.config.ts                       # Vite configuration, proxying, and build targets
│   ├── tsconfig.json                        # Strict TypeScript compilation rules
│   ├── eslint.config.js                     # ESLint flat configuration (zero-warnings policy)
│   ├── index.html                           # Single-page application root HTML shell
│   ├── src/                                 # Application source tree
│   │   ├── main.tsx                         # Client DOM mount entry point
│   │   ├── app/                             # Application root, routing, and error boundaries
│   │   ├── api/                             # Strongly-typed HTTP REST client and endpoints
│   │   ├── components/                      # Reusable UI component libraries (common, feedback, layout)
│   │   ├── features/                        # Domain-driven feature cards (admin, audit, holder, issuer, verifier)
│   │   ├── routes/                          # Route page views (public, authenticated, fallback)
│   │   ├── state/                           # Vanilla event-driven reactive stores (auth, issuer, holder, verifier, admin)
│   │   └── styles/                          # Tailwind CSS 4 theme and design tokens
│   └── tests/                               # Vitest unit, component, integration, and security test suites
└── docs/                                    # Architectural decision records, specs, and audit reports
```

### 2.1 Static Asset Packaging Relationship
The frontend is configured via `frontend/vite.config.ts` to output production builds directly into the backend's static resource directory:
```typescript
build: {
  outDir: '../zkdoc-api/src/main/resources/static',
  emptyOutDir: true,
  sourcemap: false,
  target: 'es2022'
}
```
This enables seamless single-container deployments where Spring Boot serves the pre-rendered single-page application from `GET /**` while serving RESTful endpoints from `/api/**`.

---

## 3. Technology Stack & Dependency Audit

The active frontend dependencies were audited against `frontend/package.json` and resolved in `frontend/package-lock.json`:

### 3.1 Production Dependencies
| Package | Declared Version | Resolved Version | Usage / Architectural Purpose | Audit Assessment |
| :--- | :--- | :--- | :--- | :--- |
| `react` | `^19.3.0` | `19.3.0` | Core UI component engine | **Modern:** Successfully upgraded from legacy React 18. Clean execution without React 18 legacy APIs. |
| `react-dom` | `^19.3.0` | `19.3.0` | DOM rendering and portal engine | Synchronized with React 19.3.0 core. |
| `react-router` | `^8.4.0` | `8.4.0` | Client-side routing & navigation | Modern React Router v8 release; clean declarative nested `<Routes>` architecture. |
| `@zxing/browser` | `^0.2.1` | `0.2.1` | Barcode & QR video capture/decoding | Performs client-side video decoding for QR verification; heavy library (~180 kB minified) currently not code-split. |
| `lucide-react` | `^1.48.0` | `1.48.0` | SVG iconography system | Consistent visual glyphs; tree-shaking active but contributes ~110 kB to monolithic bundle. |

### 3.2 Developer & Build Tooling
| Package | Declared Version | Usage / Architectural Purpose |
| :--- | :--- | :--- |
| `vite` | `^8.3.1` | High-speed ESM development server and Rollup/Rolldown production bundler. |
| `@tailwindcss/vite` & `tailwindcss` | `^4.3.3` | Tailwind CSS 4 compiler plugin with native CSS variable resolution. |
| `typescript` | `^6.0.3` | Strict type validation and transpilation. |
| `vitest` | `^5.0.2` | High-performance unit and component test runner. |
| `@testing-library/react` | `^16.3.3` | DOM and user interaction testing. |
| `@testing-library/user-event` | `^14.6.7` | High-fidelity user simulation in tests. |
| `eslint` & `typescript-eslint` | `^10.11.0` / `^8.70.1` | Flat config linter enforcing zero warnings and strict React hooks rules. |
| `prettier` | `^3.9.9` | Deterministic code formatting. |

---

## 4. Baseline Quality & Verification Results

All quality commands were executed in the project workspace to establish a definitive baseline:

### 4.1 Verification Matrix

| Quality Check | Execution Command | Result | Duration | Details & Output Evidence |
| :--- | :--- | :---: | :---: | :--- |
| **Type Checking** | `npm --prefix frontend run typecheck` | **PASSED** | 5.8s | `tsc --noEmit` exited code `0` with 0 type errors. |
| **Code Linting** | `npm --prefix frontend run lint` | **PASSED** | 8.2s | `eslint . --max-warnings 0` exited code `0` with 0 warnings or errors. |
| **Production Build** | `npm --prefix frontend run build` | **PASSED** | 1.05s | Successfully generated assets in `../zkdoc-api/src/main/resources/static`. |
| **Automated Tests** | `npm --prefix frontend run test:run` | **PASSED** | 14.28s | **23 test files passed (259/259 tests passed, 0 failed).** |

### 4.2 Production Build Output Asset Breakdown
- `index.html`: `0.81 kB` (gzip: `0.45 kB`)
- `assets/index-B4m-0JY_.css`: `69.25 kB` (gzip: `12.77 kB`)
- `assets/index-CkexoFYR.js`: `1,038.55 kB` (gzip: `273.40 kB`)
- **Vite Warning:** `Some chunks are larger than 500 kB after minification.` (Triggered by static inclusion of ZXing browser and full route features in a single bundle).

---

## 5. Component Structure & Design System

The frontend architecture follows a modular, feature-oriented component layout in `frontend/src`:

```
src/
├── components/
│   ├── auth/                        # Route protection guards (RequireAuth, RequireRole)
│   ├── common/                      # Low-level UI primitives (Button, Input, Card, Select, Badge, Tooltip)
│   ├── data/                        # Tabular & data visualization (Table, DetailPanel, KeyValueList, Pagination)
│   ├── feedback/                    # Dialogs & notifications (Alert, ConfirmationModal, Dialog, Toast, Spinner)
│   ├── layout/                      # Application shell (AppLayout, Header, Footer, Breadcrumbs, MobileNavDrawer)
│   └── verification/                # Verification status banners and result card primitives
├── features/
│   ├── admin/                       # Key management, rotation modals, and system overview cards
│   ├── audit/                       # Merkle/hash chain verification and audit event cards
│   ├── holder/                      # Credential wallet, status action modals, and ZKP proof generator
│   ├── issuer/                      # Document upload dropzone, credential issuance wizard, and lifecycle cards
│   └── verifier/                    # QR camera scanner, manual payload entry, and upload verification cards
├── routes/                          # Page-level composition components
└── state/                           # Framework-agnostic observable stores
```

### 5.1 Design Token Hierarchy
Styles are defined in `src/styles/tokens.css` and imported via `@import "tailwindcss";` in `src/styles/index.css`. The system uses CSS custom properties for typography scales, border radii, shadows, and status colors (Success, Warning, Danger, Info).

---

## 6. Route Map & Access Control Matrix

Routing is declared in `src/app/App.tsx` using `react-router` v8. The platform enforces a clear separation between public verification tools and role-restricted operator workspaces.

### 6.1 Route Inventory & Security Rules

| Route Path | View Component | Access Policy | Allowed Roles | Functional Scope |
| :--- | :--- | :---: | :---: | :--- |
| `/` | `HomePage` | Public | *Any* | Landing page, system capability overview, quick-action navigation. |
| `/login` | `LoginPage` | Public | *Any* | Manual JWT session authentication, live claims decoding preview. |
| `/verify` | `PublicVerificationPage` | Public | *Any* | Primary multi-tab verification console (QR scan, token, document upload). |
| `/verify/:token` | `PublicVerificationPage` | Public | *Any* | Deep-linked verification using URL token parameter. |
| `/verification` | `PublicVerificationPage` | Public | *Any* | Alias route for backwards compatibility with legacy verification links. |
| `/predicate-verification` | `PublicVerificationPage` | Public | *Any* | Direct link to Bulletproofs range proof verification tab. |
| `/forbidden` | `ForbiddenPage` | Public | *Any* | Explanatory error page when authenticated user lacks required role. |
| `/credentials` | `HolderDashboardPage` | Protected | `HOLDER`, `ADMIN` | Holder credential wallet, selective disclosure, self-service suspension. |
| `/holder` | `HolderDashboardPage` | Protected | `HOLDER`, `ADMIN` | Primary holder dashboard workspace. |
| `/issuer` | `IssuerDashboardPage` | Protected | `ISSUER`, `ADMIN` | Document registration, credential issuance wizard, lifecycle management. |
| `/audit-logs` | `AuditLogsPage` | Protected | `ADMIN`, `ISSUER` | Cryptographic audit trail inspection and hash chain integrity verification. |
| `/administration` | `AdminDashboardPage` | Protected | `ADMIN` | Key registry oversight, key rotation, and disaster recovery status. |
| `*` | `NotFoundPage` | Public | *Any* | 404 fallback page. |

---

## 7. State Management & Session Lifecycle

The platform uses a lightweight, vanilla TypeScript pub/sub store architecture located in `src/state/`:
- `authStore.ts`: Manages session JWT, decoded claims, and authentication status.
- `issuerStore.ts`: Coordinates document uploads, credential issuance, and issuer activity logs.
- `holderStore.ts`: Manages holder wallet credentials, filter states, and proof generation.
- `verifierStore.ts`: Holds public verification results, active camera streams, and verification history.
- `adminStore.ts`: Manages cryptographic key registries and system recovery health metrics.

### 7.1 Security & Session Hygiene Evaluation
1. **Storage Mechanism:** Tokens are persisted exclusively in `sessionStorage` (`zkdoc_auth_session`). This prevents session leakage across different browser tabs or permanent storage in `localStorage`.
2. **RFC 8725 JWT Enforcement:** Client-side parsing strictly rejects unsecured tokens (`alg: "none"`) and symmetric HMAC tokens (`alg: "HS*"`), requiring asymmetric RSA signatures (`RS256` / `PS256`).
3. **Session Teardown Broadcast:** When `authStore.clearToken()` is invoked, a custom DOM event `zkdoc:auth:cleared` is dispatched, instructing all other domain stores to purge cached in-memory data immediately.

---

## 8. Core User Workflows Analysis

The application encompasses five mission-critical workflows:

### Workflow A: Document Registration & Hash Signing
- **Actors:** Authorized Issuers (`ISSUER`, `ADMIN`).
- **Path:** `/issuer` -> Document Upload Tab.
- **Process:** User uploads a document (PDF, PNG, JPG, JSON up to 25 MB). The backend computes the document's SHA-256 integrity hash, signs it with the issuer's active RSA private key, stores document metadata, and returns a unique Document ID and verification QR code.
- **Verification Evidence:** Covered in `tests/issuerComponents.test.tsx`.

### Workflow B: QR-Code Document Verification
- **Actors:** General Public, Verifiers.
- **Path:** `/verify` -> Scan QR Tab.
- **Process:** User opens camera viewfinder (or uploads QR image). ZXing decodes the QR payload into a canonical JSON verification reference `{ "documentId": "...", "token": "...", "capability": "..." }`. The frontend validates the payload format and submits the capability token to `/api/public/verify/capability/{token}`.
- **Privacy Disclosure:** Minimal disclosure: confirms authenticity, validity period, and issuer identity without revealing private document contents.

### Workflow C: Direct Document Verification (Independent Modality)
- **Actors:** General Public, Verifiers.
- **Path:** `/verify` -> Upload Document Tab.
- **Process:** Verifier uploads a raw document file directly to `/api/public/verify/upload` without requiring a pre-printed QR code. The backend computes the file hash and matches it against registered document digests.
- **Critical Architectural Requirement:** Workflow C is completely distinct from Workflow B. The UI correctly keeps these as separate tabs and does not force QR scanning when direct document upload is selected.

### Workflow D: Zero-Knowledge Predicate Verification
- **Actors:** General Public, Relying Parties.
- **Path:** `/verify` -> ZKP Predicates Tab (or `/predicate-verification`).
- **Process:** Verifier submits a Bulletproofs zero-knowledge range proof payload generated by a holder (e.g., proving `age >= 21` without revealing the exact birthdate). The frontend posts the proof to `/api/v2/zkp/credential-predicates/verify`.
- **ZKP Reality:** Bulletproofs mathematical verification is implemented in backend module `zkdoc-zkp` using Weavechain Bulletproofs; the frontend correctly handles the proof representation.

### Workflow E: Credential Lifecycle & Trust Administration
- **Actors:** Holders, Issuers, Administrators.
- **Path:** `/credentials`, `/issuer`, `/administration`.
- **Process:** Issuance of W3C-compliant Verifiable Credentials, holder wallet acceptance, lifecycle transitions (Active -> Suspended -> Resumed -> Revoked), and administrator public key registration / rotation.

---

## 9. Frontend-to-Backend Integration & API Contracts

All API communications route through the centralized client in `src/api/client.ts` using endpoint definitions from `src/api/endpoints.ts`.

### 9.1 Endpoint Mapping Matrix

| Functional Group | REST Endpoint Path | HTTP Method | Auth Required? | Backend Controller |
| :--- | :--- | :---: | :---: | :--- |
| **Document Upload** | `/api/documents` | `POST` (Multipart) | Yes (`ISSUER`) | `DocumentController` |
| **Document Verify** | `/api/documents/{id}/verify` | `POST` | Yes (`VERIFIER`) | `DocumentController` |
| **Direct Upload Verify** | `/api/documents/verify-upload` | `POST` (Multipart) | Yes (`VERIFIER`) | `DocumentController` |
| **Document QR Code** | `/api/documents/{id}/qr` | `GET` | Yes (`ISSUER`) | `DocumentController` |
| **Issue Credential** | `/api/credentials/issue` | `POST` | Yes (`ISSUER`) | `CredentialController` |
| **Suspend Credential** | `/api/credentials/{id}/suspend` | `POST` | Yes (`ISSUER`) | `CredentialController` |
| **Resume Credential** | `/api/credentials/{id}/resume` | `POST` | Yes (`ISSUER`) | `CredentialController` |
| **Revoke Credential** | `/api/credentials/{id}/revoke` | `POST` | Yes (`ISSUER`) | `CredentialController` |
| **Public Token Verify** | `/api/public/verify/{token}` | `GET` | **No** (Public) | `PublicVerificationController` |
| **Public Capability Verify**| `/api/public/verify/capability/{token}` | `GET` | **No** (Public) | `PublicVerificationController` |
| **Public Upload Verify** | `/api/public/verify/upload` | `POST` (Multipart) | **No** (Public) | `PublicVerificationController` |
| **ZKP Predicate Verify** | `/api/v2/zkp/credential-predicates/verify` | `POST` | **No** (Public) | `CredentialPredicateController` |
| **Holder Credentials** | `/api/holder/credentials` | `GET` | Yes (`HOLDER`) | `HolderCredentialController` |
| **Holder Proof Generate**| `/api/holder/credentials/proofs/generate`| `POST` | Yes (`HOLDER`) | `HolderCredentialController` |
| **Admin Key Registry** | `/api/admin/keys` | `GET`, `POST` | Yes (`ADMIN`) | `KeyRegistryAdminController` |
| **Admin Rotate Key** | `/api/admin/keys/{keyId}/rotate` | `POST` | Yes (`ADMIN`) | `KeyRegistryAdminController` |
| **Audit Events Log** | `/api/admin/audit/events` | `GET` | Yes (`ADMIN`, `ISSUER`) | `AuditAdminController` |
| **Verify Audit Chain** | `/api/admin/audit/verify-integrity` | `POST` | Yes (`ADMIN`, `ISSUER`) | `AuditAdminController` |
| **Health Check** | `/actuator/health` | `GET` | **No** (Public) | Spring Boot Actuator |

### 9.2 Error Response Normalization
The API client normalizes Spring Boot Problem Details / RFC 7807 error responses into a typed `ApiError` interface:
```typescript
export interface ApiError {
  readonly message: string;
  readonly status: number;
  readonly code?: string;
  readonly details?: Record<string, unknown>;
  readonly timestamp: string;
}
```

---

## 10. Application Security Audit

| Security Vector | Implementation Mechanism | Audit Evaluation | Status |
| :--- | :--- | :--- | :---: |
| **Token Storage** | `sessionStorage` (`zkdoc_auth_session`) | Prevents token persistence across browser sessions; isolated to active tab. | **SECURE** |
| **Token Parsing** | Strict Base64URL parsing & RFC 8725 validation | Rejects `alg: "none"` and symmetric `HS*` keys; parses epoch expiration. | **SECURE** |
| **Open Redirects** | Strict regex validation in `LoginPage.tsx` | Validates `returnUrl` against internal relative paths `^\/[a-zA-Z0-9\-_./]*$`; rejects `javascript:` and protocol-relative `//` URLs. | **SECURE** |
| **Private Key Defense** | Adversarial regex check in `RegisterKeyModal.tsx` | Blocks strings matching `-----BEGIN (RSA )?PRIVATE KEY-----` from being submitted to public key registry. | **SECURE** |
| **XSS Sanitization** | React 19 automatic JSX text node escaping | No instances of `dangerouslySetInnerHTML` found in the entire codebase. | **SECURE** |
| **Route Protection** | `RequireAuth` and `RequireRole` components | Blocks unauthenticated users and redirects unauthorized roles to `/forbidden`. | **SECURE** |

---

## 11. Privacy & Zero-Knowledge Reality Check

A core requirement of this audit is distinguishing between true mathematical Zero-Knowledge Proofs (ZKP) and standard cryptographic verification:

1. **Standard Document Verification (Workflows A, B, C):**
   - Uses **SHA-256** cryptographic hash digests for document integrity.
   - Uses **RSA-2048/4096** digital signatures for non-repudiation and authenticity.
   - Uses **Capability Tokens** for controlled, minimal information disclosure.
   - *Conclusion:* This is standard public key cryptography, not zero-knowledge proofs.
2. **Predicate & Range Verification (Workflow D):**
   - Uses **Bulletproofs** (Weavechain Bulletproofs library on backend) for zero-knowledge range proofs over commitments.
   - Enables statements such as `age >= 21` or `income >= threshold` to be mathematically proven without revealing the plaintext attribute.
   - *Conclusion:* True Zero-Knowledge Proof architecture is active in Workflow D and correctly handled by `PredicateProofVerifyCard.tsx` and `PredicateProofGeneratorCard.tsx`.

---

## 12. Accessibility (a11y) Audit (WCAG 2.1 AA)

An automated and manual evaluation of components was conducted against W3C WCAG 2.1 AA guidelines:

| Accessibility Criterion | Current Implementation | Evaluation Finding | Priority |
| :--- | :--- | :--- | :---: |
| **Skip Navigation** | `<SkipLink />` present in `AppLayout.tsx`. | Properly bypasses header navigation to `#main-content`. | Compliant |
| **Modal Focus Management** | Modals render via standard React components. | Missing focus trapping inside modal dialogs (`ConfirmationModal.tsx`, `KeyPemModal.tsx`); tab key escapes modal back to underlying page. | **High** |
| **Focus Return** | Modals unmount when dismissed. | Focus does not programmatically return to the triggering button upon modal close. | **Medium** |
| **Form Labels & Errors** | Form inputs have visual `<label>` elements. | Several inputs lack explicit `id` / `htmlFor` pairings or `aria-describedby` links to error message spans. | **High** |
| **Color Contrast** | Dark slate color palette in `tokens.css`. | Tertiary text (`text-slate-400` on `bg-slate-900`) achieves ~4.8:1 (passes AA), but subtle border outlines (`border-slate-800`) are difficult to discern for low-vision users. | **Medium** |
| **Camera Viewfinder a11y** | `<video>` element in `QrScannerCard.tsx`. | Missing accessible textual alternative describing scanning state for screen readers. | **Medium** |

---

## 13. Usability & User Experience (UX) Review

1. **Information Density:** Feature dashboards (`AdminDashboardPage.tsx`, `AuditLogsPage.tsx`) display dense cryptographic information (PEM keys, hex digests, token strings). The `FormattedHash` component successfully truncates hashes (`0x3a4f...9b2c`) with one-click copy feedback.
2. **Upload Feedback:** Large file uploads currently present a static spinner without upload percentage progress indicators, creating uncertainty during uploads on slower connections.
3. **Dual Verification Clarity:** The tabs on `PublicVerificationPage.tsx` successfully differentiate QR camera scanning from raw document upload, preventing user confusion.

---

## 14. Performance & Bundle Analysis

Building production assets with Vite 8.3.1 identified the following performance profile:

### 14.1 Bundle Size Metrics
- **Total Bundle Size:** `1,038.55 kB` minified (`273.40 kB` gzipped).
- **CSS Bundle:** `69.25 kB` minified (`12.77 kB` gzipped).
- **Vite Warning:** Chunk size warning triggered (> 500 kB threshold).

### 14.2 Root Cause Analysis
All 10+ page routes and heavy third-party vendor libraries are statically imported in `src/app/App.tsx`:
- `@zxing/browser` (~180 kB minified) is loaded on the initial home page even if the user never uses the QR scanner.
- Lucide icon set (~110 kB) is bundled in the main chunk.
- Admin and Audit tables are bundled for public users who will never authenticate as administrators.

---

## 15. Defect & Vulnerability Register

| ID | Category | Severity | Title & Description | Remediation Plan (Phase Reference) |
| :--- | :--- | :---: | :--- | :--- |
| **FE-AUDIT-001** | Performance | **Medium** | **Monolithic Bundle (1.04 MB):** All routes statically imported in `App.tsx` without dynamic `React.lazy()` chunking. | Implement route-based lazy loading and manual vendor splitting in Phase 11. |
| **FE-AUDIT-002** | Accessibility | **Medium** | **Modal Focus Trapping:** Dialogs (`KeyPemModal`, `ConfirmationModal`) do not constrain keyboard focus within the dialog container. | Implement `aria-modal="true"` and keyboard trap hooks in Phase 4 component refactor. |
| **FE-AUDIT-003** | Accessibility | **Low** | **Form Error ARIA Links:** Validation errors in issuance and provision cards lack `aria-describedby` associations. | Standardize `FormField.tsx` to link input IDs to error message elements in Phase 4. |
| **FE-AUDIT-004** | Usability | **Low** | **Upload Progress Indication:** Drag-and-drop dropzones lack progress percentage bar for large file uploads up to 25 MB. | Implement XMLHttpRequest / Axios progress event stream in Phase 6. |
| **FE-AUDIT-005** | Testing / DX | **Low** | **Jsdom Concurrency Overhead:** Vitest concurrent jsdom instances create high CPU load on Windows, occasionally slowing long `userEvent.type()` inputs. | Configure `testTimeout: 10000` or optimize userEvent paste operations in test fixtures. |
| **FE-AUDIT-006** | Usability | **Low** | **Camera Permission Guidance:** When camera permissions are denied, `QrScannerCard` shows a generic error instead of browser-specific permission recovery instructions. | Add explicit camera permission troubleshooting prompts in Phase 5. |
| **FE-AUDIT-007** | Design System | **Low** | **Inconsistent Color Utility Coupling:** Several components use hardcoded Tailwind slate colors instead of semantic CSS variables from `tokens.css`. | Consolidate all color tokens in Phase 3 design system normalization. |
| **FE-AUDIT-008** | Security | **Info** | **Bearer Token Masking:** The manual token input in `LoginPage` supports password masking, but default state is unmasked. | Default token textarea to masked state with toggle button in Phase 10. |

---

## 16. Recommendations for Phase 2: UX Research & Journey Planning

Based on the verified audit baseline, the following directives are recommended for Phase 2:
1. **Preserve Frozen Contracts:** Do not alter REST endpoints, request payload schemas, or session storage contracts established in `endpoints.ts` and `authStore.ts`.
2. **Prioritize Separation of Verification Paths:** Ensure the UX architecture maintains distinct, intuitive entry points for QR verification vs Direct Document Verification.
3. **Design Progressive Disclosure for Cryptography:** Formulate user journey wireframes that conceal raw cryptographic complexities (PEM keys, Merkle hashes) behind intuitive status badges, with expandable "Technical Details" panels for auditors.
4. **Prepare Component Splitting:** Structure page mockups to support atomic lazy-loading boundaries for Phase 4 and Phase 11 implementation.
