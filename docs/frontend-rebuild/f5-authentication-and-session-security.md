# F5: Authentication, Authorization-Aware Navigation, and Session Security

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F5  
**Document ID:** ZKDOC-FE-F5-001  
**Status:** Approved Implementation Report  
**Date:** October 2026  

---

## 1. Executive Summary & Objective

Phase F5 delivers a production-grade authentication, authorization-aware navigation, and session security foundation for the Zero-Knowledge Document Verification Platform. 

In strict adherence to **ADR-003** (*Token Storage and Session Lifecycle*) and backend security contracts established across the backend's 24 engineering phases:
1. **Zero Mock/Fake Tokens:** The application strictly disallows fabricated tokens or fake mock credentials (`demo-signature-*`).
2. **Resource Server Alignment:** Authentication aligns with the backend's Spring Security OAuth2 Resource Server (`zkdoc-api`), which enforces RSA-PSS / RS256 Bearer JWTs issued by the key authority (`https://auth.zkdoc.internal`, audience `zkdoc-api`).
3. **Storage Security:** Tokens are managed in-memory via Zustand with optional persistence strictly in `sessionStorage` to survive manual tab reloads during evaluation. **Storage in `localStorage` is categorically prohibited**, eliminating persistent XSS token theft vectors.
4. **CWE-601 Defense:** All redirect and return-path parameters are strictly validated via `validateReturnUrl()`, preventing open redirect attacks.
5. **Role-Aware Navigation & Guards:** Route-level guards (`RequireAuth`, `RequireRole`) protect sensitive views, while the header and mobile drawer adaptively display links based on active user roles (`ISSUER`, `HOLDER`, `VERIFIER`, `ADMIN`).

---

## 2. Backend Security Architecture & Contract Alignment

The backend (`zkdoc-api`) operates as an OAuth2 Resource Server configured in `SecurityConfiguration.java`:
- **Filter Chain:** `JwtAuthenticationFilter` intercepts requests and delegates cryptographic verification to `ManagedJwtDecoderProvider`.
- **Public Endpoints (Permitted without Authentication):**
  - Static frontend assets: `/`, `/index.html`, `/assets/**`, `/*.svg`, `/*.png`, `/*.ico`, `/*.json`
  - Actuator health check: `/actuator/health`
  - Public verification: `/api/v1/verify/**`, `/api/v1/credentials/verify-predicate`
- **Protected Endpoints (Bearer JWT Required):**
  - Holder operations: `/api/v1/holder/**` (`ROLE_HOLDER` or `ROLE_ADMIN`)
  - Issuer operations: `/api/v1/issuer/**`, `/api/v1/credentials/issue` (`ROLE_ISSUER` or `ROLE_ADMIN`)
  - Audit logging: `/api/v1/audit/**` (`ROLE_ADMIN` or `ROLE_VERIFIER`)
  - Key registry and administration: `/api/v1/keys/**` (`ROLE_ADMIN`)

### Authoritative JWT Contract
The frontend strictly validates and inspects JWTs according to RFC 7519 and RFC 8725:

| Claim | Type | Description | Frontend Handling |
| :--- | :--- | :--- | :--- |
| `sub` | `string` | Unique identifier of the authenticated user or service | Rendered in user profile badge and session inspector |
| `roles` | `string[]` or `string` | Granted authorities (e.g. `["ROLE_ISSUER", "ROLE_HOLDER"]`) | Normalized (strips `ROLE_`, uppercase), checked for authorization |
| `iss` | `string` | Issuer URL (`https://auth.zkdoc.internal`) | Displayed in inspection panel; verified by backend |
| `aud` | `string` or `string[]` | Audience (`zkdoc-api`) | Displayed in inspection panel; verified by backend |
| `exp` | `number` | Unix timestamp (seconds) when token expires | Drives real-time session expiration timer and auto-logout |
| `iat` | `number` | Unix timestamp when token was issued | Used to compute token age |

---

## 3. Frontend Token Storage & Lifecycle Architecture

### 3.1 ADR-003 Storage Policy
Per ADR-003, `localStorage` is disallowed because its persistence across browser sessions makes credentials vulnerable to long-term exfiltration if an XSS vector ever emerges. 

The frontend implements a multi-tier memory and session store:
```
       +---------------------------------------------+
       |           Active Browser Session            |
       |                                             |
       |  +---------------------------------------+  |
       |  |          Zustand Auth Store           |  |
       |  |  (In-Memory State: Fast, Ephemeral)   |  |
       |  +---------------------------------------+  |
       |                      |                      |
       |         Synchronized | on Token Set         |
       |                      v                      |
       |  +---------------------------------------+  |
       |  |         Window sessionStorage         |  |
       |  |  (Tab-scoped only; wiped on tab close)|  |
       |  +---------------------------------------+  |
       +---------------------------------------------+
                              |
                     WIPED ON LOGOUT
```

1. **Primary Store:** React state via Zustand (`useAuthStore`). Fast, reactive, not accessible across origins.
2. **Secondary Store:** `sessionStorage` (`zkdoc_auth_token`). Used exclusively to survive browser reloads (`F5`) in the active tab.
3. **Session Termination:** Closing the tab or clicking "Disconnect Session" immediately destroys both the in-memory state and the `sessionStorage` item.

### 3.2 Automated Expiration Watcher
Upon setting a token, `authStore` schedules an internal timeout based on the `exp` claim:
- Calculates remaining milliseconds: `delay = exp * 1000 - Date.now()`.
- If `delay <= 0`, the token is immediately rejected as expired.
- When `delay > 0`, a timer triggers automatic session teardown when expiration is reached, clearing memory and notifying the user.

---

## 4. Role Hierarchy and Authorization Engine

Roles are normalized to strip extraneous prefixes (`ROLE_`) and converted to uppercase.

### Role Hierarchy & Admin Superuser Access
An `ADMIN` identity inherits administrative privileges and acts as a superuser across all functional domains:
- `hasRole(role)`:
  - Returns `true` if user possesses the role.
  - Returns `true` if user possesses `ADMIN`.
- `hasAnyRole(roles)`:
  - Returns `true` if user possesses at least one of the queried roles.
  - Returns `true` if user possesses `ADMIN`.

```typescript
hasRole: (role: string) => {
  const currentRoles = get().user?.roles ?? [];
  const normalized = role.replace(/^ROLE_/, '').toUpperCase();
  if (currentRoles.includes('ADMIN')) return true;
  return currentRoles.includes(normalized);
}
```

---

## 5. Route Protection & Authorization Guards

Two higher-order route guards protect sensitive application boundaries:

### 5.1 `<RequireAuth>`
- **Purpose:** Enforces that a valid, unexpired session is present.
- **Loading State:** While the store hydrates from `sessionStorage`, renders an accessible loading skeleton (`Skeleton` with `aria-busy="true"`).
- **Unauthenticated Handling:** Redirects the user to `/login?returnUrl=<currentPath>`.
- **Security Check:** Sanitizes `returnUrl` to ensure it only encodes relative internal paths.

### 5.2 `<RequireRole>`
- **Purpose:** Enforces that the authenticated user possesses the specific role(s) required to view a page.
- **Missing Roles Handling:** Redirects the user to `/forbidden` (`ForbiddenPage`), preventing unauthorized access to views like Issuer Portal or Audit Logs.

### Route Mapping in `App.tsx`:
```tsx
{/* Public Routes */}
<Route path="/" element={<PublicVerifyPage />} />
<Route path="/login" element={<LoginPage />} />
<Route path="/forbidden" element={<ForbiddenPage />} />

{/* Protected Holder Routes */}
<Route
  path="/credentials"
  element={
    <RequireAuth>
      <RequireRole role="HOLDER">
        <HolderDashboardPage />
      </RequireRole>
    </RequireAuth>
  }
/>

{/* Protected Issuer Routes */}
<Route
  path="/issuer"
  element={
    <RequireAuth>
      <RequireRole role="ISSUER">
        <IssuerDashboardPage />
      </RequireRole>
    </RequireAuth>
  }
/>

{/* Protected Audit Routes */}
<Route
  path="/audit-logs"
  element={
    <RequireAuth>
      <RequireRole role="VERIFIER">
        <AuditLogsPage />
      </RequireRole>
    </RequireAuth>
  }
/>

{/* Protected Administration Routes */}
<Route
  path="/administration"
  element={
    <RequireAuth>
      <RequireRole role="ADMIN">
        <AdministrationPage />
      </RequireRole>
    </RequireAuth>
  }
/>
```

---

## 6. Open Redirect Defense (`validateReturnUrl`)

To prevent CWE-601 (Open Redirect vulnerabilities) when redirecting users post-authentication, `validateReturnUrl()` in `src/lib/utils/security.ts` applies strict defensive checks:

```typescript
export function validateReturnUrl(candidate: string | null | undefined, fallback = '/'): string {
  if (!candidate || typeof candidate !== 'string') return fallback;
  const trimmed = candidate.trim();
  if (!trimmed) return fallback;

  // Reject protocol-relative URLs (e.g. "//evil.com")
  if (trimmed.startsWith('//')) return fallback;

  // Reject backslash obfuscation (e.g. "/\evil.com" or "\evil.com")
  if (trimmed.includes('\\')) return fallback;

  // Must begin with a single slash
  if (!trimmed.startsWith('/')) return fallback;

  // Must parse as a valid path on synthetic origin
  try {
    const parsed = new URL(trimmed, 'http://localhost');
    if (parsed.origin !== 'http://localhost') return fallback;
    return parsed.pathname + parsed.search + parsed.hash;
  } catch {
    return fallback;
  }
}
```

---

## 7. User Interface: `LoginPage` & Adaptive Navigation

### 7.1 Accessible `LoginPage`
Located at `/login`, the login screen provides a secure token connection interface designed with F4 design system components:
- **Token Input:** Monospace textarea for pasting signed Bearer JWTs, featuring a visibility toggle to mask/unmask sensitive credentials.
- **Real-Time Client-Side Claim Inspector:** As the operator types or pastes a token, the component parses header and payload in real time without sending them across the network:
  - Verifies 3-part period-delimited structure.
  - Displays Subject (`sub`), Roles badge list, Expiration time, and Validity status.
  - Validates signature algorithm against allowed standards (`RS256`, `PS256`).
- **Session Duration Badge:** Shows computed session validity remaining (e.g. `Expires in 59m 42s`).
- **Error Feedback:** Renders contextual alerts for malformed Base64, invalid JSON, missing claims, or expired timestamps.
- **Active Session State:** When already authenticated, displays an informative card with current subject, roles, and a prominent "Disconnect Session" button.

### 7.2 Authorization-Aware Navigation
The primary application shell (`Header.tsx` and `MobileNavDrawer.tsx`) adaptively renders links:
- **Unauthenticated View:**
  - Displays "Public Verification".
  - Shows "Connect Session" action button leading to `/login`.
- **Authenticated View:**
  - Evaluates active user roles and renders authorized navigation items:
    - `HOLDER` / `ADMIN`: "Holder Credentials" (`/credentials`)
    - `ISSUER` / `ADMIN`: "Issuer Portal" (`/issuer`)
    - `VERIFIER` / `ADMIN`: "Audit Logs" (`/audit-logs`)
    - `ADMIN`: "Administration" (`/administration`)
  - Displays authenticated user badge with subject abbreviation and primary role badge.
  - Provides a single-click "Disconnect" action.

---

## 8. API Client Integration & 401 Interception

In `src/lib/api/client.ts`, all outgoing HTTP requests automatically attach the active Bearer token:
```typescript
const token = useAuthStore.getState().token;
if (token) {
  headers.set('Authorization', `Bearer ${token}`);
}
```
If any protected backend endpoint responds with HTTP 401 Unauthorized (e.g. due to key rotation or server-side expiration), the client automatically purges local auth state and dispatches an unauthorized event, redirecting the user to `/login`.

---

## 9. Verification & Evidence

- **Frontend Unit & Component Tests:** 76 passing tests across 6 test suites (`npm run test:run`).
- **Type Checking:** 0 errors (`tsc --noEmit`).
- **ESLint:** 0 warnings, 0 errors (`eslint --max-warnings 0`).
- **Code Formatting:** 100% Prettier compliant.
- **Production Build:** Succeeded in 710ms, static bundle packaged into `zkdoc-api`.
- **Spring Boot Integration Test:** `FrontendIntegrationTest` passed (9 tests, 0 failures).
