# Phase 4 Handover Blueprint — Application Shell & Navigation
## Zero-Knowledge Document Verification Platform

---

## 1. Executive Handover Summary

This document serves as the formal architectural transition from **Phase 3: Frontend Architecture & Design System** to **Phase 4: Application Shell & Navigation**.

Phase 3 established the design tokens, semantic color architecture, responsive spacing scales, and foundational component primitives. Phase 4 will compose these primitives into a unified, accessible, and responsive application shell comprising the public navigation header, dynamic authenticated workspace links, mobile navigation drawer, breadcrumbs, and footer.

---

## 2. Reusable Design System Assets Ready for Phase 4

### 2.1 Design Tokens (`frontend/src/styles/tokens.css`)
Phase 4 can immediately bind layout elements to the established CSS variables:
- **Surfaces:** `var(--surface-canvas)` (page backdrop), `var(--surface-card)` (header and drawer backgrounds), `var(--surface-card-hover)` (nav item hover states).
- **Text:** `var(--text-primary)` (active navigation links), `var(--text-secondary)` (inactive links), `var(--text-muted)` (footer captions).
- **Brand & Focus:** `var(--color-brand-primary)` (active indicators), `var(--color-border-focus)` (2px outline with 2px offset).
- **Z-Index Scale:** `var(--z-sticky)` (header), `var(--z-drawer)` (mobile drawer), `var(--z-modal)` (dialogs).

### 2.2 Shared UI Components Available
The foundational component library in `frontend/src/components/` is fully verified and ready for composition:
- [`Button`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/common/Button.tsx): Variants `primary`, `secondary`, `outline`, `destructive`, `ghost`. Supports `leftIcon`, `rightIcon`, and `isLoading`.
- [`Badge`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/common/Badge.tsx): For rendering active session role tags (`ISSUER`, `HOLDER`, `ADMIN`).
- [`SkipLink`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/layout/SkipLink.tsx): Keyboard bypass to `#main-content`.
- [`PageHeader`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/layout/PageHeader.tsx): For top-of-page breadcrumb, title, and action bars.
- [`Dialog`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/feedback/Dialog.tsx): Accessible modal with focus trapping and `Esc` dismiss.

---

## 3. Navigation Requirements from Phase 2 to Implement in Phase 4

### 3.1 Public Navigation (Always Visible)
1. **Brand Identity:** Logo and wordmark linking to `/` (Home).
2. **Primary Verification Action:** High-visibility CTA button `"Verify Document"` navigating to `/verify`.
3. **Session Action:** `"Operator Login"` button navigating to `/login` when unauthenticated.

### 3.2 Dynamic Role-Based Authenticated Links (Subscribed to `authStore`)
When an operator session is active in `authStore.getSnapshot()`:
- **`ROLE_HOLDER`:** Displays `"My Credentials"` (`/holder` or `/credentials`).
- **`ROLE_ISSUER`:** Displays `"Issuer Workspace"` (`/issuer`), `"Audit Logs"` (`/audit-logs`).
- **`ROLE_ADMIN`:** Displays `"Administration"` (`/administration`), `"Issuer Workspace"` (`/issuer`), `"Audit Logs"` (`/audit-logs`).
- **Session Info & Disconnect:** Displays active subject name, role badge, and an accessible `"Disconnect"` button that triggers `authStore.clearToken()`.

### 3.3 Mobile Navigation Drawer (`< 768px`)
- Controlled via an accessible hamburger button with `aria-expanded` and `aria-controls`.
- Uses a focus-trapped slide-over drawer with a semi-transparent backdrop.
- Closes automatically when any navigation link is clicked or the `Esc` key is pressed.

---

## 4. Accessibility & Responsive Constraints for Phase 4

1. **Landmarks:** Header must use `<header role="banner">`; Navigation must use `<nav aria-label="Main Navigation">`; Main must use `<main id="main-content" tabIndex={-1}>`; Footer must use `<footer role="contentinfo">`.
2. **Active Link Indication:** Active routes must set `aria-current="page"` and render a visual bottom border or background highlight.
3. **Touch Targets:** All interactive links and buttons in the header and mobile drawer must maintain at least 44x44 px touch bounding boxes.

---

## 5. Explicit Phase 4 Scope Boundaries

During Phase 4, developers MUST:
- Refactor and polish [`AppLayout.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/layout/AppLayout.tsx), [`Header.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/layout/Header.tsx), [`Footer.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/layout/Footer.tsx), and [`MobileNavDrawer.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/layout/MobileNavDrawer.tsx).
- Bind active navigation state to `authStore`.

During Phase 4, developers MUST NOT:
- Implement or rewrite the internal contents of feature dashboards (`DocumentUploadCard`, `QrScannerCard`, `KeyRegistryCard`).
- Alter backend APIs, routing paths, or database schemas.
- Introduce new external dependencies.
