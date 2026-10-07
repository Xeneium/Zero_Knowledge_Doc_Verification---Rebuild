# Zero-Knowledge Document Verification Platform
## Authoritative Domain Model & Value Object Architecture

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 2 — Canonical Credential & Domain Architecture  

---

### 1. Architectural Philosophy: The Domain Boundary

The foundational principle of Phase 2 is:

> **Cryptography must operate on deterministic, explicitly specified representations of authenticated domain objects.**

To guarantee bit-exact determinism, security against serialization vulnerabilities, and clean separation of concerns, the architecture enforces a strict four-tier representation separation:

```
+-----------------------------------------------------------------+
|                          API DTO Tier                           |
|       (Transport representations: JSON, form data, multipart)   |
+-----------------------------------------------------------------+
                                |  Explicit Mappers
                                v
+-----------------------------------------------------------------+
|                       Pure Domain Model                         |
|   (Immutable Value Objects, Entities, Invariants, State Machines) |
+-----------------------------------------------------------------+
               |                                     |
               | Canonicalizer                       | Persistence Port
               v                                     v
+-------------------------------+   +-----------------------------+
|    Canonical Crypto Tier      |   |      Persistence Tier       |
| (Deterministic binary frame,  |   | (PostgreSQL 16+ Relational  |
|  SHA-256 digest, RSA-PSS sig) |   |  schema, Flyway, HikariCP)  |
+-------------------------------+   +-----------------------------+
```

Under no circumstances may a JPA/JDBC persistence record, an ORM proxy, or an API DTO participate directly in cryptographic canonicalization or hashing.

---

### 2. Core Domain Entities & Value Objects

#### 2.1. Strongly Typed Value Objects
Primitives and raw strings are banned from representing security-critical concepts. The platform establishes dedicated, immutable value objects:

| Value Object | Type | Validation / Invariants |
| :--- | :--- | :--- |
| `CredentialId` | 128-bit UUID | Globally unique, non-null, valid RFC 4122 UUID. |
| `IssuerId` | String wrapper | Non-blank, max 128 chars, matches `^[a-zA-Z0-9_\-.:/@]+$`. |
| `KeyId` | String wrapper | Non-blank, max 64 chars, matches `^[a-zA-Z0-9_\-.:]+$`. |
| `AttributeName` | String wrapper | ASCII alphanumeric + underscore/hyphen, max 128 chars, NFC normalized. |
| `AttributeValue` | Typed value container | Enforces NFC normalization, type consistency, signed 64-bit integer range. |
| `CredentialVersion`| Integer wrapper | Strictly positive integer ($v \ge 1$). Baseline: 1. |
| `CanonicalizationVersion` | Integer wrapper | Strictly positive integer ($v \ge 1$). Baseline: 1 (`zkdoc-canonical-v1`). |
| `Digest` | 32-byte array | Exactly 32 bytes (256-bit), constant-time equality check, lowercase hex format. |
| `DocumentId` | 128-bit UUID | Globally unique document identifier. |
| `ArtifactId` | 128-bit UUID | Globally unique physical/stored artifact identifier. |

#### 2.2. Issuer Domain (`com.zkdoc.credential.Issuer`)
Represents an authoritative issuing entity:
* **Fields:** `IssuerId id`, `String name`, `IssuerStatus status`, `KeyId signingKeyId`, `Instant createdAt`, `Instant updatedAt`.
* **Private Key Hygiene:** Private keys are strictly prohibited from residing in the domain entity. Only the public key reference (`signingKeyId`) is stored.
* **Status Machine:** `ACTIVE`, `SUSPENDED`, `REVOKED`. Revocation is terminal and irreversible.

#### 2.3. Credential Attribute Domain (`com.zkdoc.credential.CredentialAttribute`)
Represents a structured, typed claim within a credential:
* **Attribute Types:**
  * `INTEGER` (64-bit signed integer): **Commitment-eligible** for Pedersen commitments and Bulletproof range proofs.
  * `STRING` (UTF-8, Unicode NFC normalized): Excluded from numeric range proofs.
  * `BOOLEAN` (`true` or `false`): Single-byte binary representation.
  * `DATE` (ISO-8601 LocalDate, `YYYY-MM-DD`): Stored as epoch day integer in canonical binary.
  * `DATE_TIME` (ISO-8601 Instant in UTC): Stored as epoch millis integer in canonical binary.
  * `IDENTIFIER` (Normalized string URI/URN identifier).
* **Duplicate Prevention:** Credentials strictly reject duplicate attribute names upon construction.

#### 2.4. Credential Domain (`com.zkdoc.credential.Credential`)
Represents an authenticated collection of issuer claims:
* **Fields:** `CredentialId id`, `String schemaId`, `IssuerId issuerId`, `KeyId issuerKeyId`, `CredentialVersion version`, `Instant issuedAt`, `Instant expiresAt`, `CredentialStatus status`, `List<CredentialAttribute> attributes`, `CanonicalizationVersion canonicalizationVersion`.
* **Invariants:**
  * `expiresAt` must be strictly after `issuedAt`.
  * `attributes` list is immutable (`List.copyOf`) and sorted/deduplicated.
  * Operational `status` changes return new records via `transitionTo(nextStatus)` without altering claim data.

#### 2.5. Document and Artifact Separation (`com.zkdoc.document`)
The platform explicitly decouples document identity from storage representation:
* **`Document`:** Holds legal identity, ownership, metadata, SHA-256 digest, RSASSA-PSS signature, registered key ID, opaque public verification token, and operational status.
* **`DocumentArtifact`:** Represents the physical or object-storage instance (file version, content-type, size, SHA-256 digest, storage key URI, and status).
* **Storage Decoupling:** Large raw file bytes never reside within the core `Document` domain record.
