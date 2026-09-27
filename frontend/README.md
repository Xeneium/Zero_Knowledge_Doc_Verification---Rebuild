# Verity Zero frontend

The React client provides the responsive foundation, public document verification, platform-operations boundary, and Phase 6 release hardening for the Zero-Knowledge Document Verification Platform. A production build is served by `zkdoc-api`; Vite remains available for frontend development.

## Runtime and stack

- Node.js 22.12 or newer (Node.js 24 LTS is recommended)
- npm 11 or newer; `package-lock.json` is the authoritative lockfile
- React 19.3.0 and React DOM 19.3.0
- TypeScript 6.0.3 in strict mode
- Vite 8.3.1 with `@vitejs/plugin-react` 6.1.1
- React Router 8.4.0
- Tailwind CSS 4.3.3 through the Vite plugin
- Lucide React 1.48.0
- ZXing Browser 0.2.1 for local camera QR decoding
- Vitest 5.0.2, jsdom 30.1.1, and React Testing Library 16.3.3
- ESLint 10.11.0 and Prettier 3.9.9

Exact resolved transitive versions are recorded in `package-lock.json`.

## Start the complete application

In IntelliJ IDEA, reload the root Maven project, allow the `zkdoc-api` `generate-resources` phase to build the frontend, then run `zkdoc-api/src/main/java/com/zkdoc/api/Application.java`. Open `http://localhost:8080`; a separate Vite process is not required for normal application use.

The Maven integration installs its pinned Node/npm runtime under `zkdoc-api/target`, runs `npm ci`, builds the frontend, and packages the generated assets with Spring Boot. Known SPA routes are forwarded to `index.html`; `/api/**` remains under Spring MVC and Spring Security.

On Windows, stop a running Vite development server before invoking the Maven frontend lifecycle because `npm ci` replaces native files under `node_modules`; restart Vite after the Maven build completes.

## Optional Vite development

Run the Spring Boot backend separately on port 8080. From `frontend/`:

```powershell
npm ci
Copy-Item .env.example .env.local
npm run dev
```

Open `http://localhost:5173`. The overview performs one request to the real `GET /api/health` endpoint. If the API is stopped or the response does not match `{ "status": "ok" }`, the UI reports that the backend is unavailable without blocking navigation.

The checked-in local configuration uses a same-origin `/api` request and Vite proxies it to `http://localhost:8080`. Override the development destination with `VITE_DEV_PROXY_TARGET` when necessary.

## Environment variables

| Variable                | Runtime                      | Purpose                                 | Default                 |
| ----------------------- | ---------------------------- | --------------------------------------- | ----------------------- |
| `VITE_API_BASE_URL`     | Browser bundle               | API origin prefix or root-relative base | `/`                     |
| `VITE_DEV_PROXY_TARGET` | Vite development server only | Destination for `/api` proxy requests   | `http://localhost:8080` |

Only `VITE_` values intended for the browser may be used in client code. Never put secrets, private keys, passwords, or JWT signing material in frontend environment files. The API base URL validator accepts only root-relative paths or credential-free HTTP(S) URLs.

The backend currently has no CORS policy for `http://localhost:5173`, so direct cross-origin browser requests are not the default. Production uses the integrated same-origin Spring Boot server. Its controller forwards the app's known client routes to `index.html` without intercepting `/api/**`.

## Commands

```powershell
npm run dev          # Vite development server on port 5173
npm run build        # strict type check, then production bundle
npm run preview      # preview dist/ on port 4173
npm run test         # interactive Vitest watch mode
npm run test:run     # one deterministic test run
npm run lint         # ESLint, with warnings treated as failures
npm run typecheck    # TypeScript without emitting files
npm run format       # apply Prettier formatting
npm run format:check # verify formatting without changing files
```

## Architecture

```text
src/
├── app/                 # router, providers, error boundary, lazy feature routes
├── components/
│   ├── feedback/        # empty and page-error states
│   ├── layout/          # responsive shell, header, sidebar, page headers
│   ├── navigation/      # breadcrumbs
│   └── ui/              # typed, accessible design-system primitives
├── config/              # validated public environment configuration
├── features/dashboard/  # overview and live health status
├── features/verification/ # public verification, QR scanner, validation, results
├── features/administration/ # audited platform-operations boundary
├── hooks/               # shared React behavior
├── lib/
│   ├── api/             # centralized Fetch client, errors, health contract
│   └── utils/           # framework-independent utilities
├── routes/              # placeholders and 404 page
├── styles/              # CSS design tokens and Tailwind entry point
└── main.tsx              # browser entry point

tests/                   # component, routing, verification, and API-client tests
```

The app uses local React state for the mobile drawer, health state, and short-lived verification workflow. No global state library is required. API requests use the existing client in `src/lib/api`; feature pages do not duplicate Fetch behavior.

## Implemented frontend capabilities

- Original responsive shell with desktop sidebar and accessible mobile drawer
- Overview, credentials, verification, predicate verification, audit, administration, and custom 404 routes
- Central design tokens for color, type, spacing, radii, shadows, and focus presentation
- Reusable controls and feedback primitives, including buttons, form fields, badges, cards, dialogs, tabs, alerts, loaders, toasts, empty states, and confirmation patterns
- Typed Fetch client with JSON serialization, content-type checks, empty responses, HTTP/network/timeout/cancellation errors, and `AbortSignal` support
- Validated environment configuration and a non-blocking health indicator using the backend’s actual response contract
- Global render error boundary, reduced-motion handling, skip navigation, semantic landmarks, visible focus, accessible labels, and touch-sized controls
- Strict TypeScript, ESLint, Prettier, deterministic component tests, and production bundling
- Public `/verify` workflow backed by `GET /api/public/verify/{token}`
- Manual entry for current 43-character base64url tokens, legacy 64-character lowercase hexadecimal tokens, and exact backend verification URLs
- User-initiated camera QR scanning with explicit stop/retry controls, camera cleanup, and a manual fallback
- Runtime validation of the public `{ signatureValid, documentActive }` response before any result is trusted
- Distinct verified, inactive, invalid-signature, not-found, rate-limit, network, timeout, and server-failure presentation
- Privacy controls: QR URLs are parsed rather than opened, camera frames remain local, and references/results are not placed in browser storage or URLs
- `/administration` platform-operations landing page documenting the real authentication, audit, account, session, role, and key-management boundaries
- Responsive capability table that clearly labels unavailable, write-only, and backend-only operations without fetching protected data or rendering nonfunctional controls
- Route-level lazy loading for public verification and platform operations, with an accessible loading announcement
- Sanitized HTTP failures: backend error messages are never propagated to presentation code, and only constrained machine-readable error codes are retained
- Hardened mobile navigation with hidden off-canvas links, background interaction blocking, scroll locking, and focus restoration
- Validation focus moves to the verification-reference field when submission fails
- Production source maps are disabled and QR scanning remains a separate on-demand chunk

## Route inventory

| Route                     | Access and behavior                                            |
| ------------------------- | -------------------------------------------------------------- |
| `/`                       | Public overview and real `GET /api/health` status              |
| `/verify`                 | Public manual/QR verification using the real public API        |
| `/credentials`            | Non-operational boundary; authenticated workflow is absent     |
| `/verification`           | Non-operational boundary for protected document checks         |
| `/predicate-verification` | Non-operational boundary; requires a real `VERIFIER` context   |
| `/audit-logs`             | Non-operational boundary because no audit-read API exists      |
| `/administration`         | Informational operations boundary containing no protected data |
| `*`                       | Client-side not-found page                                     |

All supported direct routes are explicitly forwarded to `index.html` by Spring Boot. `/api/**` remains outside the SPA fallback and under backend routing and authorization.

## Phase 6 quality evidence

- Strict TypeScript, ESLint, Prettier, and all Vitest suites pass.
- The production build splits public verification and platform operations from the initial application bundle. ZXing remains loaded only after an explicit camera-start action.
- The measured uncompressed main JavaScript chunk is approximately 348 kB, reduced from approximately 371 kB before route splitting. Public verification is approximately 16 kB, platform operations approximately 8 kB, and the on-demand ZXing chunk approximately 478 kB.
- `npm audit --omit=dev` reported zero known production vulnerabilities at the time of the Phase 6 audit.
- Browser validation covers the implemented public route and operations page. Physical camera hardware and additional browser engines were unavailable, so cross-browser and real-device camera compatibility are not claimed.
- The local JDK 26 and cached Maven 3.9.16 toolchain compiled the seven-module reactor and passed all 29 API/integration tests. A packaged application smoke run applied all six Flyway migrations and served every documented SPA deep link on a separate local port while leaving unknown `/api/**` paths protected.

## Verification result interpretation

- **Document verified** means the backend returned `signatureValid: true` and `documentActive: true`.
- **Document is inactive** means the signature is valid but the stored record is not active. The public contract does not disclose why.
- **Document signature is invalid** means the backend completed verification and returned `signatureValid: false`.
- **Unable to verify** covers transport, timeout, malformed-response, and server failures. These failures are never presented as evidence that a document is invalid.

The public endpoint returns no document contents, issuer identity, hash, signature material, or internal identifiers. The interface describes minimal disclosure separately from zero-knowledge proof verification.

## Camera behavior

Camera access is requested only after **Start camera** is selected and requires a browser with `navigator.mediaDevices.getUserMedia` in a secure context (`https://` or localhost). The scanner prefers the rear-facing camera, decodes frames locally, stops after a successful scan, stops on user request, and cleans up on unmount/navigation. Permission denial, no-camera, and unsupported-browser cases direct the user to manual entry.

## Authorization and unsupported capabilities

Public verification requires no JWT and exposes only the backend's two public booleans. Authenticated stored/uploaded document verification and credential verification remain backend-only because this repository has no configured external JWT issuer or frontend authentication provider. The verifier-only Bulletproof predicate endpoint is not exposed as an unauthenticated UI. Verification history is not implemented because the backend has no audit/history read endpoint.

### Phase 5 administration audit

- **Account settings:** unavailable. There are no profile, account-status, profile-update, or password-change endpoints.
- **Session management:** unavailable. Spring Security is stateless and the backend has no session list, refresh-token store, or token-revocation operation.
- **Users and roles:** unavailable. There is no user directory, account lifecycle, role assignment, or role creation API.
- **Audit viewing:** unavailable. Verification events are written to SQLite, but there is no authorized read, detail, filter, pagination, or export endpoint. The records are not described as immutable or tamper-proof.
- **Key administration:** backend-only. `POST /api/admin/keys` registers public-key metadata and `PATCH /api/admin/keys/{keyId}/status` changes status for an existing key. Both require `ADMIN`. The frontend deliberately offers no blind mutation form because it has no authenticated ADMIN context and the backend exposes no key list/detail contract.
- **Administrative dashboard:** no summary metrics endpoint exists, so the operations page presents audited capability decisions rather than fabricated counts.

The `/administration` page is informational and contains no protected data. It makes no administrative API requests and is not a substitute for a future authenticated, backend-authorized administration interface. No `/settings`, `/admin/users`, or `/admin/audit-logs` routes are claimed.

## Intentionally deferred

Registration/login, JWT storage and renewal, role-aware navigation, account/profile/password settings, server-side logout, session revocation, user or role management, credential issuance, document upload, authenticated document/credential operations, predicate proof generation/submission, private-key administration, audit querying/export, and deployment automation are not implemented. Placeholder and operations pages state these boundaries explicitly and do not fabricate data.

Authentication can be added by introducing a narrowly scoped auth provider and request credential strategy around the existing API client. Client role checks should improve presentation only; Spring Security remains the authorization authority. Access tokens should not be persisted in `localStorage`.

## IntelliJ IDEA

Open the repository root as the existing Maven project. Running `Application.java` serves the last generated assets in `zkdoc-api/src/main/resources/static`. Run Maven `generate-resources` (or `package`) after frontend changes so the pinned Maven frontend toolchain regenerates those assets. For the optional Vite workflow, add `frontend/package.json` to the npm tool window and use a supported local Node.js runtime.
