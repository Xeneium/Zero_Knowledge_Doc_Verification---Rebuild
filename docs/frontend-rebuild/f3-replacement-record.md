# Phase F3: Replacement Record & Decommissioning Audit

**Project:** Zero-Knowledge Document Verification Platform  
**Document ID:** ZKDOC-FE-F3-002  
**Status:** Certified Replacement Record  
**Execution Date:** 2026-10-14  
**Target Milestone:** Frontend Phase F3 Complete — Ready for Phase F4  

---

## 1. Executive Summary

This record tracks the exact file-by-file disposition of the legacy frontend implementation during Phase F3.

In compliance with the F1 Safe Replacement Map and F2 Architecture Blueprint, all 74 legacy frontend files across `frontend/src/` and `frontend/tests/` were safely quarantined into `frontend/legacy-reference/`. A completely clean, type-safe foundation was established in `frontend/src/` with 0 lint errors, 0 type errors, 16 passing unit tests, and 9 passing backend integration tests.

---

## 2. Exhaustive Disposition of Legacy Frontend Files

### 2.1 Quarantined to `frontend/legacy-reference/` (Retained as Reference)

The following 74 files were removed from active compilation and quarantined to `frontend/legacy-reference/`:

#### A. Source Code (`frontend/legacy-reference/src/`)
1. `src/main.tsx` — Legacy bootstrap with unmanaged render errors.
2. `src/vite-env.d.ts` — Legacy untyped env.
3. `src/app/App.tsx` — Monolithic legacy shell with unauthenticated mock token state.
4. `src/app/ErrorBoundary.tsx` — Basic error boundary without accessible live announcement.
5. `src/app/LazyPages.tsx` — Untyped dynamic imports.
6. `src/app/navigation.ts` — Legacy navigation constants.
7. `src/app/providers.tsx` — Untyped React context provider wrapper.
8. `src/app/router.tsx` — Unvalidated route declarations.
9. `src/components/feedback/EmptyState.tsx` — Unused empty state.
10. `src/components/feedback/ErrorState.tsx` — Untyped error display.
11. `src/components/feedback/RouteLoading.tsx` — Spinner loading indicator.
12. `src/components/layout/AppHeader.tsx` — Inaccessible header without skip link.
13. `src/components/layout/AppShell.tsx` — Layout container with missing ARIA landmarks.
14. `src/components/layout/BrandMark.tsx` — SVG logo atom.
15. `src/components/layout/PageHeader.tsx` — Page header atom.
16. `src/components/layout/Sidebar.tsx` — Mobile navigation drawer.
17. `src/components/navigation/Breadcrumbs.tsx` — Untyped breadcrumbs.
18. `src/components/ui/Alert.tsx` — Legacy alert component.
19. `src/components/ui/Badge.tsx` — Legacy badge with insufficient contrast.
20. `src/components/ui/Button.tsx` — Legacy button with 36px touch targets.
21. `src/components/ui/Card.tsx` — Legacy card component.
22. `src/components/ui/ConfirmationDialog.tsx` — Unaccessible modal.
23. `src/components/ui/Dialog.tsx` — Custom modal missing focus trapping.
24. `src/components/ui/DropdownMenu.tsx` — Custom dropdown missing keyboard navigation.
25. `src/components/ui/Field.tsx` — Form field missing `aria-describedby` error binding.
26. `src/components/ui/IconButton.tsx` — Small 32px icon button.
27. `src/components/ui/Skeleton.tsx` — Simple pulse box.
28. `src/components/ui/Spinner.tsx` — Raw SVG spinner.
29. `src/components/ui/Tabs.tsx` — Non-semantic tab implementation.
30. `src/components/ui/Toast.tsx` — Untyped toast component.
31. `src/components/ui/Tooltip.tsx` — Non-accessible tooltip.
32. `src/config/env.ts` — Legacy environment loader.
33. `src/features/administration/api/adminApi.ts` — Partial admin endpoints (missing recovery reconcile).
34. `src/features/administration/pages/PlatformOperationsPage.tsx` — Incomplete operations view.
35. `src/features/audit/pages/AuditLogsPage.tsx` — Audit view without pagination controls.
36. `src/features/auth/AuthContext.tsx` — **Critical Flaw:** Injected fake mock tokens (`demo-signature-for-development-eval`).
37. `src/features/auth/SessionModal.tsx` — Insecure mock token generator modal.
38. `src/features/credentials/api/credentialsApi.ts` — Untyped holder credentials API.
39. `src/features/credentials/pages/CredentialsPage.tsx` — Holder view without proof generation nonces.
40. `src/features/dashboard/BackendStatus.tsx` — Actuator probe viewer.
41. `src/features/dashboard/OverviewPage.tsx` — Legacy overview page.
42. `src/features/verification/api/authoritativeVerificationApi.ts` — Redundant verification wrapper.
43. `src/features/verification/api/verificationApi.ts` — Verification API missing capability support.
44. `src/features/verification/components/PresentationDemoCard.tsx` — Fake presentation demo.
45. `src/features/verification/components/QrScanner.tsx` — Inflexible camera scanner.
46. `src/features/verification/components/VerificationResultPanel.tsx` — Result panel with false-positive bug.
47. `src/features/verification/demo/presentationDemo.ts` — Hardcoded mock data.
48. `src/features/verification/pages/DocumentVerificationPage.tsx` — Document verification view.
49. `src/features/verification/pages/PredicateVerificationPage.tsx` — **Critical Flaw:** Rendered green "Mathematically Valid" on proof rejection.
50. `src/features/verification/pages/PublicVerificationPage.tsx` — Verification page with broken QR regex.
51. `src/features/verification/scanner/startQrScanner.ts` — Scanner starter function.
52. `src/features/verification/types/verification.types.ts` — Incomplete domain types.
53. `src/features/verification/validation/verificationReference.ts` — **Bug:** Regex failed on canonical JSON QR format.
54. `src/hooks/useBackendHealth.ts` — Health polling hook.
55. `src/lib/api/api-error.ts` — Legacy error class.
56. `src/lib/api/client.ts` — Legacy client looking for `payload.code`.
57. `src/lib/api/health.ts` — Health API call.
58. `src/lib/utils/cn.ts` — Legacy class name helper.
59. `src/routes/FeaturePlaceholderPage.tsx` — Generic placeholder.
60. `src/routes/NotFoundPage.tsx` — Basic 404 page.
61. `src/styles/globals.css` — Outdated global CSS.
62. `src/styles/tokens.css` — Incomplete color tokens.

#### B. Legacy Tests (`frontend/legacy-reference/tests/`)
63. `tests/api-client.test.ts`
64. `tests/App.test.tsx`
65. `tests/AuditLogsPage.test.tsx`
66. `tests/auth-context.test.tsx`
67. `tests/CredentialsPage.test.tsx`
68. `tests/ErrorBoundary.test.tsx`
69. `tests/PlatformOperationsPage.test.tsx`
70. `tests/PublicVerificationPage.test.tsx`
71. `tests/QrScanner.test.tsx`
72. `tests/setup.ts`
73. `tests/verification-reference.test.ts`
74. `tests/VerificationPages.test.tsx`

---

## 3. Files Replaced and Reconfigured

| Path | Action Taken | Rationale & Outcome |
| :--- | :--- | :--- |
| `frontend/tsconfig.json` | Updated exclude list | Added `"legacy-reference"` to `"exclude"` array so TypeScript ignores quarantined files. |
| `frontend/eslint.config.js` | Updated ignores list | Added `'legacy-reference'` and `'legacy-reference/**'` to `ignores` array. |
| `frontend/.gitignore` | Updated ignore rules | Added `legacy-reference/` to prevent tracking quarantined code. |
| `frontend/vite.config.ts` | Updated Vitest config | Configured `include: ['tests/**/*.{test,spec}.{ts,tsx}']` and `exclude: ['legacy-reference/**']`. |

---

## 4. New Files Introduced in Phase F3

| Path | Purpose & Responsibility | Testing & Verification |
| :--- | :--- | :--- |
| `src/main.tsx` | Clean application entry point mounting `#root` with `React.StrictMode`. | Verified by production build |
| `src/vite-env.d.ts` | Typed `ImportMetaEnv` definitions. | Verified by `npm run typecheck` |
| `src/config/env.ts` | Type-safe environment configuration with production/development booleans. | Verified by `npm run typecheck` |
| `src/styles/tokens.css` | Semantic CSS custom properties for canvas, surface, brand, success, warning, danger. | Verified by visual review & build |
| `src/styles/index.css` | Tailwind CSS v4 entry with reduced-motion media query and focus ring rules. | Verified by build |
| `src/lib/utils/cn.ts` | Zero-dependency class name concatenation utility. | Verified by unit tests |
| `src/api/types.ts` | Authoritative backend DTO interfaces strictly matching Spring Boot Java models. | Verified by `npm run typecheck` |
| `src/api/endpoints.ts` | Centralized REST endpoint URI constants. | Verified by unit tests |
| `src/api/errors.ts` | `ApiError` class with correlation ID, validation errors, and status getters. | Verified by `tests/client.test.ts` |
| `src/api/client.ts` | Typed `apiRequest` client with correlation ID tracing and 401 interception. | Verified by `tests/client.test.ts` |
| `src/state/authStore.ts` | In-memory auth store with `sessionStorage` fallback and JWT claim decoder. | Verified by `tests/client.test.ts` |
| `src/components/common/Button.tsx` | Accessible button with 44px touch targets, loading spinner, and focus rings. | Verified by `tests/components.test.tsx` |
| `src/components/common/Badge.tsx` | High-contrast status badge with semantic variants. | Verified by `tests/components.test.tsx` |
| `src/components/common/Card.tsx` | Structured card container with Title, Content, and Footer. | Verified by `tests/components.test.tsx` |
| `src/components/common/Alert.tsx` | Accessible alert banner with `role="alert"` / `role="status"`. | Verified by `tests/components.test.tsx` |
| `src/components/common/Skeleton.tsx` | Accessible loading placeholder with `role="presentation"`. | Verified by `tests/components.test.tsx` |
| `src/components/layout/SkipLink.tsx` | Accessible keyboard bypass link targeting `#main-content`. | Verified by `tests/App.test.tsx` |
| `src/components/layout/Header.tsx` | Responsive header with brand identity, active route links, and session badge. | Verified by `tests/App.test.tsx` |
| `src/components/layout/Footer.tsx` | Semantic footer with platform status and cryptographic notice. | Verified by `tests/App.test.tsx` |
| `src/components/layout/AppLayout.tsx` | Root shell composing SkipLink, Header, Main, and Footer. | Verified by `tests/App.test.tsx` |
| `src/routes/HomePage.tsx` | Overview page presenting platform pillars and navigation CTAs. | Verified by `tests/App.test.tsx` |
| `src/routes/NotFoundPage.tsx` | Accessible 404 view with return link. | Verified by `tests/App.test.tsx` |
| `src/routes/PlaceholderPages.tsx` | Explicit non-misleading placeholders for future feature routes. | Verified by `tests/App.test.tsx` |
| `src/app/ErrorBoundary.tsx` | Accessible error boundary with reload button. | Verified by unit tests |
| `src/app/App.tsx` | Root component with BrowserRouter and complete route tree. | Verified by `tests/App.test.tsx` |
| `tests/setup.ts` | Vitest setup with `@testing-library/jest-dom/vitest` and `matchMedia` mock. | Verified by `npm run test:run` |
| `tests/client.test.ts` | Unit tests for API client, correlation IDs, Bearer tokens, and error mapping. | Passed (4/4) |
| `tests/components.test.tsx` | Unit tests for Button, Badge, Card, Alert, and Skeleton. | Passed (7/7) |
| `tests/App.test.tsx` | Integration tests for App root, header navigation, routes, and 404 fallback. | Passed (5/5) |

---

## 5. Backend & Shared Files Preserved Intact

The following critical files were verified before and after Phase F3 and remain 100% untouched:
- `zkdoc-api/pom.xml` & root `pom.xml`
- `zkdoc-api/src/main/java/com/zkdoc/api/controller/SpaForwardController.java`
- `zkdoc-api/src/test/java/com/zkdoc/api/FrontendIntegrationTest.java`
- All Java source files across `zkdoc-crypto`, `zkdoc-zkp`, `zkdoc-keys`, `zkdoc-core`, `zkdoc-api`
- Flyway database migration scripts (`V1__...` through `V13__...`)
- `Dockerfile` & `docker-compose.yml`

---

## 6. Deviations from F1/F2 Plan

- **Zero Deviations:** The quarantine structure, directory layout, technology toolchain, and build target executed exactly in accordance with the Phase F1 Safe Replacement Map and Phase F2 Architecture Specification.
