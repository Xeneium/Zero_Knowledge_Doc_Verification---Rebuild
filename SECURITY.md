# Security model

## What is protected

- Protected API calls require a Bearer JWT whose `kid` resolves to a registered `JWT_SIGNING` public key. The JWT must be a valid RSA-signed token and pass standard time validation.
- The accepted `roles` claim is limited to `ISSUER`, `HOLDER`, `VERIFIER`, and `ADMIN`.
- Only `ISSUER` can issue documents or credentials, or create a document QR code. Other roles receive `403 Forbidden` for those endpoints.
- `VERIFIER` is required for the credential-predicate verification endpoint. `ADMIN` is required to register keys or change a key's status.
- Verification accepts signatures made by **active** and **retired** registered keys. A **revoked** key is excluded before signature verification and therefore fails verification.
- Every completed verification attempt records the authenticated subject (or `anonymous`), timestamp, target identifier when available, event type, and pass/fail result. The audit table does not store request bodies, uploaded files, hashes, signatures, proof bytes, or private keys.

## Intentionally public

`GET /api/health` and `GET /api/public/verify/{token}` are unauthenticated. The latter is rate limited and returns only signature validity and document activity; it does not disclose document files, paths, identities, or hashes.

## Frontend security boundary

- The browser calls only confirmed API routes. The public verification response is runtime-checked before it can produce a trusted result.
- QR contents are length- and structure-validated, are never executed or opened, and are reduced to an opaque backend token before a same-origin request.
- Camera decoding occurs locally. Access requires an explicit action, and streams are stopped after a scan, cancellation, error, or navigation.
- Verification references and results are held only in component memory; the frontend does not place them in URLs, local storage, or session storage.
- Shared HTTP errors expose only status-specific public messages and constrained error codes. Backend messages and exception details are not propagated to the interface.
- Production source maps are disabled. No analytics, telemetry, authentication tokens, private keys, or document contents are included in the browser bundle.
- The frontend has no authentication provider. Protected API workflows are deliberately non-operational, and the informational operations page contains no protected data or privileged controls.

## Important limitations and operator responsibilities

- **Private keys are environment-injected files, not HSM-backed.** The registry contains public-key metadata and an environment-variable name for a private-key file; it never accepts or returns private-key bytes.
- Current document and credential signing keys are generated in-process at startup by the Phase 1/3 services. They are not yet wired through the key registry and cannot survive a restart. Do not treat this local-development behaviour as production key management.
- JWT issuance is deliberately outside this application. An operator must provision the JWT public key in the registry, securely operate the issuer, and use a unique `kid` for key rotation.
- SQLite is the project's only configured database. It is not encrypted at rest by this application and is suitable only for a carefully secured single-node deployment with restrictive file permissions, controlled backups, and an explicit retention policy.
- JWT issuer and audience restrictions are not configured by default; deployers must add those constraints for their identity provider. HTTPS/TLS termination, secret-file permissions, database backups, log retention, and rate-limit storage are deployment responsibilities.
- The audit log is operational metadata, not a tamper-evident ledger. Restrict database access and retention according to your policy.
