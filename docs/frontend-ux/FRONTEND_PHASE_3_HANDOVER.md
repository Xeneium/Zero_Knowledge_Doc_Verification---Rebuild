# Phase 3 Handover Blueprint — Design System & Architecture
## Zero-Knowledge Document Verification Platform

---

## 1. Executive Handover Summary

This document establishes the authoritative bridge transitioning the project from **Phase 2: UX Research & User Journey Planning** into **Phase 3: Frontend Architecture & Design System**.

Phase 3 is tasked with creating the systemic foundational design tokens, normalized styling architecture, component contracts, and lazy-loading boundaries required to execute subsequent UI feature phases (Phases 4 through 18) without architectural ambiguity.

---

## 2. Agreed UX Principles to Code Against

1. **Clarity Before Cryptography:** Human-readable verification outcomes ("Authentic & Verified", "Integrity Compromised") take visual precedence. Cryptographic technical parameters (SHA-256 hashes, PEM keys, Merkle proofs) reside exclusively inside progressive disclosure accordions.
2. **Autonomous Verification Channels:** The design system must provide distinct, uncoupled component variants for QR-code camera verification (Workflow B) and Direct document upload verification (Workflow C).
3. **Multi-Sensory Outcome Signaling:** Verification and alert components must never rely on color alone. Every status card requires an explicit semantic icon, high-contrast text label, and screen-reader status announcement (`aria-live="polite"`).
4. **Forgiving Interactions:** Form and dropzone components must preserve user file selections and text inputs when recoverable errors occur.

---

## 3. Confirmed Page Inventory & Route Boundaries

The design system in Phase 3 must provide layouts and component wrappers tailored to the following route structure:

```
src/routes/
├── HomePage.tsx                     # Landing presentation & capability overview
├── LoginPage.tsx                    # Bearer token session authentication
├── PublicVerificationPage.tsx       # Multi-tab verification hub (QR, Upload, Token, ZKP)
├── HolderDashboardPage.tsx          # Credential wallet & range proof generation
├── IssuerDashboardPage.tsx          # Document dropzone & issuance wizard
├── AuditLogsPage.tsx                # High-density Merkle audit trail explorer
├── AdminDashboardPage.tsx           # Trust root key registry & rotation console
├── ForbiddenPage.tsx                # Role boundary violation screen
└── NotFoundPage.tsx                 # 404 recovery view
```

### 3.1 Code-Splitting Architecture Requirement for Phase 3
To remediate `FE-AUDIT-001` (1.04 MB monolithic bundle), Phase 3 must establish dynamic code-splitting wrappers in `src/app/App.tsx`:
- Heavy libraries (`@zxing/browser` in QR scanner) must load dynamically on-demand.
- Role-specific dashboard routes (`/issuer`, `/holder`, `/administration`, `/audit-logs`) must load via `React.lazy()` with shared accessible `<Suspense>` fallback skeletons.

---

## 4. Confirmed API Endpoints & Request/Response Models

Phase 3 mock providers, component props, and API clients will bind strictly to these confirmed backend endpoints:

| Functional Area | REST Endpoint Path | HTTP Method | Expected Payload / Response Model |
| :--- | :--- | :---: | :--- |
| **Document Upload** | `/api/documents` | `POST` | `multipart/form-data; file=<blob>` -> `DocumentResponse` |
| **Direct Document Verify** | `/api/documents/verify-upload` | `POST` | `multipart/form-data; file=<blob>` -> `DocumentVerificationResponse` |
| **Public Capability Verify** | `/api/public/verify/capability/{token}` | `GET` | Response: `CapabilityVerificationResponse` |
| **Public Upload Verify** | `/api/public/verify/upload` | `POST` | `multipart/form-data; file=<blob>` -> `PublicVerificationResponse` |
| **ZKP Predicate Verify** | `/api/v2/zkp/credential-predicates/verify` | `POST` | `PredicateVerificationRequest` -> `PredicateVerificationResponse` |
| **Holder Credentials** | `/api/holder/credentials` | `GET` | Array: `HolderCredentialItem[]` |
| **Admin Key Registry** | `/api/admin/keys` | `GET`, `POST` | `KeyRegistrationRequest` -> `KeyRegistryItem[]` |
| **Audit Chain Verify** | `/api/admin/audit/verify-integrity` | `POST` | Response: `AuditChainVerificationResponse` |

---

## 5. Design Token System Specifications for Phase 3

Phase 3 will refactor [`frontend/src/styles/tokens.css`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/styles/tokens.css) into a semantic CSS custom property architecture:

### 5.1 Semantic Color Architecture
Tokens must be defined using CSS variables that map cleanly across dark and light themes:

```css
:root {
  /* Brand Foundations */
  --color-brand-primary: #0284c7;     /* Sky 600 */
  --color-brand-accent: #06b6d4;      /* Cyan 500 */
  
  /* Semantic Surfaces */
  --surface-canvas: #090d16;          /* Deep slate canvas */
  --surface-card: #0f172a;            /* Slate 900 card */
  --surface-card-hover: #1e293b;      /* Slate 800 hover */
  --surface-elevated: #1e293b;        /* Modals & Dropdowns */
  
  /* Text & Typography */
  --text-primary: #f8fafc;            /* Slate 50 (>= 7:1 contrast) */
  --text-secondary: #94a3b8;          /* Slate 400 (>= 4.5:1 contrast) */
  --text-muted: #64748b;              /* Slate 500 */
  
  /* Verification Statuses (WCAG AA Compliant) */
  --status-verified: #10b981;         /* Emerald 500 */
  --status-verified-bg: #064e3b33;    /* Emerald 900 / 20% */
  --status-tampered: #ef4444;         /* Red 500 */
  --status-tampered-bg: #7f1d1d33;    /* Red 900 / 20% */
  --status-warning: #f59e0b;          /* Amber 500 */
  --status-warning-bg: #78350f33;     /* Amber 900 / 20% */
  --status-info: #0284c7;             /* Sky 600 */
  
  /* Interactive Boundaries */
  --border-subtle: #1e293b;           /* Slate 800 */
  --border-focus: #38bdf8;            /* Sky 400 (High-visibility focus ring) */
  --ring-focus-width: 2px;
  --ring-focus-offset: 2px;
}
```

### 5.2 Typography Scale
- **Display Heading:** `text-3xl font-bold tracking-tight` (30px / 36px line-height)
- **Section Heading:** `text-xl font-semibold` (20px / 28px line-height)
- **Card Title:** `text-base font-medium` (16px / 24px line-height)
- **Body Text:** `text-sm font-normal text-slate-300` (14px / 20px line-height)
- **Monospace Cryptographic Data:** `font-mono text-xs tracking-wider text-slate-400`

---

## 6. Core Component Library Roadmap (For Phase 4)

Phase 3 will prepare component interface contracts (`.d.ts` / props interfaces) for the component library to be finalized in Phase 4:

1. **Button Primitive:** Variants: `primary`, `secondary`, `outline`, `danger`, `ghost`. States: `idle`, `loading` (with spinner), `disabled`. Supports `leftIcon`, `rightIcon`.
2. **Document Dropzone:** Multi-state drag-and-drop file target with active border highlight, file metadata preview, removal button, progress percentage bar, and 25 MB boundary guard.
3. **Verification Status Banner:** Polymorphic banner supporting `verified`, `tampered`, `revoked`, `expired`, and `offline` with non-color icons and accessible live region announcements.
4. **Accessible Modal Dialog:** Focus-trapping modal with backdrop blur, keyboard `Esc` handler, `aria-modal="true"`, and automatic focus restoration upon dismiss.
5. **Formatted Hash:** Truncated hexadecimal string display with one-click clipboard copy feedback and tooltip displaying full digest.

---

## 7. Explicit Exclusions from Phase 3

To prevent scope creep and preserve engineering discipline:
- **Do NOT implement feature cards:** `DocumentUploadCard.tsx`, `QrScannerCard.tsx`, and `KeyRegistryCard.tsx` belong to domain-specific feature phases (Phases 5 through 9).
- **Do NOT alter backend endpoints or schemas:** All Spring Boot Java controllers remain untouched.
- **Do NOT rewrite state management stores:** `authStore.ts`, `issuerStore.ts`, and `verifierStore.ts` are verified and will be retained.

---

## 8. Checkpoint for Phase 3 Authorization

Phase 3 may proceed immediately upon user confirmation of the visual theme recommendation:
- **Default Recommendation:** Adopt the **Modern Dual-Mode Enterprise Architecture** defaulting to Dark Slate (`slate-950`/`slate-900`) with high-visibility Emerald/Cyan verification cues, structured through semantic CSS variables in `tokens.css`.
