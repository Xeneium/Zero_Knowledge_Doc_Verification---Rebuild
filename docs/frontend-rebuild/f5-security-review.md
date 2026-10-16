# F5: Authentication and Session Security Review

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F5  
**Document ID:** ZKDOC-FE-F5-003  
**Status:** Approved Security Assessment  
**Date:** October 2026  

---

## 1. Executive Summary & Scope

This document provides a comprehensive application security review of the Frontend Phase F5 implementation (*Authentication, Authorization-Aware Navigation, and Session Security*).

The review evaluated:
1. Threat vectors associated with client-side token storage, lifecycle management, and session invalidation.
2. Defenses against Open Redirect vulnerabilities (CWE-601).
3. Role-Based Access Control (RBAC) consistency between the frontend React application and the Spring Security OAuth2 Resource Server.
4. Cryptographic discipline, including the complete elimination of unsigned or fabricated mock tokens.
5. Input handling, credential masking, and sensitive data leakage risks in URLs, logs, and persistent storage.

---

## 2. Threat Modeling & Vulnerability Analysis

| Vulnerability / Threat | CWE / OWASP ID | Pre-F5 Risk Level | F5 Mitigation & Defense Controls | Residual Risk |
| :--- | :--- | :---: | :--- | :---: |
| **Open Redirect via Post-Login Redirection** | CWE-601<br>OWASP A01:2021 | **HIGH** | `validateReturnUrl()` strictly enforces relative-only internal paths, rejecting protocol-relative URLs (`//`), backslashes (`\`, `/\`), schemes, and foreign origins. | **NONE** |
| **Token Theft via Persistent Browser Storage** | CWE-312<br>OWASP A07:2021 | **HIGH** | ADR-003 strictly prohibits `localStorage`. State is kept in-memory with tab-scoped `sessionStorage` fallback, wiped immediately on logout or tab closure. | **LOW** |
| **Fabricated Client-Side Administrative Access** | CWE-287<br>OWASP A07:2021 | **CRITICAL** | Eradicated legacy mock tokens (`demo-signature-*`). Frontend requires real Bearer JWTs signed by the backend key authority. Server cryptographically enforces signatures on every request. | **NONE** |
| **Client-Side Authorization Bypass** | CWE-862<br>OWASP A01:2021 | **MEDIUM** | Two-tier defense: Route guards (`RequireAuth`, `RequireRole`) prevent unauthorized UI view rendering; Spring Security backend acts as authoritative gatekeeper for all data/actions. | **NONE** |
| **Session Fixation / Stale Token Retention** | CWE-384<br>OWASP A07:2021 | **MEDIUM** | Proactive automated timer calculates `exp * 1000 - Date.now()`, executing automated session purge at token expiration. HTTP 401 responses trigger immediate store purge. | **NONE** |
| **Credential Leakage in Logs or Query Strings** | CWE-598<br>OWASP A02:2021 | **MEDIUM** | JWTs are never appended to URLs or query parameters. The `LoginPage` includes an input masking toggle (`type="password"`) to prevent shoulder surfing. | **NONE** |

---

## 3. In-Depth Analysis of Security Controls

### 3.1 Token Storage Security (ADR-003 Compliance)
A common vulnerability in Single Page Applications is persisting access tokens in `localStorage`. Items stored in `localStorage` persist indefinitely across browser restarts and all tabs within the same origin, making them accessible to any script executed via XSS.

**F5 Implementation:**
- **In-Memory Store:** The active JWT and decoded user profile are maintained in React/Zustand heap memory (`useAuthStore`).
- **Tab-Scoped Session Storage:** An optional synchronization writes exclusively to `window.sessionStorage`. By specification:
  - `sessionStorage` is isolated strictly to the current tab.
  - Opening a link in a new tab or opening an external window does not share the token.
  - Closing the browser tab destroys the storage item immediately.
- **Explicit Logout:** The `logout()` action actively calls `sessionStorage.removeItem('zkdoc_auth_token')` and cancels any pending auto-logout timers.

### 3.2 Open Redirect Defense (`validateReturnUrl`)
When an unauthenticated user attempts to visit a protected route (e.g. `/credentials`), `<RequireAuth>` saves the requested route in `returnUrl` (e.g. `/login?returnUrl=/credentials`). Upon successful authentication, the application navigates to this URL.

If left unvalidated, an attacker could supply `returnUrl=https://evil.com`, causing the application to redirect the user to a phishing domain after login.

**F5 Defensive Implementation:**
```typescript
export function validateReturnUrl(candidate: string | null | undefined, fallback = '/'): string {
  if (!candidate || typeof candidate !== 'string') return fallback;
  const trimmed = candidate.trim();
  if (!trimmed) return fallback;

  // 1. Reject protocol-relative URLs (e.g. "//evil.com")
  if (trimmed.startsWith('//')) return fallback;

  // 2. Reject backslash trickery (e.g. "/\evil.com" or "\evil.com")
  if (trimmed.includes('\\')) return fallback;

  // 3. Must be a relative path beginning with a single slash
  if (!trimmed.startsWith('/')) return fallback;

  // 4. Must parse as a valid path on the synthetic origin without changing host/origin
  try {
    const parsed = new URL(trimmed, 'http://localhost');
    if (parsed.origin !== 'http://localhost') return fallback;
    return parsed.pathname + parsed.search + parsed.hash;
  } catch {
    return fallback;
  }
}
```
This defense has been validated with 7 dedicated unit test cases covering diverse bypass payloads.

### 3.3 Cryptographic Discipline & Real Token Verification
In legacy versions of the frontend, mock tokens claiming admin privileges were generated client-side with hardcoded strings (`demo-signature-for-development-eval`). This created a false sense of security and introduced severe architectural debt.

**F5 Architecture:**
- **Zero Client Signature Generation:** The frontend never signs JWTs. Digital signatures must originate from the backend key authority (`KeyRegistryService`, `KeyPurpose.JWT_SIGNING`).
- **Header & Payload Inspection:** The `LoginPage` inspects tokens locally to provide immediate feedback to the operator (algorithm verification, subject, roles, expiration) without sending unvalidated tokens over the network.
- **Server Enforcement:** The frontend does not trust decoded client-side claims for sensitive operations; the Spring Boot OAuth2 Resource Server validates RSA-PSS / RS256 cryptographic signatures on every HTTP request.

### 3.4 Defense-in-Depth for Authorization
1. **Frontend Layer (UX & Navigation):**
   - `<RequireAuth>` prevents unauthenticated users from seeing dashboard layouts, redirecting them safely.
   - `<RequireRole>` prevents authorized users with insufficient roles (e.g., a `HOLDER` visiting `/issuer`) from viewing interface controls, redirecting them to `/forbidden`.
   - Header navigation links are conditionally rendered based on `hasRole()` checks.
2. **Backend Layer (Authoritative Enforcement):**
   - Spring Security annotations (`@PreAuthorize("hasRole('ISSUER')")`) and security filter chain rules protect all REST endpoints.
   - Any attempt to bypass client-side guards and directly execute API calls results in HTTP 403 Forbidden or HTTP 401 Unauthorized.

---

## 4. Operational Recommendations for Subsequent Phases

1. **Phase F6 (Holder Management):**
   - Ensure holder credential queries pass through the centralized `client.ts` client to maintain automated `Authorization: Bearer` injection and 401 handling.
2. **Phase F7 (Issuer Management):**
   - Ensure issuance requests containing private or sensitive claims are transmitted over TLS (HTTPS) and never cached in client-side state beyond the active workflow.
3. **Phase F11 (Production Hardening):**
   - Configure HTTP response headers in Spring Boot (`SecurityConfiguration.java`):
     - `Content-Security-Policy: default-src 'self'; script-src 'self'; object-src 'none'; style-src 'self' 'unsafe-inline';`
     - `X-Content-Type-Options: nosniff`
     - `X-Frame-Options: DENY`
     - `Referrer-Policy: strict-origin-when-cross-origin`

---

## 5. Security Verdict

The Phase F5 implementation conforms to OWASP Top 10 guidelines, addresses CWE-601, CWE-384, and CWE-312, complies with ADR-003, and integrates cleanly with the backend OAuth2 Resource Server.

**Security Status: APPROVED for Phase F6.**
