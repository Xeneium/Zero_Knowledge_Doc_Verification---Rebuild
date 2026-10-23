# Zero-Knowledge Document Verification Platform
## Production Readiness Audit — 2026-10-22

## 1. Scope and evidence boundary

This is an implementation review of the current checkout, not a review of the claims in the supplied project profile. The profile was used as operational context and its instructions were not treated as authority over the audit.

Evidence inspected:

- Current Git state, Maven reactor, Java modules, frontend source and package metadata.
- Spring Security configuration, JWT filter, production validator, upload/storage configuration, Docker/Compose, CI, and PostgreSQL migrations.
- Existing architecture, security, deployment, frontend-rebuild, and audit documents.
- A fresh backend test attempt and fresh frontend verification attempt on 2026-10-22.

Important boundary conditions:

- The checkout is on `main`, while the supplied profile identifies `feat/readiness`.
- The working tree is substantially modified and contains many untracked files. Findings describe the working tree unless explicitly marked as documentation-only.
- No UI screenshots or recordings are committed. The UI section is therefore a source-level and design-document review, not a pixel-accurate visual acceptance review.
- No production cloud account, reverse proxy, external IdP, managed PostgreSQL instance, backup restore, or live multi-instance deployment was available for verification.

## 2. Executive decision

**Decision: Not production-ready for public deployment. Suitable for controlled academic demonstration and a tightly bounded internal pilot after P0/P1 fixes.**

The core architecture is appropriately simple for the stated 5–50 concurrent-user and 1,000–10,000-request/day envelope: a modular Spring Boot monolith, React SPA, PostgreSQL in production, and filesystem/object-style artifact storage. The cryptographic boundaries and security intent are stronger than typical student projects.

The release gate is not the cryptographic design. It is operational correctness: the documented green test state is not reproducible, production defaults are inconsistent, database migrations relax referential integrity to compensate for incompatible stores, the frontend toolchain is not locally executable, and the production edge/backup/restore path is only described rather than demonstrated.

### Strongest parts

- Clear module separation between core crypto, documents, credentials, ZKP, keys, and API.
- Stateless JWT authentication with explicit role checks and object-authorization services.
- Fail-fast production validation for demo seeding, database passwords, storage encryption, and development key providers.
- Good container baseline: non-root runtime, dropped capabilities, read-only root filesystem, health probes, internal backend network.
- Defensive headers and CSP in the Spring Security chain.
- Rate limiting and bounded concurrency around expensive verification/proof operations.
- CI includes pinned GitHub Actions, secret scanning, SCA, SBOM generation, CodeQL, Dockerfile linting, and container scanning.

### Most dangerous issues

1. PostgreSQL profile sets demo seeding to `true` by default (`application.yml:93-95`). The runtime validator blocks only `prod`/`production`, while Compose uses `postgres`; this creates a profile-mismatch risk.
2. Fresh Maven execution fails 9 storage tests before reaching the API module. The immediate cause is Windows sandbox temp-file move denial, but there is no reproducible evidence that the claimed 463-test result applies to this checkout.
3. PostgreSQL migrations drop multiple foreign keys (`V11`, `V12`) and use compatibility triggers/default values. This trades startup compatibility for data-integrity enforcement and is unsafe as a long-term production model.
4. Artifact storage is local filesystem volume. It is acceptable for one instance, but it is not a durable, independently backed-up, multi-instance storage strategy until restore drills and lifecycle reconciliation are proven.
5. The frontend currently sends bearer tokens from JavaScript (`frontend/src/api/client.ts:21-23`). This can be acceptable for a controlled token-based SPA, but it increases XSS blast radius and has no visible refresh-token/rotation strategy in the reviewed client path.
6. The production Compose file exposes the API directly on the host (`docker-compose.yml:54-55`) and contains no TLS edge. The supplied profile correctly describes a reverse proxy requirement, but it is not implemented in the repository.

## 3. Scores

| Area | Score | Rationale |
|---|---:|---|
| Architecture | 76/100 | Good modular monolith and domain boundaries; persistence compatibility layers and API/domain coupling reduce confidence. |
| UI/UX | 68/100 | Structured routes, role workspaces, error boundary, and accessibility intent; no visual evidence, route duplication, and unverified responsive behavior prevent a higher score. |
| Security | 67/100 | Strong baseline controls and fail-fast checks; production-default inconsistency, bearer-token exposure, local key custody, and incomplete edge deployment remain material. |
| Performance | 70/100 | Bounded crypto concurrency and sensible JVM/container limits; in-memory limits, filesystem storage, no proven caching, and expensive ZKP operations limit scale. |
| Maintainability | 64/100 | Tests and documentation are substantial, but working-tree drift, compatibility migrations, duplicated client/API patterns, and unverifiable toolchain state create maintenance risk. |
| Scalability | 58/100 | Fine for the stated pilot envelope on one instance; not yet ready for horizontal scaling because rate limits, artifact storage, audit sequencing, and key state are local/process-sensitive. |
| Production readiness | 54/100 | Good engineering intent, but release evidence and operational controls are incomplete; public production should remain blocked. |

## 4. Findings by priority

### P0 — release blockers

#### P0-1: Production profile has unsafe demo-seeding default

**Evidence:** `zkdoc-api/src/main/resources/application.yml:93-95` sets `ZKDOC_DEMO_SEED_ENABLED:true` for `postgres | prod | production`. The validator at `ProductionSecurityValidator.java:72-83` rejects only `prod` and `production`. Compose selects `postgres` at `docker-compose.yml:44`.

**Problem and impact:** A container using the documented Compose profile can start with demo data enabled, or can avoid the fail-fast validator because the active profile is `postgres`. This risks synthetic users, credentials, or keys being present in a real database.

**Fix:** Make the production-safe default false in every non-demo profile. Separate `demo` from `postgres` rather than using an OR-style profile block. Make the validator reject `postgres` whenever `ZKDOC_DEMO_SEED_ENABLED=true`. Add an application-context test that boots each profile and asserts the effective value.

**Tradeoff:** Demo startup requires explicit configuration, but the safety benefit is decisive. Complexity: low; effort: 2–4 hours including tests.

#### P0-2: Test and build evidence is not reproducible

**Evidence:** Fresh `./mvnw.cmd test` fails in `zkdoc-document` with 9 `AccessDeniedException` errors while moving temp files into test directories. Frontend `npm run typecheck`, `lint`, and `test:run` fail because `tsc`, `eslint`, and `vitest` are unavailable from the installed `node_modules`.

**Problem and impact:** The stated 462/463 or 463-test result cannot be accepted as current release evidence. A release may contain untested frontend or storage behavior.

**Fix:** Reproduce backend tests in a clean Linux CI runner and locally outside the restricted temp location; make storage tests use `Files.copy`/atomic replace with a fallback for Windows filesystem semantics, or document and isolate the sandbox-specific limitation. Run `npm ci` from the lockfile, then typecheck, lint, test, and build. Record exact commit SHA, tool versions, counts, skipped tests, and failures.

**Tradeoff:** A Windows-specific test adaptation adds code paths, but it is preferable to silently declaring green tests that only pass in one environment. Complexity: medium; effort: 0.5–1.5 days.

#### P0-3: Production deployment has no implemented TLS edge

**Evidence:** Compose maps API port directly to the host (`docker-compose.yml:54-55`) and contains no Nginx/Caddy/ALB configuration. The profile describes TLS as a requirement but not as a checked artifact.

**Impact:** Direct exposure can bypass TLS, HSTS, secure-cookie policy, request-size controls, and trusted-proxy handling. Public verification tokens and bearer tokens can be intercepted if the operator exposes port 8080 directly.

**Fix:** Either document Compose as internal-only and provide a checked-in reverse-proxy configuration, or deploy behind a managed TLS edge. Add a smoke test asserting HTTPS, HSTS, CSP, request-size limits, forwarded-client-IP behavior, and that 8080 is not internet reachable.

**Tradeoff:** A proxy adds one operational component, but TLS termination is a mandatory boundary, not premature architecture. Complexity: medium; effort: 1–2 days.

### P1 — high priority

#### P1-1: PostgreSQL migrations remove referential integrity

**Evidence:** `V11:8-14` drops foreign keys for artifacts, credential keys, and commitments. `V12:8-11` drops foreign keys for capabilities, commitments, and holder credentials. Compatibility triggers at `V11:17-67` and `V12:14-41` synthesize or mirror columns.

**Problem:** The database is being made compatible with multiple persistence models by removing constraints. Orphaned credentials, capabilities, documents, and commitments become possible. The trigger default `sha256-synthetic-salt` is especially concerning because it produces a sentinel value in a security-sensitive column.

**Impact:** Silent data corruption, broken revocation/ownership relationships, difficult incident reconstruction, and incorrect verification results.

**Fix/refactoring:** Choose one canonical schema. Migrate stores to it in a staged migration: add canonical columns, backfill and validate, dual-write temporarily, compare counts/digests, switch reads, then remove legacy columns. Re-add foreign keys with `NOT VALID`, repair violations, then validate. Replace sentinel defaults with explicit `NULL` plus application-level validation or a proper generated value.

**Tradeoff:** The migration is more work and may require a maintenance window, but preserves correctness. Complexity: high; effort: 3–7 days for a careful solo implementation.

#### P1-2: Artifact storage is not production-durable across instances

**Evidence:** Compose uses a local named volume (`docker-compose.yml:56-57`, `103-107`), while the profile targets multi-node/containerized deployment. The API is otherwise stateless, but artifact bytes remain node-local.

**Impact:** A second API instance cannot necessarily read an artifact; volume loss can separate database metadata from the document payload; backups may be incomplete unless the artifact volume and database snapshot are coordinated.

**Fix:** For the pilot, explicitly support one API replica and enforce this operationally. Before horizontal scaling, move artifacts to S3-compatible object storage with server-side encryption, versioning, lifecycle rules, and content-addressed keys. Store only metadata and immutable digest in PostgreSQL. Add a restore test that proves database plus artifacts recover as one unit.

**Tradeoff:** Object storage adds credentials and an external dependency. At the stated scale, it is still simpler operationally than inventing a distributed filesystem. Complexity: medium; effort: 2–4 days.

#### P1-3: JWT handling increases client-side token exposure

**Evidence:** `frontend/src/api/client.ts:15,21-23,98-100` reads a bearer token from the client auth store and places it in an Authorization header. The profile describes stateless JWTs and external OIDC, but no refresh rotation or token lifetime contract is visible in this client path.

**Impact:** Any successful XSS or compromised dependency can read the in-memory token for its lifetime. Long-lived demo tokens make this worse.

**Fix:** For production, use an OIDC authorization-code flow with PKCE, short-lived access tokens, rotating refresh tokens handled by the IdP, and a documented token lifetime. Prefer a backend-for-frontend with Secure/HttpOnly/SameSite cookies if the deployment model permits it. Keep demo tokens isolated behind a demo-only build/profile.

**Tradeoff:** BFF/cookie sessions add backend session concerns; PKCE-only SPA keeps the architecture simpler but retains XSS exposure. Choose based on the real IdP and threat model. Complexity: medium; effort: 2–5 days.

#### P1-4: Local signing-key custody is not adequate for public production

**Evidence:** The supplied profile states filesystem PEM custody. `ProductionSecurityValidator` rejects `DevelopmentSigningKeyProvider`, but it does not make filesystem custody hardware-backed or operationally managed.

**Impact:** Host compromise or volume backup compromise can allow credential forgery until key rotation/revocation propagates.

**Fix:** Use a managed KMS/HSM if available; under a zero-cost constraint, use a dedicated secret mounted at runtime, strict filesystem permissions, non-exportable operational process where possible, documented rotation, dual-key overlap, and a tested compromise response. Never commit private keys or include them in demo bundles.

**Tradeoff:** KMS costs money and adds vendor coupling. For the academic pilot, hardened secret-mounted PEM plus rotation is acceptable; public issuance should be gated on a stronger custody decision. Complexity: medium.

#### P1-5: Direct Compose secrets and encryption configuration are permissive

**Evidence:** `docker-compose.yml:50-52` defaults encryption enabled but permits an empty encryption key; backup keys at `:100-101` also default empty. Application defaults include an empty master key (`application.yml:50-52`). The production validator catches some cases only for `prod`/`production`, not necessarily `postgres`.

**Fix:** Use required-variable syntax for encryption and backup keys, fail closed before startup, and make profile validation cover all production deployment profiles. Remove secrets from `.env` examples except placeholders. Add a startup test that rejects empty key material.

#### P1-6: CI does not provide a complete production acceptance gate

**Evidence:** CI builds backend and frontend separately; Docker build runs with `-DskipTests` (`Dockerfile:36-37`), and CI does not show a Compose smoke test, migration test from an empty PostgreSQL database, backup restore, or authenticated end-to-end browser test.

**Fix:** Add a post-build acceptance job: start PostgreSQL and API with explicit production-safe env, run Flyway from empty database, exercise auth/RBAC/upload/verify/revoke, verify headers and health, run backup/restore, and publish evidence. Keep the existing fast jobs for pull requests.

### P2 — medium priority

#### P2-1: API authorization is partly path-based and partly method-based

**Evidence:** `SecurityConfiguration.java:94-103` mixes route matchers with broad authenticated fallbacks. `/api/documents/**` and `/api/credentials/**` allow all four roles at the HTTP layer, relying on object-level services for ownership.

**Risk:** A future endpoint added under an existing path can inherit broader access than intended.

**Fix:** Use explicit controller/service authorization annotations for sensitive operations, centralize permission constants, and add a matrix test for every route, method, role, ownership case, and revoked state.

#### P2-2: CSRF is globally disabled without a documented browser trust boundary

**Evidence:** `SecurityConfiguration.java:27` disables CSRF globally. Bearer-only APIs generally do not require CSRF, but a future cookie/BFF mode would.

**Fix:** Document the invariant that authentication is Authorization-header bearer-only. If cookies are introduced, enable CSRF protection for state-changing browser requests and add origin checks.

#### P2-3: In-memory rate limits do not scale horizontally

**Evidence:** The rate-limit implementation is Bucket4j process-local and uses bounded concurrency for ZKP operations.

**Impact:** Limits reset per instance and an attacker can distribute load across replicas. It is acceptable for one pilot instance but not a multi-replica guarantee.

**Fix:** Keep local concurrency guards for CPU protection, and enforce public/API quotas at the reverse proxy or a shared Redis-backed limiter when replicas are introduced.

#### P2-4: Upload limits are inconsistent with the operational profile

**Evidence:** `application.yml:8-15` allows 25 MB, while the profile states 10 MB. The server, client, documentation, and reverse proxy must agree.

**Fix:** Define one named limit, expose it to the frontend, enforce it at edge and server, and test oversize requests. Do not rely only on client preflight.

#### P2-5: Database migrations are divergent between SQLite and PostgreSQL

**Evidence:** Separate migration trees and multiple compatibility migrations exist. The profile itself reports different version ranges.

**Impact:** Local tests can pass against SQLite while production semantics differ. This is a high probability source of deployment-only defects.

**Fix:** Use PostgreSQL for all integration and release tests. Keep SQLite only as an explicitly unsupported demo convenience, with a documented feature subset.

#### P2-6: Duplicate client request implementations increase drift

**Evidence:** `apiRequest` and `apiBlobRequest` duplicate authentication, correlation, stale-session, error parsing, and 401 handling (`frontend/src/api/client.ts:9-155`).

**Fix:** Factor a single request pipeline with a response decoder strategy. Add tests for 401, 403, non-JSON errors, aborted requests, and stale sessions.

#### P2-7: Frontend dependency/tooling state is not aligned with the profile

**Evidence:** `frontend/package.json` declares React 19.3, React Router 8.4, Vite 8.3, TypeScript 6, and Node >=22.12, while the profile describes React 18 / TypeScript 5.7. Local executable resolution failed.

**Fix:** Treat `package-lock.json` plus CI as the source of truth. Update profile/docs, pin exact versions for release builds where practical, and add a clean-room `npm ci` check.

### P3 — lower priority

- Add visual regression screenshots at the six primary routes and mobile breakpoints.
- Add structured JSON logs with explicit redaction tests for JWTs, document content, credentials, and proof witnesses.
- Add OpenTelemetry traces for upload, issuance, proof generation, verification, and audit append.
- Add pagination and server-side filtering contracts to all admin/history lists.
- Add dependency update automation with a review policy for crypto, Spring, React, and parser libraries.
- Replace duplicated route aliases (`/verify`, `/verification`, `/predicate-verification`, `/verify/:token`) with canonical routes plus redirects and analytics.

## 5. Architecture review

### Assessment

The modular monolith is the right architecture for this product and scale. The module list is understandable, and keeping core crypto free of Spring dependencies is a good boundary. Do not split into microservices, Kubernetes, or event-driven infrastructure at the current traffic and team size.

The main architectural weakness is not module count; it is that persistence models are being adapted after the fact through compatibility columns, triggers, and dropped foreign keys. This makes the system look decoupled while moving correctness into implicit database behavior.

### Recommended target shape

Keep one deployable API with these internal boundaries:

1. API adapters: HTTP validation, authentication context, DTOs, error mapping.
2. Application services: use-case transactions and authorization decisions.
3. Domain modules: document, credential, holder, issuer, verification, key lifecycle.
4. Ports: artifact store, key store, credential store, audit store, clock.
5. Adapters: PostgreSQL repositories, object storage, KMS/secret provider, metrics.

The current code is close to this shape, but the API module appears to own too many cross-domain wiring and compatibility concerns. Refactor incrementally around use cases rather than renaming every package.

## 6. UI/UX audit

### Evidence and limitation

The code has role-specific workspaces, route guards, an error boundary, loading status semantics, a public verifier, issuer workflow, holder wallet, and admin console. That is a good product skeleton. No screenshots or recordings are present, so color, spacing, visual hierarchy, and responsive behavior cannot be conclusively scored.

### Specific issues and redesign

| Issue | Impact | Recommended implementation | Priority |
|---|---|---|---:|
| Four verification route aliases | Users and analytics get fragmented URLs; navigation feels product-incomplete | Choose `/verify` as canonical, redirect aliases, preserve token deep links | P1 |
| Role workspaces are route-protected but shared navigation must be audited | Users can see destinations they cannot use unless navigation filters by role | Derive nav items from a typed permission map; show disabled/explanation only when useful | P1 |
| Crypto workflows are inherently high latency | A 70–85 ms proof-generation benchmark can still feel opaque during retries or mobile CPU pressure | Use explicit step states: validating, generating, confirming, failed; disable duplicate submit and expose correlation/reference ID | P1 |
| Verification results can be high consequence | A binary green check can overstate what was proven | Show separate badges for signature validity, document digest, credential status, predicate result, issuer trust, and expiry | P1 |
| Upload forms risk unclear failure recovery | Users may not know whether a file was rejected for type, size, digest, or authorization | Inline field-level errors plus a stable summary; preserve selected metadata but never retain sensitive file data after rejection | P1 |
| Admin/audit screens are likely information-dense | Long event rows and hashes are hard to scan | Use a table with fixed columns, monospace hash excerpts, copy action, detail drawer, filters, and pagination | P2 |
| No committed visual regression evidence | Responsive regressions can pass unit tests | Add Playwright screenshots at 360/768/1440 widths and keyboard-only checks | P2 |
| Dark-mode/accessibility claims are unverified | Enterprise users need readable status and keyboard operation | Test WCAG contrast, focus visibility, reduced motion, screen reader labels, QR camera fallback, and 200% zoom | P2 |

Design guidance: use a restrained token system for surface, border, text, success/warning/error, spacing, radius, and typography; keep motion limited to state transitions; avoid using color alone for cryptographic status; provide copyable IDs and hashes; and make every destructive action confirmable with an explanation of irreversibility.

## 7. Security audit

### Positive controls

- Stateless security context and explicit JWT validation.
- Role restrictions for issuer, holder, verifier, and admin paths.
- CSP, frame denial, MIME sniffing protection, and referrer policy.
- Correlation IDs and security audit events.
- Input size limits and document format validation.
- Non-root, read-only runtime container with dropped capabilities.
- Secret scanning, SBOM, SCA, CodeQL, and pinned action SHAs.

### Missing or conditional controls

- External OIDC issuer/JWKS rotation and audience/issuer contract are not verified against a real IdP.
- Key custody is filesystem-based according to the profile.
- CSRF policy is safe only while bearer-header authentication remains invariant.
- Rate limiting is local-process state.
- TLS/HSTS are deployment requirements, not repository-enforced behavior.
- Security log redaction and retention were not proven by a live inspection.
- File malware scanning and decompression-bomb controls are not evidenced. Magic-byte checks are not a malware defense.

Defensive implementation priorities are: fail-closed production configuration; explicit OIDC validation; object-level authorization matrix tests; encrypted durable artifact storage; edge TLS and trusted proxy configuration; rate limits for upload, issuance, proof generation, and public verification; and a tested key-compromise runbook.

## 8. Performance and scalability

The stated Bulletproof generation/verification numbers are plausible for a pilot but must be treated as machine-specific until reproduced in CI or a representative deployment. The key capacity constraint is CPU, not database I/O: 13.7 proof generations/sec and 83 verification ops/sec are theoretical isolated rates, not end-to-end capacity.

For a 2 vCPU, 1 GB API container, plan conservatively for:

- One or a few concurrent proof generations, with the existing bounded concurrency guard.
- Public verification quotas that prevent CPU starvation.
- Asynchronous issuance/proof jobs only if user-visible latency or volume grows; do not add a queue now.
- PostgreSQL indexes on token/status/expiry/owner and bounded result pages.
- No unbounded document reads into memory; stream uploads and downloads.

The next useful measurement is a k6/Gatling or JMeter scenario against Compose: 50 concurrent users, mixed public verification, authenticated reads, 10 MB uploads, and a small percentage of ZKP operations, with p50/p95/p99, CPU, heap, GC, DB pool saturation, and filesystem latency.

## 9. Database review

PostgreSQL is the correct production choice. The schema has useful indexes and explicit lifecycle tables. The principal issue is data integrity: dropping foreign keys and using dual columns/triggers makes the database less authoritative.

Required production database controls:

- One canonical ID and digest column per entity.
- Foreign keys reintroduced after data cleanup.
- Explicit uniqueness for issuer/document/credential relationships and capability tokens.
- Transaction boundaries covering document metadata, artifact metadata, credential issuance, commitment creation, and audit append.
- A clear concurrency policy for revocation and issuance, including idempotency keys.
- Database role with only required DDL/DML permissions; migrations run by a separate deployment identity.
- Point-in-time recovery and restore drill documented with RPO/RTO.
- Audit retention and archival policy; the hash chain is tamper-evident, not automatically immutable against a database administrator.

Multi-tenant readiness is currently partial. `owner_id` exists, but tenant identity, tenant-scoped uniqueness, and consistent query predicates need a first-class `tenant_id`/organization boundary before serving multiple institutions.

## 10. DevOps and operations

### Current strengths

The CI pipeline is unusually mature for the project size: pinned actions, separate backend/frontend checks, secret scanning, SCA, SBOM, CodeQL, Dockerfile linting, and Trivy. The container runtime is well hardened.

### Required operational additions

- Add a production environment template with all required variables marked as required.
- Make the Compose deployment internal-only unless paired with a checked-in TLS edge.
- Add immutable image tags by commit SHA and a rollback procedure.
- Add smoke tests after deploy and a database migration compatibility check before rollout.
- Add backup encryption-key management and restore verification; a backup script alone is not a recovery strategy.
- Add central error tracking and metrics dashboards for request rate, 4xx/5xx, auth failures, upload rejection, proof latency, rate-limit blocks, DB pool usage, and artifact failures.
- Define alert thresholds and ownership, even for a solo operator.
- Keep separate demo, local, staging, and production profiles; do not overload `postgres` as both a generic and production profile.

## 11. Implementation roadmap

### Phase 1 — Critical fixes (1–3 days)

**Goals:** prevent unsafe startup and establish reproducible evidence.

Tasks: fix demo seeding defaults/profile validation; require encryption and backup keys; resolve or isolate Windows storage-test failure; install/verify frontend dependencies from lockfile; record exact test results; ensure secrets are absent from committed demo artifacts.

Dependencies: none.

Risk: low to medium.

Outcome: fail-closed startup and a trustworthy green/red release report.

### Phase 2 — Stability improvements (3–7 days)

**Goals:** make PostgreSQL the authoritative integration target and remove silent compatibility behavior.

Tasks: inventory schema duplicates; write backfill/validation migration; restore foreign keys; remove synthetic defaults; add route/role/ownership matrix tests; add idempotency for upload, issuance, revoke, and capability creation; add pagination contracts.

Dependencies: Phase 1 and a disposable PostgreSQL database.

Risk: medium to high because migrations touch existing data.

Outcome: predictable data integrity and safer refactoring.

### Phase 3 — UX modernization (3–5 days)

**Goals:** make verification and issuance understandable and accessible.

Tasks: canonicalize routes; role-aware navigation; stepwise crypto progress; evidence-separated verification result cards; upload error recovery; responsive and keyboard testing; visual regression baseline.

Dependencies: stable API contracts from Phase 2.

Risk: medium.

Outcome: less user confusion and a credible enterprise workflow without a framework rewrite.

### Phase 4 — Performance optimization (2–4 days)

**Goals:** measure the real pilot envelope and protect expensive operations.

Tasks: load-test mixed workflows; tune Hikari and bounded crypto concurrency; verify streaming uploads; add query plans for history/admin queries; add shared edge/API quotas only if replicas are introduced; remove duplicate frontend request logic.

Dependencies: stable deployment and representative fixtures.

Risk: low to medium.

Outcome: evidence-backed limits and predictable p95 behavior.

### Phase 5 — Production hardening (3–7 days)

**Goals:** close operational security gaps.

Tasks: deploy behind TLS; integrate real OIDC; choose signing-key custody; move artifacts to encrypted object storage or explicitly enforce single-instance operation; implement backup/restore drills; add error tracking, dashboards, alerts, and rollback runbook.

Dependencies: budget decision and target host/IdP.

Risk: medium.

Outcome: controlled internal pilot or small public deployment with known RPO/RTO and incident procedures.

### Phase 6 — Scale preparation (only when justified)

**Trigger:** sustained multi-instance demand, queueing, or artifact volume beyond the single-host envelope.

Tasks: object storage, shared rate limiting, horizontal API replicas, background jobs for large documents, read-heavy query tuning, tenant isolation, and managed PostgreSQL. Reassess architecture before adding each component.

Dependencies: measured demand, not hypothetical scale.

Risk: medium to high.

Outcome: incremental scale without premature microservices or Kubernetes.

## 12. Release checklist

Do not mark public production ready until all of the following are true:

- [ ] All backend and frontend checks pass from a clean checkout at the release commit.
- [ ] Production profiles fail startup when demo seeding, default passwords, empty encryption keys, or development key providers are active.
- [ ] TLS edge is deployed and verified; API is not directly exposed.
- [ ] Real OIDC issuer, audience, JWKS rotation, role mapping, and token expiry are tested.
- [ ] Object-level authorization matrix passes for every sensitive resource.
- [ ] PostgreSQL migrations preserve foreign keys and pass from an empty database plus representative upgraded data.
- [ ] Artifact backup and database backup restore together in a documented drill.
- [ ] Public verification, upload, issuance, proof generation, and admin endpoints have rate limits and monitoring.
- [ ] Logs and audit metadata are demonstrably free of bearer tokens, raw document contents, witnesses, and private keys.
- [ ] A rollback and key-compromise response runbook has been exercised.

**Final recommendation:** release the current system as an academic/demo build or controlled single-instance staging environment only. Complete P0 items immediately, then address PostgreSQL integrity, durable artifacts, real edge security, and reproducible CI evidence before accepting public production traffic.
