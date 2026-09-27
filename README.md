# zkdoc-platform

A Java 21, Spring Boot 3.3+ multi-module foundation for zero-knowledge document verification.

## Modules

- `zkdoc-core-crypto` — shared cryptographic primitives.
- `zkdoc-document` — document-domain services.
- `zkdoc-credential` — credential-domain services.
- `zkdoc-zkp` — zero-knowledge proof services.
- `zkdoc-keys` — key-management services.
- `zkdoc-api` — Spring Boot HTTP API, database configuration, and Flyway migrations.

## Prerequisites

- A standalone JDK 21 installation (not an IDE runtime)
- Maven 3.9+
- No database server or Docker setup is required; SQLite runs from a local file.

Set `JAVA21_HOME` to the standalone JDK 21 installation and make Maven use the checked-in toolchain definition. In PowerShell:

```powershell
$env:JAVA21_HOME = 'C:\Program Files\Java\jdk-21'
mvn -t .mvn/toolchains.xml verify
```

The build requires a JDK toolchain whose version is exactly 21. Adjust the example path to your installation.

## Run locally

The application uses SQLite at `./data/zkdoc.db`. Flyway creates the database file and runs migrations at startup. No PostgreSQL credentials, Docker container, or database server is used.

Build and start the API:

```powershell
mvn -t .mvn/toolchains.xml -pl zkdoc-api -am spring-boot:run
```

Flyway runs automatically on application startup and creates the SQLite database file when needed.

Verify the service:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected response:

```json
{"status":"ok"}
```

## Document upload and verification

Upload a document as multipart form data. The API stores the file bytes, SHA-256 digest, RSA-PSS signature, and generated UUID in SQLite.

```powershell
curl.exe -H "Authorization: Bearer <issuer-jwt>" -F "file=@C:\path\to\document.pdf" http://localhost:8080/api/documents
```

Use the returned `id` for verification. Mode A hashes the server's stored bytes. Mode B hashes the newly uploaded `file` and compares that digest with the stored digest.

```powershell
Invoke-RestMethod -Headers @{ Authorization = 'Bearer <holder-or-verifier-jwt>' } http://localhost:8080/api/documents/{id}/verify
curl.exe -H "Authorization: Bearer <holder-or-verifier-jwt>" -X POST -F "file=@C:\path\to\document.pdf" http://localhost:8080/api/documents/{id}/verify-upload
```

## Public QR verification

`GET /api/documents/{id}/qr` returns a PNG QR image that encodes the public verification URL. Configure the external base URL with `ZKDOC_PUBLIC_VERIFICATION_BASE_URL` before deployment.

The QR URL is `GET /api/public/verify/{token}`, where `token` is a separate 256-bit opaque value rather than the database document UUID. Its response contains only `signatureValid` and `documentActive`. The endpoint is limited in-memory to 30 requests per minute per direct client IP. When deploying behind a reverse proxy, preserve the true client IP at the proxy and configure the application/proxy accordingly; this implementation intentionally does not trust user-supplied forwarding headers.

## Credentials

Create a credential with `POST /api/credentials`; provide `issuerId`, a UUID `credentialId`, an `attributes` array of `{ "name", "value" }` objects, ISO-8601 `issuedAt` and `expiresAt` values, and an `ACTIVE`, `REVOKED`, or `EXPIRED` status. `GET /api/credentials/{id}/verify` verifies the stored canonical payload and RSA-PSS signature.

Credential signing always uses the versioned binary canonical encoder: UTF-8 length-prefixed strings, a 16-byte UUID, attributes sorted by name, and fixed-width timestamp fields. It does not serialize the entity using JSON, maps, platform-default charsets, or `toString()`.

## Attribute commitments

`zkdoc-zkp` uses Weavechain Bulletproofs Ristretto Pedersen commitments for integer credential attributes. Each commitment is `C = vG + rH`, where `r` comes from `SecureRandom`, is never derived from `v`, and is rejected if its stored SHA-256 fingerprint has appeared before. The raw blinding factor is never persisted. The issuer signs a canonical attestation binding the commitment bytes to its credential UUID and attribute name. Commitments and attestations are stored in SQLite by Flyway migration V4.

## Non-replayable credential predicates

`POST /api/v2/zkp/credential-predicates/verify` verifies a Bulletproofs `attribute >= threshold` predicate. Its request contains only `proofVersion`, `credentialId`, `attributeName`, `threshold`, base64 `nonce`, and base64 `proofBytes`; it never accepts a value, blinding factor, or caller-provided commitment. The server retrieves the stored issuer-signed commitment itself and folds the verifier nonce into the Bulletproofs transcript before verifying.

Run the predicate security integration suite with `mvn -t .mvn/toolchains.xml -pl zkdoc-api -am test`. Each named case emits `CASE <name>: PASS` or `CASE <name>: FAIL (...)` in the Surefire output.

## API security and key registry

All routes except health and the public QR verification route require a Bearer JWT. JWTs use a registered `JWT_SIGNING` key selected by the token's `kid`, and the `roles` claim must contain one or more of `ISSUER`, `HOLDER`, `VERIFIER`, or `ADMIN`. Issuing documents/credentials and generating QR codes are `ISSUER`-only; predicate verification is `VERIFIER`-only; `/api/admin/keys` is `ADMIN`-only.

Flyway migrations V5 and V6 add the key registry and verification audit table. Registry rows support `DOCUMENT_SIGNING`, `CREDENTIAL_SIGNING`, and `JWT_SIGNING` purposes, plus `ACTIVE`, `RETIRED`, and `REVOKED` statuses. Active and retired keys can verify historic signatures; revoked keys cannot. See [SECURITY.md](SECURITY.md) for operational limits, especially the private-key and SQLite caveats.

To reset local development data, stop the application and delete `data/zkdoc.db`.
