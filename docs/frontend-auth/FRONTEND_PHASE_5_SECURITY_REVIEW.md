# FRONTEND PHASE 5 SECURITY REVIEW
## Authentication & Access Control UI
### Zero-Knowledge Document Verification Platform

---

## 1. Executive Summary

This Security Review provides an in-depth threat modeling and defensive posture evaluation of the authentication and access-control interface implemented in Phase 5.

The platform processes sensitive cryptographic commitments, digital signatures, and identity claims. In keeping with zero-trust architectural tenets, **frontend route guards and client-side claims decoding are treated strictly as user-experience optimizations, while backend cryptographic enforcement remains authoritative**.

---

## 2. Threat Modeling & Defensive Controls

### 2.1 Credential & Token Storage Assessment
- **Risk**: Storing authentication credentials in `window.localStorage` exposes tokens to persistent extraction via Cross-Site Scripting (XSS) and leaves credentials vulnerable on shared computers.
- **Defensive Posture**:
  - The application strictly uses `window.sessionStorage` scoped under the key `zkdoc_auth_token`.
  - Closing the browser tab or terminating the session automatically purges the stored value.
  - The in-memory store (`authStore`) maintains an explicit ephemeral copy with an automatic JavaScript teardown lifecycle that purges cached data across all domain stores upon logout.
- **Residual Risk**: Any script execution vulnerability (XSS) in the browser origin could still extract the in-memory or session-stored bearer token during an active session. Content Security Policy (CSP) headers configured on Spring Boot mitigate inline script execution.

### 2.2 Input Masking and Spellcheck Caching
- **Risk**: Typing or pasting high-entropy cryptographic strings into browser textareas can trigger dictionary indexing, spellcheck submission to third-party cloud services (e.g., Grammarly, OS telemetry), or autocomplete autofill caching.
- **Defensive Posture**:
  - The bearer token textarea explicitly sets `autoComplete="off"`, `spellCheck={false}`, and `autocorrect="off"`.
  - The UI masks raw token signatures in preview panels, displaying only truncated prefixes (`eyJh...`) alongside parsed claims.

### 2.3 Open Redirect Protection
- **Risk**: Attackers construct malicious links such as `https://zkdoc.example.com/login?returnUrl=https://attacker.com` to trick users into redirecting to phishing portals post-authentication.
- **Defensive Posture**:
  - `validateReturnUrl()` in `LoginPage.tsx` enforces strict validation:
    - Must start with a single `/`.
    - Cannot begin with `//` (protocol-relative URL bypass).
    - Cannot contain protocol schemes (`http:`, `https:`, `javascript:`, `data:`).
    - Cannot contain control characters or newlines (`\r`, `\n`).
  - Fallback: Any invalid or external URL silently defaults to the root overview `/`.
  - Verified by automated unit tests in `tests/authComponents.test.tsx`.

### 2.4 Cross-Tab Logout Synchronization
- **Risk**: If a user logs out in Tab A, Tab B may remain in an authenticated state in memory, allowing unintended actions if another party accesses the machine.
- **Defensive Posture**:
  - `authStore` implements a browser-native `BroadcastChannel('zkdoc_auth_sync')`.
  - Calling `clearSession()` broadcasts a `LOGOUT` payload across all open tabs within the origin.
  - Sibling tabs intercept the message, clear their in-memory tokens, purge domain store caches, and notify active UI components.

### 2.5 Role & Permission Handling Boundary
- **Principle**: *The client interface never guarantees security; the backend API enforces authorization.*
- **Implementation**:
  - `RequireRole` and `RequireAuth` inspect claims (`roles`, `sub`) purely to guide user navigation and display appropriate views.
  - Every protected API call sent to `/api/v1/**` includes the raw `Authorization: Bearer <token>` header.
  - Spring Security's `JwtAuthenticationFilter` and method-level security (`@PreAuthorize`) validate digital signatures against `KeyRegistry` and re-evaluate authorities independently on every single request.
  - Even if a malicious user alters client-side memory to force `RequireRole` to render an Issuer dashboard, any subsequent API request will receive an unforgeable `401 Unauthorized` or `403 Forbidden` response from Spring Boot.

### 2.6 CSRF Considerations
- **Architecture**: The application does not use ambient session cookies for authentication.
- **Protection**: All authenticated requests use explicit `Authorization: Bearer <token>` headers attached via Axios request interceptors (`frontend/src/api/client.ts`).
- **Result**: Because browsers do not automatically attach `Authorization` headers in cross-origin requests, Cross-Site Request Forgery (CSRF) attacks are fundamentally precluded by the transport mechanism. Disabling CSRF in Spring Security (`csrf.disable()`) is therefore verified and safe.

---

## 3. Findings Matrix

| Finding ID | Component | Severity | Description | Status / Remediation |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-AUTH-01** | `authStore.ts` | Medium | Storage of JWT in `sessionStorage` vulnerable to active XSS. | **Accepted Residual Risk**: Standard for single-page applications without server-side cookie proxying. Mitigated by strict CSP. |
| **SEC-AUTH-02** | `LoginPage.tsx` | High | Open redirect risk via `?returnUrl=` parameter. | **Remediated**: Validated strictly with regex prohibiting protocol schemes and double slashes. |
| **SEC-AUTH-03** | `LoginPage.tsx` | Low | Browser telemetry/spellcheck capturing bearer token. | **Remediated**: Added `spellCheck={false}` and `autoComplete="off"`. |
| **SEC-AUTH-04** | `ForbiddenPage.tsx` | Informational | Revealing connected subject and roles on 403 page. | **Verified Safe**: Displays only claims already held by the client; aids debugging and zero-trust verification. |
| **SEC-AUTH-05** | Backend API | Low | Lack of server-side token revocation / blocklist. | **Documented Limitation**: Stateless JWTs remain valid until `exp`. Short token lifetimes (e.g. 15–60 min) recommended in production. |

---

## 4. Backend Security Assumptions

The frontend implementation relies on the following backend guarantees, all verified during the Phase 5 backend audit:
1. Public keys used to sign JWTs are securely registered and managed in `KeyRegistry` (`RS256` / `PS256`).
2. Spring Security rejects unsigned, corrupted, expired, or non-matching audience (`aud != zkdoc-api`) tokens.
3. No secret keys or private keys are exposed via any public REST endpoints.
4. Demo token endpoints (`/api/public/demo-tokens`) are only enabled during evaluation or non-production profiles.
