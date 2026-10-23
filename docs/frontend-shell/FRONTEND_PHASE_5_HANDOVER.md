# Phase 5 Handover Specification — Authentication & Access Control
## Zero-Knowledge Document Verification Platform

---

## 1. Phase 4 Completion Baseline

Phase 4 (**Application Shell & Navigation**) has completed successfully. All shell components, responsive layout frames, and navigation structures are implemented, accessible, tested, and validated.

### Baseline Status:
- **TypeScript**: 0 errors (`npm run typecheck`)
- **ESLint**: 0 warnings, 0 errors (`npm run lint`)
- **Vitest Suite**: 24 test files, 280 tests passed (100% passing)
- **Dedicated Shell Suite**: `tests/layoutComponents.test.tsx` (20 tests passed)
- **Production Build**: Output generated to `zkdoc-api/src/main/resources/static/`
- **Design Tokens**: `tokens.css` dual-mode variables integrated across all shell elements
- **Accessibility**: Explicit landmarks (`banner`, `navigation`, `main`, `contentinfo`), circular keyboard focus trapping, focus restoration, body scroll locking, and touch targets $\ge 44 \times 44\text{px}$.

---

## 2. Handover Boundary & Phase 5 Mission

### Objective of Phase 5:
**Authentication, Session Lifecycle Management, and Role-Based Access Control Interface**

Phase 5 will implement the complete operator authentication interface and session lifecycle management, connecting the shell to cryptographic bearer credentials and role-governed views.

### Interfaces Ready for Consumption in Phase 5:
1. **`Header` Session Integration**:
   - Automatically subscribes to `authStore.subscribe(setAuth)`.
   - Reactively displays authenticated session status banner when `auth.isAuthenticated === true`.
   - Displays user subject badge and calls `authStore.clearSession()` when the user clicks "Disconnect".
   - Dynamically mounts role-gated navigation links for `HOLDER`, `ISSUER`, and `ADMIN`.
2. **`MobileNavDrawer` Session Integration**:
   - Renders authenticated user details, assigned roles, and workspace links.
   - Renders a prominent "Disconnect Session" button with full keyboard trapping.
3. **`AppLayout` & Route Protection**:
   - `App.tsx` already wires `<Route element={<RequireAuth />}>` and `<Route element={<RequireRole allowedRoles={[...]} />}>`.
   - When unauthenticated users attempt access to protected routes (`/credentials`, `/issuer`, `/audit-logs`, `/administration`), `RequireAuth` redirects to `/login?returnUrl=...`.

---

## 3. Phase 5 Implementation Scope & Deliverables

Phase 5 will address:

### 3.1 Authentication View (`src/routes/LoginPage.tsx`)
- Enhance the operator login screen with production form validation, loading states, and zero-trust security explanations.
- Provide interactive Bearer Token decoding preview (safe parsing of RFC 7519 payload without signature execution in browser).
- Implement quick demo credential selectors for testing roles (`ADMIN`, `ISSUER`, `HOLDER`) in development/audit modes.
- Implement strict open redirect defense on `returnUrl`: only internal relative paths starting with `/` (excluding `//`, `http://`, `https://`, `javascript:`) are accepted.

### 3.2 Session Lifecycle & Auto-Refresh / Expiry
- Implement active expiration timer checks using `auth.user.expiration`.
- Display session expiration countdown or warning modal when token expiration is imminent (< 5 minutes).
- Automatic graceful session clearing and redirect when token expires.
- Broadcast session termination across browser tabs via `storage` events.

### 3.3 Access Control & Error Handling
- Comprehensive 403 Forbidden page (`ForbiddenPage.tsx`) explaining required roles vs held roles.
- Self-remediation guidance (e.g., reconnect with an elevated authority credential).

---

## 4. Architectural Invariants & Constraints for Phase 5

1. **Preserve Shell Landmark Hierarchy**: Do not alter `<header role="banner">`, `<main id="main-content" role="main">`, or `<footer role="contentinfo">`.
2. **Preserve `AuthState` Contract**: The layout components rely on:
   ```typescript
   export interface AuthState {
     readonly status: AuthStatus;
     readonly token: string | null;
     readonly user: AuthUser | null;
     readonly isAuthenticated: boolean;
     readonly error: string | null;
   }
   ```
   Do not modify property names or make existing fields incompatible.
3. **Preserve Autonomous Verification Channels**: Keep public verification routes (`/verify`, `/verify/:token`, `/predicate-verification`) strictly public and accessible without any authentication challenge.
4. **Zero Regressions**: Maintain 100% test pass rate across the existing 280 tests.

---

## 5. Next Steps Checklist for Phase 5 Kickoff

- [x] Phase 4 Shell & Navigation implemented and verified.
- [x] Phase 4 test suite passing (20 new tests, 280 total tests).
- [x] Phase 4 documentation delivered in `docs/frontend-shell/`.
- [ ] Review `src/routes/LoginPage.tsx` and `src/components/auth/` for Phase 5 tasks.
- [ ] Implement token claim inspection and open redirect mitigations in Phase 5.
- [ ] Validate session expiry timers and multi-tab sync in Phase 5.
