# Architecture & Project Analysis

> **Zero-Knowledge Document Verification Platform**
> Java 26 · Spring Boot 4.1.1 · SQLite · Pedersen Commitments · Bulletproofs

---

## Table of Contents

- [High-Level Architecture](#high-level-architecture)
- [Module Dependency Graph](#module-dependency-graph)
- [Module Breakdown](#module-breakdown)
  - [zkdoc-platform (Root POM)](#zkdoc-platform-root-pom)
  - [zkdoc-core-crypto](#zkdoc-core-crypto)
  - [zkdoc-document](#zkdoc-document)
  - [zkdoc-credential](#zkdoc-credential)
  - [zkdoc-zkp](#zkdoc-zkp)
  - [zkdoc-keys](#zkdoc-keys)
  - [zkdoc-api](#zkdoc-api)
- [Database Schema (Flyway Migrations)](#database-schema-flyway-migrations)
- [Security Architecture](#security-architecture)
- [Zero-Knowledge Proof Flow](#zero-knowledge-proof-flow)
- [API Endpoints Summary](#api-endpoints-summary)
- [Test Coverage Matrix](#test-coverage-matrix)
- [Design Patterns Used](#design-patterns-used)
- [Production Readiness Notes](#production-readiness-notes)

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                         zkdoc-api                                  │
│  ┌──────────┐ ┌─────────────┐ ┌──────────┐ ┌───────────────────┐  │
│  │Controller│ │  Security   │ │  Audit   │ │  Rate Limiting    │  │
│  │  Layer   │ │ JWT + RBAC  │ │ Service  │ │  (Bucket4j)       │  │
│  └────┬─────┘ └──────┬──────┘ └────┬─────┘ └────────┬──────────┘  │
│       │               │             │                │             │
├───────┴───────────────┴─────────────┴────────────────┴─────────────┤
│                        Service Layer                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  ┌───────────┐  │
│  │zkdoc-document│  │zkdoc-cred.   │  │ zkdoc-zkp│  │zkdoc-keys │  │
│  │  Upload      │  │  Canonical   │  │ Pedersen  │  │ Registry  │  │
│  │  Hash+Sign   │  │  Encode+Sign │  │ Bulletprf │  │ Lifecycle │  │
│  └──────┬───────┘  └──────┬───────┘  └─────┬────┘  └─────┬─────┘  │
│         │                 │                │             │         │
├─────────┴─────────────────┴────────────────┴─────────────┴─────────┤
│                     zkdoc-core-crypto                              │
│                   (Shared SHA-256, RSA-PSS)                        │
├────────────────────────────────────────────────────────────────────┤
│                     SQLite (data/zkdoc.db)                         │
│                   Flyway Migrations V1–V6                          │
└────────────────────────────────────────────────────────────────────┘
```

---

## Module Dependency Graph

```
zkdoc-api
├── zkdoc-document
│   └── zkdoc-core-crypto
├── zkdoc-credential
│   └── zkdoc-core-crypto
├── zkdoc-zkp
│   └── zkdoc-core-crypto
└── zkdoc-keys
```

---

## Module Breakdown

### zkdoc-platform (Root POM)

| Property | Value |
|---|---|
| Group ID | `com.zkdoc` |
| Artifact ID | `zkdoc-platform` |
| Version | `0.1.0-SNAPSHOT` |
| Packaging | `pom` |
| Java Version | `26` |
| Spring Boot | `4.1.1` |
| SQLite JDBC | `3.53.4.0` |
| Bucket4j | `8.19.0` |
| ZXing | `3.5.4` |
| Bulletproofs | `1.0.11` (Weavechain) |

External repository: [JitPack](https://jitpack.io) — required by Weavechain Bulletproofs for `keccakj` metadata.

---

### zkdoc-core-crypto

**Status:** Placeholder module (POM only, no Java sources yet).
**Purpose:** Intended to house shared SHA-256 and RSA-PSS cryptographic primitives.

> Currently, each module (`zkdoc-document`, `zkdoc-credential`) contains its own `Sha256Hasher` and `RsaPssSigner` implementations.

---

### zkdoc-document

**Purpose:** Document upload, storage, hash verification, and public QR-based verification.

| Class | Responsibility |
|---|---|
| `DocumentUploadService` | Orchestrates file storage: SHA-256 hashing, RSA-PSS signing, UUID generation, and persistence |
| `JdbcDocumentStore` | SQLite-backed `DocumentStore` implementation |
| `DocumentRecord` | Domain model for stored documents |
| `DocumentNotFoundException` | Domain exception |
| `Sha256Hasher` | SHA-256 digest utility |
| `RsaPssSigner` | RSA-PSS signature utility |
| `VerificationResult` | Standard verification response |
| `PublicVerificationResult` | Minimal public verification response (no sensitive data) |

---

### zkdoc-credential

**Purpose:** Verifiable credential lifecycle — issuance, canonical encoding, signing, and trust verification.

| Class | Responsibility |
|---|---|
| `CredentialService` | Issues and verifies credentials using canonical encoding |
| `CredentialTrustService` / `CredentialTrustVerifier` | Verifies credential integrity and trust chain |
| `CanonicalCredentialEncoder` | Deterministic binary encoding: UTF-8 length-prefixed strings, 16-byte UUID, sorted attributes, fixed-width timestamps |
| `CredentialRsaPssSigner` | RSA-PSS signing on canonical representations |
| `CredentialSha256Hasher` | SHA-256 digest |
| `JdbcCredentialStore` | SQLite persistence |
| `Credential` / `CredentialAttribute` | Domain models |
| `SignedCredential` / `StoredCredential` | Signed & persisted credential wrappers |
| `CredentialStatus` | Enum for credential lifecycle states |
| `CredentialNotFoundException` | Domain exception |

**Key design:** The canonical encoder ensures signing is deterministic — no reliance on `toString()`, JSON ordering, or default charsets.

---

### zkdoc-zkp

**Purpose:** Zero-knowledge proof operations using Pedersen commitments and Weavechain Bulletproofs.

| Class | Responsibility |
|---|---|
| `PedersenCommitmentService` | Creates commitments `C = vG + rH` with secure random blinding factor |
| `CredentialPredicateProver` | Generates Bulletproof range proofs for integer attributes |
| `CredentialPredicateVerifier` | Verifies Bulletproof proofs without revealing the underlying value |
| `JdbcCommitmentStore` | Stores commitment metadata; checks SHA-256 fingerprint collisions |
| `AttributeCommitment` | Domain model for commitments |
| `BlindingFactorSource` / `SecureRandomBlindingFactorSource` | Abstracted random blinding factor generation |
| `CommitmentStore` | Store interface |
| `CredentialPredicateVerificationResult` | Verification response model |

**Key design:** The blinding factor `r` is always a fresh `SecureRandom` value (never derived from `v`). Its SHA-256 fingerprint is collision-checked before storage. The raw blinding factor is **never persisted**.

---

### zkdoc-keys

**Purpose:** Centralized public key registry with lifecycle management.

| Class | Responsibility |
|---|---|
| `KeyRegistryService` | Key registration, status updates, and lookups |
| `JdbcKeyRegistry` | SQLite persistence |
| `RegisteredKey` | Domain model |
| `KeyPurpose` | Enum: `DOCUMENT_SIGNING`, `CREDENTIAL_SIGNING`, `JWT_SIGNING` |
| `KeyStatus` | Enum: `ACTIVE`, `RETIRED`, `REVOKED` |

**Lifecycle policy:**
- **Active** keys: sign and verify
- **Retired** keys: verify only (for historic signatures)
- **Revoked** keys: rejected before verification

---

### zkdoc-api

**Purpose:** Spring Boot application entry point, HTTP controllers, security, audit, and rate limiting.

#### Controllers

| Controller | Endpoints | Required Role |
|---|---|---|
| `HealthController` | `GET /api/health` | Public |
| `DocumentController` | `POST /api/documents`, `GET /api/documents/{id}/verify`, `POST /api/documents/{id}/verify-upload`, `GET /api/documents/{id}/qr` | ISSUER |
| `CredentialController` | `POST /api/credentials`, `GET /api/credentials/{id}/verify` | ISSUER |
| `CredentialPredicateController` | `POST /api/v2/zkp/credential-predicates/verify` | VERIFIER |
| `PublicVerificationController` | `GET /api/public/verify/{token}` | Public |
| `KeyRegistryAdminController` | `POST /api/admin/keys`, `PUT /api/admin/keys/{id}/status` | ADMIN |

#### Security Components

| Class | Responsibility |
|---|---|
| `SecurityConfiguration` | Spring Security filter chain — JWT authentication, role-based endpoint authorization |
| `JwtAuthenticationFilter` | Extracts Bearer JWT, validates RSA signature via registered `JWT_SIGNING` keys, maps `roles` claim |
| `PublicVerificationRateLimitFilter` | Bucket4j-based rate limiting: 30 req/min per client IP |
| `VerificationAuditService` | Records all verification events (subject, timestamp, event type, result) — never stores sensitive payloads |

#### Exception Handlers

- `DocumentExceptionHandler` — maps `DocumentNotFoundException` to 404
- `CredentialExceptionHandler` — maps `CredentialNotFoundException` to 404

---

## Database Schema (Flyway Migrations)

| Migration | Table | Purpose |
|---|---|---|
| **V1** | `document_records` | Document ID, file bytes, SHA-256 hash, RSA-PSS signature, upload timestamp |
| **V2** | Adds `public_token` column | 256-bit opaque token for QR-based public verification |
| **V3** | `credentials` | Credential ID, subject, issuer, status, canonical bytes, signature, timestamps |
| **V4** | `attribute_commitments` | Credential ID, attribute name, Pedersen commitment bytes, blinding fingerprint, issuer attestation |
| **V5** | `key_registry` | Key ID, algorithm, purpose, public key PEM, status, environment variable name for private key |
| **V6** | `verification_audit_events` | Audit event ID, subject, event type, target ID, result, timestamp |

---

## Security Architecture

```
                     Incoming Request
                           │
                     ┌─────▼──────┐
                     │  Is /health │──Yes──▶ Allow
                     │  or /public │
                     └─────┬──────┘
                           │ No
                     ┌─────▼──────┐
                     │  Extract   │
                     │  Bearer JWT│
                     └─────┬──────┘
                           │
                     ┌─────▼──────┐
                     │  Validate  │──Fail──▶ 401 Unauthorized
                     │  RSA-PSS   │
                     │  Signature │
                     └─────┬──────┘
                           │ Pass
                     ┌─────▼──────┐
                     │  Check kid │──Revoked──▶ 401 Unauthorized
                     │  Key Status│
                     └─────┬──────┘
                           │ Active/Retired
                     ┌─────▼──────┐
                     │  Check Role│──Denied──▶ 403 Forbidden
                     │  (ISSUER / │
                     │  VERIFIER/ │
                     │  ADMIN)    │
                     └─────┬──────┘
                           │ Granted
                     ┌─────▼──────┐
                     │  Execute   │
                     │  + Audit   │
                     └────────────┘
```

**Roles:**
- `ISSUER` — document/credential issuance, QR code generation
- `HOLDER` — credential holder operations
- `VERIFIER` — credential predicate verification
- `ADMIN` — key registry management

---

## Zero-Knowledge Proof Flow

```
  ISSUER                                       VERIFIER
    │                                              │
    │  1. Issue credential with integer attribute  │
    │  2. Create Pedersen commitment C = vG + rH   │
    │  3. Sign attestation (credentialId +          │
    │     attributeName + commitmentBytes)          │
    │  4. Store commitment (never store r)          │
    │                                              │
    │                                              │
    │           ┌──────────────────────┐            │
    │           │  Prover (HOLDER)     │            │
    │           │  Generates Bulletprf │            │
    │           │  range proof for     │            │
    │           │  predicate (e.g.     │            │
    │           │  age ≥ 18)           │            │
    │           └──────────┬───────────┘            │
    │                      │                        │
    │                      ▼                        │
    │           POST /api/v2/zkp/                   │
    │           credential-predicates/verify        │
    │                      │                        │
    │           ┌──────────▼───────────┐            │
    │           │  Verifier checks:    │            │
    │           │  • Credential exists │            │
    │           │  • Credential valid  │            │
    │           │  • Issuer signature  │            │
    │           │    on commitment OK  │            │
    │           │  • Bulletproof valid  │            │
    │           │  • Nonce fresh       │            │
    │           │                      │            │
    │           │  NEVER receives:     │            │
    │           │  • Actual value      │            │
    │           │  • Blinding factor   │            │
    │           │  • Raw commitment    │            │
    │           └──────────────────────┘            │
```

---

## Test Coverage Matrix

| Module | Test Class | What is Tested |
|---|---|---|
| **zkdoc-api** | `SecurityAuthorizationIntegrationTest` | Role-based access control enforcement across all endpoints |
| **zkdoc-api** | `CredentialPredicateSecurityIntegrationTest` | Predicate verification: valid proofs, wrong credentials, altered proofs, replay attacks, revoked/expired credentials, invalid signatures, commitment substitution |
| **zkdoc-credential** | `CanonicalCredentialEncoderTest` | Deterministic binary encoding consistency |
| **zkdoc-document** | `DocumentUploadServiceTest` | Document upload, hashing, signing flow |
| **zkdoc-zkp** | `PedersenCommitmentServiceTest` | Commitment creation and verification |
| **zkdoc-keys** | _(none)_ | ⚠️ **No test coverage** |
| **zkdoc-core-crypto** | _(none)_ | Module has no source code |

---

## Design Patterns Used

| Pattern | Where | Purpose |
|---|---|---|
| **MVC** | `zkdoc-api` controllers | Separation of HTTP handling from business logic |
| **Repository** | `JdbcDocumentStore`, `JdbcCredentialStore`, `JdbcKeyRegistry`, `JdbcCommitmentStore` | Data access abstraction |
| **Strategy** | `BlindingFactorSource` → `SecureRandomBlindingFactorSource` | Pluggable random generation |
| **Filter Chain** | `JwtAuthenticationFilter`, `PublicVerificationRateLimitFilter` | Security pipeline |
| **Dependency Injection** | Throughout (Spring `@Component`, `@Service`) | Loose coupling |
| **Domain Exception** | `DocumentNotFoundException`, `CredentialNotFoundException` | Clean error propagation |
| **Canonical Encoding** | `CanonicalCredentialEncoder` | Deterministic signing input |

---

## Production Readiness Notes

### ⚠️ Current Limitations (Development Mode)

| Area | Current State | Production Requirement |
|---|---|---|
| **Database** | SQLite (local file) | PostgreSQL or equivalent with encryption at rest |
| **Key Management** | Environment-injected files | HSM-backed key storage |
| **Signing Keys** | Generated in-process at startup | Persistent, registry-managed keys |
| **JWT Issuance** | External (not in this app) | Secure token service with issuer/audience constraints |
| **Rate Limiting** | In-memory (Bucket4j) | Distributed rate limiter (Redis-backed) |
| **Audit Log** | SQLite table | Tamper-evident ledger or dedicated audit system |
| **TLS** | Not configured | HTTPS termination required |
| **Dependencies** | Weavechain Bulletproofs emits JDK 26 warnings | Address native access and MessagePack compatibility |

### ✅ Strengths

- Strong cryptographic design: Pedersen commitments with fresh random blinding
- Deterministic canonical encoding for reproducible signatures
- Comprehensive RBAC with JWT-based authentication
- Rate limiting on public endpoints
- Audit trail for all verification events
- Clean module separation following domain boundaries
- Thorough security integration tests covering edge cases and attack vectors
