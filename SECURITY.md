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

## Important limitations and operator responsibilities

- **Private keys are environment-injected files, not HSM-backed.** The registry contains public-key metadata and an environment-variable name for a private-key file; it never accepts or returns private-key bytes.
- Current document and credential signing keys are generated in-process at startup by the Phase 1/3 services. They are not yet wired through the key registry and cannot survive a restart. Do not treat this local-development behaviour as production key management.
- JWT issuance is deliberately outside this application. An operator must provision the JWT public key in the registry, securely operate the issuer, and use a unique `kid` for key rotation.
- SQLite is local development storage. It is not encrypted at rest by this application, does not provide production multi-user access control, and should be replaced with an operationally secured database before deployment.
- JWT issuer and audience restrictions are not configured by default; deployers must add those constraints for their identity provider. HTTPS/TLS termination, secret-file permissions, database backups, log retention, and rate-limit storage are deployment responsibilities.
- The audit log is operational metadata, not a tamper-evident ledger. Restrict database access and retention according to your policy.
