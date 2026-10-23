# Phase 3 — Frontend Architecture Specification
## Zero-Knowledge Document Verification Platform

---

## 1. Executive Summary & Mission

### 1.1 Scope and Objective
This document defines the authoritative frontend software architecture for the **Zero-Knowledge Document Verification Platform**, fulfilling the requirements of **Phase 3: Frontend Architecture & Design System**.

Following the technical audit in Phase 1 ([`FRONTEND_PHASE_1_AUDIT_REPORT.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/frontend-audit/FRONTEND_PHASE_1_AUDIT_REPORT.md)) and the user experience strategy in Phase 2 ([`FRONTEND_PHASE_2_UX_STRATEGY.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/frontend-ux/FRONTEND_PHASE_2_UX_STRATEGY.md)), this architecture establishes a clean, decoupled, accessible, and type-safe foundation. It provides the core design tokens and reusable component primitives needed for subsequent feature development (Phases 4 through 18) without introducing unnecessary framework abstractions.

### 1.2 Core Architectural Tenets
1. **Separation of Presentation and Business Logic:** Visual components (`components/common`, `components/feedback`, `components/data`) are strictly presentational. They receive typed data via props and emit user interactions via callbacks, remaining completely decoupled from HTTP clients, browser storage, or backend cryptographic implementations.
2. **Autonomous Verification Paths:** The architecture enforces strict independence between **QR-based verification** (Workflow B) and **Direct document file verification** (Workflow C). Neither workflow imports or depends on the other.
3. **Progressive Disclosure of Cryptography:** Complex mathematical constructs (SHA-256 digests, RSA public key fingerprints, Merkle tree roots, Bulletproofs range commitments) are rendered using standardized presentation primitives ([`FormattedHash`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/data/FormattedHash.tsx), [`VerificationStatusCard`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/verification/VerificationStatusCard.tsx)) that display human-readable status indicators by default with opt-in expandable technical drawers.
4. **Zero-Trust Client Session Hygiene:** Authentication tokens are stored exclusively in `sessionStorage` (`zkdoc_auth_session`), isolating sessions to the active browser tab. Decoded JWT claims adhere strictly to RFC 8725, rejecting unsigned (`alg: "none"`) or symmetric (`alg: "HS*"`) tokens.

---

## 2. Incorporation of Prior Phase Requirements

### 2.1 From Phase 1 (Audit & Baseline)
- **Runtime Alignment:** Formalized runtime environment on **React 19.3.0**, **TypeScript 6.0.3**, **Vite 8.3.1**, and **Tailwind CSS 4.3.3**.
- **Static Asset Target:** Build configuration in [`vite.config.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/vite.config.ts) routes production assets directly to `../zkdoc-api/src/main/resources/static`, enabling single-container Spring Boot deployment.
- **Code-Splitting Readiness:** The architectural boundary structure isolates heavy vendor libraries (`@zxing/browser`, Lucide icons) so that dynamic imports (`React.lazy()`) can be introduced in Phase 11 without refactoring component contracts.

### 2.2 From Phase 2 (UX Strategy & Journeys)
- **Dual-Mode Semantic Palette:** Design tokens defined in [`tokens.css`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/styles/tokens.css) map cleanly between light surface foundations and high-contrast dark slate surfaces (`--surface-canvas`, `--surface-card`, `--status-verified`, `--status-tampered`).
- **Accessible Outcome Signaling:** Status components ([`Badge`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/common/Badge.tsx), [`Alert`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/feedback/Alert.tsx), [`VerificationStatusCard`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/verification/VerificationStatusCard.tsx)) enforce multi-sensory feedback by combining explicit semantic icons, text labels, and `role="alert"` / `role="status"` screen reader attributes.
- **Form Error Association:** Form controls link inputs to error message spans via `aria-describedby="{id}-error"` and set `aria-invalid="true"`.

---

## 3. Directory Layout & Organizational Structure

The frontend architecture follows a domain-driven, modular hierarchy in `frontend/src`:

```
frontend/src/
├── main.tsx                         # Client DOM mount & React 19 root
├── vite-env.d.ts                    # Vite client types & environment declarations
│
├── api/                             # HTTP Client & REST Contracts
│   ├── client.ts                    # Fetch wrapper with Bearer token injection & error normalization
│   ├── endpoints.ts                 # Authoritative backend REST endpoints
│   ├── errors.ts                    # Normalized ApiError type definition
│   ├── types.ts                     # Strongly-typed backend request & response DTOs
│   ├── issuerApi.ts                 # Document upload & credential issuance services
│   ├── holderApi.ts                 # Holder wallet & range proof API calls
│   ├── verifierApi.ts               # Public QR, direct upload, and ZKP verification services
│   └── adminApi.ts                  # Trust key registry & audit chain verification services
│
├── app/                             # Application Shell & Routing Bootstrap
│   ├── App.tsx                      # Top-level routing declaration & route guards
│   └── ErrorBoundary.tsx            # Global React error boundary with safe recovery UI
│
├── components/                      # Reusable Design System Primitives
│   ├── auth/                        # Route protection guards (RequireAuth, RequireRole)
│   ├── common/                      # Atomic inputs & surfaces (Button, Input, TextArea, Select, Checkbox, Card, Badge)
│   ├── data/                        # Data visualization (Table, Pagination, FormattedHash, CopyToClipboard, KeyValueList)
│   ├── feedback/                    # Notification & overlay primitives (Dialog, ConfirmationModal, Alert, Toast, Spinner)
│   ├── layout/                      # Shared layout chrome (AppLayout, Header, Footer, Breadcrumbs, PageHeader, SkipLink)
│   └── verification/                # Domain-specific verification cards (VerificationStatusCard, VerificationBadge)
│
├── features/                        # Domain Feature Cards (Self-contained business modules)
│   ├── admin/                       # Key registry management, rotation modals, recovery overview
│   ├── audit/                       # Merkle hash chain verification, audit event tables
│   ├── holder/                      # Holder wallet cards, status modals, ZKP range proof generator
│   ├── issuer/                      # Document upload dropzone, credential issuance wizard, lifecycle controls
│   └── verifier/                    # QR camera viewfinder, direct upload verification, manual reference entry
│
├── lib/                             # Framework-Independent Pure Utilities
│   └── utils/
│       ├── cn.ts                    # Tailwind CSS class merging utility (clsx / twMerge)
│       ├── fileValidation.ts        # Client-side 25 MB boundary and MIME type checking
│       ├── qrPayload.ts             # Canonical JSON verification payload parser & validator
│       └── security.ts              # Adversarial regex guards (private key interception, URL sanitization)
│
├── routes/                          # Page Composition Views (Route targets)
│   ├── HomePage.tsx                 # Public landing & feature introduction
│   ├── LoginPage.tsx                # Operator Bearer JWT login with live claims preview
│   ├── PublicVerificationPage.tsx   # Multi-tab public verification hub (QR, Upload, Token, ZKP)
│   ├── HolderDashboardPage.tsx      # Holder credential wallet workspace
│   ├── IssuerDashboardPage.tsx      # Institutional document registration & issuance workspace
│   ├── AuditLogsPage.tsx            # Cryptographic audit log explorer
│   ├── AdminDashboardPage.tsx       # Platform trust console & key registry
│   ├── ForbiddenPage.tsx            # Access denied recovery screen
│   └── NotFoundPage.tsx             # 404 fallback page
│
├── state/                           # Framework-Agnostic Reactive Observable Stores
│   ├── authStore.ts                 # JWT session state, RFC 8725 validation, sessionStorage persistence
│   ├── issuerStore.ts               # Document upload state, active issuance wizard state
│   ├── holderStore.ts               # Holder wallet credentials list, proof generation state
│   ├── verifierStore.ts             # Public verification results, camera state, verification history
│   └── adminStore.ts                # Key registry items, system recovery metrics
│
└── styles/                          # Styling & Design Tokens
    ├── tokens.css                   # Authoritative CSS custom property tokens (light/dark)
    └── index.css                    # Tailwind CSS 4 imports, base typography, reduced-motion overrides
```

---

## 4. Layer Responsibilities & Architectural Boundaries

| Layer | Primary Responsibilities | Permitted Dependencies | Forbidden Dependencies |
| :--- | :--- | :--- | :--- |
| **Styles & Tokens** (`src/styles/`) | Define raw CSS variables, color palettes, spacing, elevation shadows, focus indicators, and reduced-motion rules. | CSS specifications only. | No JavaScript or TypeScript imports. |
| **UI Primitives** (`src/components/common/`, `feedback/`, `data/`) | Render reusable visual elements. Handle keyboard events, focus trapping, and ARIA roles. | React, `src/lib/utils/cn.ts`, Lucide icons. | Must NOT import `src/api/*`, `src/state/*`, or backend DTOs. |
| **Domain Utilities** (`src/lib/utils/`) | Pure functions: file bounds checking, regex validation, base64url decoding, sanitization. | Standard Web APIs (`File`, `TextDecoder`, `atob`). | Must NOT contain JSX, React hooks, or UI state. |
| **API Client** (`src/api/`) | Execute HTTP requests, attach Bearer tokens, normalize error responses (`ApiError`). | Standard `fetch`, `src/api/types.ts`. | Must NOT import React components or mutate UI state directly. |
| **Reactive Stores** (`src/state/`) | Manage application domain state using vanilla pub/sub listeners. Handle `sessionStorage` persistence. | `src/api/*`, TypeScript types. | Must NOT render DOM or import UI components. |
| **Feature Cards** (`src/features/*`) | Compose UI primitives with reactive stores and API clients to deliver cohesive user workflows. | `components/*`, `state/*`, `api/*`, `lib/*`. | Must NOT define root routing or global layout chrome. |
| **Routes & App** (`src/routes/`, `src/app/`) | Declare URL paths, enforce route protection (`RequireAuth`), handle global error boundaries. | `features/*`, `components/layout/*`, React Router. | Feature implementation logic must remain in feature cards. |

---

## 5. State Management & Lifecycle Architecture

The application purposefully avoids heavy third-party state managers (Redux, Zustand, MobX) in favor of lightweight, vanilla TypeScript observable stores located in `src/state/`:

```
                 ┌────────────────────────────────┐
                 │       authStore (Session)      │
                 │   - Persists in sessionStorage │
                 │   - RFC 8725 JWT Parsing       │
                 └──────────────┬─────────────────┘
                                │
               Dispatches 'zkdoc:auth:cleared'
                                │
    ┌───────────────────────────┼───────────────────────────┐
    ▼                           ▼                           ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│   issuerStore    │  │   holderStore    │  │  verifierStore   │
│ - Upload state   │  │ - Wallet records │  │ - Active stream  │
│ - Issuance draft │  │ - Range proofs   │  │ - History (5 max)│
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

### 5.1 Multi-Store Teardown Pattern
When an operator logs out or a session expires:
1. `authStore.clearToken()` removes `zkdoc_auth_session` from `sessionStorage`.
2. A custom DOM event `zkdoc:auth:cleared` is dispatched on `window`.
3. `issuerStore`, `holderStore`, and `adminStore` listen for this event and immediately purge all cached in-memory credentials, document IDs, and key records.
4. Prevents sensitive data leakage across shared operator workstations.

---

## 6. TypeScript Conventions & Quality Standards

1. **Strict Type Safety:** `tsconfig.json` enforces `noImplicitAny: true`, `strictNullChecks: true`, and `isolatedModules: true`.
2. **Explicit Props Interfaces:** Every component exports its props interface (e.g., `ButtonProps`, `FormFieldProps`, `DialogProps`).
3. **No `any` or Unsafe Assertions:** Component props use discriminated unions for state modeling (e.g. `variant: 'primary' | 'secondary' | 'outline' | 'destructive' | 'ghost'`).
4. **Backend DTO Decoupling:** API response types (`DocumentResponse`, `CapabilityVerificationResponse`) are maintained in `src/api/types.ts` and mapped cleanly into component view models.

---

## 7. Security & Data Protection Architecture

1. **Storage Containment:** Persistent storage (`localStorage`, IndexedDB) is strictly prohibited for sensitive data. Only ephemeral session tokens reside in `sessionStorage`.
2. **XSS Immunity:** UI components render data via native JSX text node interpolation. Zero instances of `dangerouslySetInnerHTML` exist in the entire codebase.
3. **Adversarial Private Key Protection:** Admin key registration modal incorporates client-side regex blocking `-----BEGIN (RSA )?PRIVATE KEY-----`, preventing accidental leakage of institutional private signing keys.
4. **Open Redirect Mitigation:** The authentication redirection mechanism in `LoginPage.tsx` sanitizes `returnUrl` parameters against an allowlist regex (`^\/[a-zA-Z0-9\-_./]*$`), strictly rejecting external URLs (e.g. `//attacker.com`) and pseudo-protocols (`javascript:`).

---

## 8. Architectural Recommendations for Subsequent Phases

- **Phase 4 (Application Shell & Navigation):** Build the top-level header, mobile drawer navigation, breadcrumbs, and footer, binding them to `authStore` to reflect active operator sessions dynamically.
- **Phase 5 (Public Verification Hub):** Refactor `PublicVerificationPage` to lazily load `@zxing/browser` only when Tab 1 (QR Scanner) is activated.
- **Phase 6 (Issuer Workspace):** Integrate determinate upload progress bars into `DocumentUploadCard` for large files approaching 25 MB.
- **Phase 11 (Bundle Optimization):** Wrap all routes in `App.tsx` with `React.lazy()` and Suspense fallback skeletons, reducing initial bundle size from 1.04 MB to under 250 kB.
