# FRONTEND PHASE 6 HANDOVER
## Authentication & Access Control to Document Upload Experience
### Zero-Knowledge Document Verification Platform

---

## 1. Phase 5 Completion Status

**Status: PASS — Phase 5 Completed.**

Phase 5 has established a stable, battle-tested, accessible authentication foundation. All quality gates (type checking, linting, unit/component tests, and production bundling) pass with 100% compliance across 284 automated tests.

---

## 2. Authentication Interfaces and Reusable Components

Phase 6 developers can immediately leverage the following foundational components and hooks:

### 2.1 Reusable Auth Components
- **`RequireAuth` (`frontend/src/components/auth/RequireAuth.tsx`)**: Wraps any route to ensure the user is logged in. Redirects to `/login?returnUrl=...` if not.
- **`RequireRole` (`frontend/src/components/auth/RequireRole.tsx`)**: Wraps routes requiring specific roles (e.g. `['ISSUER', 'ADMIN']`). Redirects to `/forbidden` if unauthorized.
- **`ForbiddenPage` (`frontend/src/routes/ForbiddenPage.tsx`)**: Displays a clean 403 screen with user subject, roles, and a quick switch button.
- **`LoginPage` (`frontend/src/routes/LoginPage.tsx`)**: Complete JWT connection page with live claims decoding and validation.

### 2.2 Reusable State Hooks
- **`useAuth()` (`frontend/src/state/authStore.ts`)**:
  ```typescript
  import { useAuth, authStore } from '../state/authStore';

  const { isAuthenticated, user, token, status } = useAuth();
  // user.subject: string (e.g. "issuer@zkdoc.internal")
  // user.roles: string[] (e.g. ["ISSUER"])
  // authStore.hasRole('ISSUER'): boolean
  // authStore.hasAnyRole(['ISSUER', 'ADMIN']): boolean
  ```

---

## 3. API-Client Conventions for Phase 6

Phase 6 will implement the **Document Upload Experience**. The API client in `frontend/src/api/client.ts` is already wired to inject the active bearer token automatically:

1. **Automatic Header Injection**:
   Every request made via `apiClient` or specialized API wrappers automatically attaches:
   ```
   Authorization: Bearer <token>
   ```
2. **Standard 401/403 Handling**:
   The response interceptor in `client.ts` catches `401 Unauthorized` responses and automatically triggers `authStore.handleTokenExpiration()`, transitioning the UI to the expired state and redirecting the user safely to `/login`.
3. **Multipart Form Data Requirement**:
   For document uploads in Phase 6, send requests using standard `FormData` without manually overriding the `Content-Type` header (allowing Axios/the browser to append the appropriate boundary):
   ```typescript
   const formData = new FormData();
   formData.append('file', file);
   const response = await apiClient.post('/api/v1/documents/upload', formData, {
     headers: { 'Content-Type': 'multipart/form-data' },
   });
   ```

---

## 4. Existing Routes & Access Matrix

| Path | Protection | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `/` | Public | None | Application overview & feature map |
| `/verify` | Public | None | Public capability & QR code verification |
| `/login` | Public | None | Cryptographic session connection terminal |
| `/forbidden` | Public | None | 403 Access Denied explanation page |
| `/issuer` | Protected | `ISSUER`, `ADMIN` | Target for Phase 6 Document Upload workflows |
| `/holder` | Protected | `HOLDER`, `ADMIN` | Holder wallet & credential management |
| `/admin` | Protected | `ADMIN` | System administration & key registry |
| `/audit` | Protected | `ADMIN` | Cryptographic audit log trail |

---

## 5. Guidance for Phase 6 — Document Upload Experience

When commencing Phase 6, adhere strictly to the following technical constraints:

1. **Role Enforcement**:
   - The document upload workflow must remain accessible only to users with the `ISSUER` or `ADMIN` role.
   - Use `<RequireRole allowedRoles={['ISSUER']}>` to wrap the issuer dashboard route.
2. **Client-Side Pre-Validation**:
   - File size ceiling: Enforce the backend maximum file size limit (25 MB) on the client before network transmission.
   - Allowed MIME types: Restrict to supported document formats (PDF, PNG, JPEG).
   - Compute the SHA-256 integrity hash in the browser using the Web Crypto API (`crypto.subtle.digest('SHA-256', buffer)`) to verify client-side integrity against the backend response.
3. **Honest Backend Contracts**:
   - Call `/api/v1/documents/upload` expecting standard document registration responses (`documentId`, `sha256Hash`, `status`).
   - Do not assume non-existent background virus-scanning microservices or third-party cloud S3 buckets unless verified in `zkdoc-document`.
4. **Preserve Zero-Trust Principles**:
   - Never cache document contents or cryptographic private keys in unencrypted browser storage.
