# Zero Knowledge Document Verification

`Zero Knowledge Document Verification` (`zkdoc-platform`) is a Java 21, Spring Boot multi-module foundation for zero-knowledge document and credential verification. It uses an enterprise-grade cryptographic architecture featuring Pedersen commitments, Bulletproof range proofs, RSASSA-PSS digital signatures, and minimal-disclosure verification capabilities.

## Modules

- `zkdoc-core-crypto` — shared SHA-256 and RSA-PSS primitives.
- `zkdoc-document` — document signing, storage, and verification.
- `zkdoc-credential` — canonical credential encoding and signing.
- `zkdoc-zkp` — Pedersen commitments and Bulletproof predicate verification.
- `zkdoc-keys` — signing-key registry and status policy.
- `zkdoc-api` — the sole Spring Boot entry point, HTTP API, SQLite configuration, and Flyway migrations.

## Runtime baseline

- Java 26 (the project and compiler release are pinned to 26)
- Spring Boot 4.1.1
- SQLite JDBC 3.53.4.0
- Flyway, Spring Framework, Spring Security, and Jackson are supplied at the compatible versions managed by Spring Boot 4.1.1.

There is deliberately no Maven toolchain file and no `JAVA21_HOME` setup. Do not configure Java 21 for this project.

## Local setup in IntelliJ IDEA

1. Open the root `pom.xml` as a Maven project and allow Maven synchronization to finish.
2. In **File → Project Structure → Project**, select the detected OpenJDK 26 SDK (or download/add a Java 26 SDK). Set the language level to 26.
3. In **Settings → Build Tools → Maven**, use IntelliJ IDEA's bundled Maven. No separate Maven installation is necessary.
4. Run Maven `generate-resources` or `package` once after frontend changes. The `zkdoc-api` build installs its pinned Node/npm toolchain, runs `npm ci`, and writes the production React bundle into the API's static resources.
5. Open `zkdoc-api/src/main/java/com/zkdoc/api/Application.java` and run that class. It is the only class with a `main()` method and the only supported application entry point. Open `http://localhost:8080` for the integrated application.

The application starts with SQLite at `./data/zkdoc.db`. Flyway creates or upgrades that file at startup. The checked-in application configuration does not read a `.env` file and does not use PostgreSQL credentials.

Verify the running API:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected response:

```json
{"status":"ok"}
```

## Build and tests

Run Maven goals from IntelliJ's Maven tool window, using the root project:

- **Lifecycle → test** runs the full suite.
- **Lifecycle → package** builds the complete multi-module package.

For a terminal that already has Java 26 and Maven available, the equivalent command is:

```powershell
mvn test
```

The predicate integration suite logs each security case as `CASE <name>: PASS` or `CASE <name>: FAIL (...)`. It covers both accepted predicates (equal-to and above threshold) and rejected proofs, including wrong credentials, altered proofs or thresholds, replay with a different nonce, revoked/expired credentials, invalid signatures, and commitment substitution.

## Documents and public QR verification

`POST /api/documents` accepts a multipart `file` from an `ISSUER`, stores the file bytes, SHA-256 digest, RSA-PSS signature, and generated UUID in SQLite. `GET /api/documents/{id}/verify` verifies the stored copy; `POST /api/documents/{id}/verify-upload` independently hashes a newly uploaded `file`.

`GET /api/documents/{id}/qr` creates a PNG QR image encoding `GET /api/public/verify/{token}`. The token is a separate 256-bit opaque value, not a document UUID. Public verification returns only `signatureValid` and `documentActive`, and it is rate-limited in memory to 30 requests per minute per direct client IP. Set `ZKDOC_PUBLIC_VERIFICATION_BASE_URL` in the process environment to give QR codes a deployed external base URL.

## Credentials and zero-knowledge predicates

`POST /api/credentials` creates an issuer-signed credential and `GET /api/credentials/{id}/verify` verifies it. Credentials use a separate, versioned binary canonical encoder: UTF-8 length-prefixed strings, a 16-byte UUID, attributes sorted by name, and fixed-width timestamps. No entity `toString()`, unspecified map order, JSON ordering, or default charset participates in signing.

For integer attributes, `zkdoc-zkp` creates a Weavechain Bulletproofs Ristretto Pedersen commitment `C = vG + rH`. `r` is a new `SecureRandom` value for every commitment, never derived from `v`; its SHA-256 fingerprint is checked against prior values before storage, and the raw value is never persisted. The issuer signs a canonical attestation binding the commitment bytes to the credential UUID and attribute name.

`POST /api/v2/zkp/credential-predicates/verify` accepts only a proof version, credential ID, attribute name, threshold, verifier nonce, and proof bytes. It obtains the issuer-signed commitment itself, binds the nonce into the Bulletproof transcript, and never accepts the hidden value, blinding factor, or a caller-supplied commitment.

## Security and operational limits

All non-public routes require a Bearer JWT with `ISSUER`, `HOLDER`, `VERIFIER`, or `ADMIN` roles. Issuing documents/credentials and QR generation are issuer-only; predicate verification is verifier-only; `/api/admin/keys` is admin-only. Flyway migrations create a key registry for document, credential, and JWT keys. Active and retired keys verify historic signatures; revoked keys fail verification. Verification attempts are audited without recording sensitive payloads.

See [SECURITY.md](SECURITY.md) for the precise security boundary, including the fact that private keys are environment-injected files rather than HSM-backed and that local SQLite is not a production multi-node datastore.

The Weavechain Bulletproofs dependency requires the JitPack repository for its published `keccakj` metadata; its incompatible unused Caffeine transitive declaration is excluded. Java 26 may emit third-party warnings from SQLite native access and Bulletproofs' older MessagePack dependency. They do not change the tested behavior, but they should be addressed before a production deployment.

## Frontend and public verification

The [`frontend/`](frontend/) application provides the responsive React shell, design system, typed API client, health indicator, and the Phase 4 public verification page at `/verify`. Production assets are built into and served by `zkdoc-api`; the frontend does not change the backend's authorization or cryptographic responsibilities.

The public page accepts the opaque token or the exact verification URL emitted by a document QR code. Camera scanning starts only after an explicit user action, decodes locally, and releases the stream after a scan, user stop, or navigation. The UI renders only the real `{ "signatureValid": boolean, "documentActive": boolean }` response and distinguishes backend-invalid results from failures that prevented verification.

The Phase 5 `/administration` page documents the platform's real operational boundaries without requesting protected data or presenting nonfunctional controls. The backend has no profile, password, user, role, session, audit-read/export, or summary endpoints. Audit events are write-only application metadata, not a tamper-proof ledger. Key registration and status mutation exist under `/api/admin/keys`, but remain backend-only because the frontend has no authenticated ADMIN context and the backend has no key-list/detail operation.

The Phase 6 hardening pass sanitizes shared API errors, improves verification validation focus, prevents closed mobile navigation from remaining keyboard-accessible, manages drawer focus and background interaction, and splits verification and operations routes from the initial JavaScript bundle. Production source maps remain disabled. No additional runtime dependencies or deployment infrastructure were introduced.

With the backend running on port 8080:

```powershell
Set-Location frontend
npm ci
Copy-Item .env.example .env.local
npm run dev
```

Open `http://localhost:5173`. Vite proxies same-origin `/api` requests to the backend because the current Spring configuration does not enable cross-origin browser access. This is an optional development workflow; normal use starts from `Application.java` and opens `http://localhost:8080`.

Authenticated document/credential verification is not exposed because the repository has no frontend authentication provider or configured external JWT issuer. Predicate verification remains restricted to the backend's `VERIFIER` role, and verification history is absent because there is no history-read API. See [`frontend/README.md`](frontend/README.md) for QR requirements, exact result semantics, tests, build integration, and deferred features.
