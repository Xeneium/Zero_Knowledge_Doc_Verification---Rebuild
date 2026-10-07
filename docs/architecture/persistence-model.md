# Zero-Knowledge Document Verification Platform
## Relational Persistence Model & Schema Governance

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 2 — Canonical Credential & Domain Architecture  

---

### 1. Relational Boundary & Isolation Principles

Relational database schemas serve as an append-oriented persistence boundary. The database model enforces the following core guarantees:
1. **Explicit Data Mapping:** Persistence adapters (`JdbcCredentialStore`, `JdbcIssuerStore`, `JdbcDocumentStore`, `JdbcArtifactStore`) explicitly map database result sets to immutable domain objects and vice versa.
2. **Database Integrity via Constraints:** Database invariants are enforced through PostgreSQL native primary keys, foreign keys, unique indexes, and `CHECK` constraints, rather than relying solely on JVM runtime assertions.
3. **Immutability of Cryptographic Records:** Historical issuance records, digests, and signatures cannot be mutated via CRUD updates.

---

### 2. Schema Entities (PostgreSQL 16+)

#### 2.1. `issuers` Table
Stores authoritative issuing authorities:
```sql
CREATE TABLE issuers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issuer_id VARCHAR(128) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    signing_key_id VARCHAR(64) NOT NULL REFERENCES key_registry(key_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### 2.2. `credentials` Table
Stores issued verifiable credentials and their cryptographic evidence:
```sql
CREATE TABLE credentials (
    id UUID PRIMARY KEY,
    schema_id VARCHAR(255) NOT NULL,
    issuer_id VARCHAR(128) NOT NULL,
    key_id VARCHAR(64) NOT NULL REFERENCES key_registry(key_id),
    version INT NOT NULL DEFAULT 1,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED', 'EXPIRED')),
    canonical_digest BYTEA NOT NULL,
    rsa_signature BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb
);
```

#### 2.3. `credential_attributes` Table
Provides normalized, strongly typed relational representation of credential claims:
```sql
CREATE TABLE credential_attributes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_id UUID NOT NULL REFERENCES credentials(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('INTEGER', 'STRING', 'BOOLEAN', 'DATE', 'DATE_TIME', 'IDENTIFIER')),
    value TEXT NOT NULL,
    integer_value BIGINT,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_credential_attribute_name UNIQUE (credential_id, name)
);
```

#### 2.4. `documents` Table
Maintains document identities, cryptographic digests, signatures, and capability tokens:
```sql
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_digest VARCHAR(64) NOT NULL,
    rsa_signature BYTEA NOT NULL,
    key_id VARCHAR(64) NOT NULL REFERENCES key_registry(key_id),
    public_token VARCHAR(64) UNIQUE NOT NULL,
    object_storage_key VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### 2.5. `document_artifacts` Table
Stores versioned physical representations decoupled from document identities:
```sql
CREATE TABLE document_artifacts (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    artifact_version INT NOT NULL DEFAULT 1,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_digest VARCHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'DELETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_artifact_version UNIQUE (document_id, artifact_version)
);
```

---

### 3. Migration Parity Strategy

To ensure seamless local testing without container dependencies while guaranteeing production PostgreSQL fidelity:
* **Production & Container Migrations (`classpath:db/migration/postgresql`):**
  * `V1__init_schema.sql`: Core schema (key registry, documents, credentials, commitments, sessions, audit events).
  * `V2__canonical_domain_entities.sql`: Domain additions (`issuers`, `credential_attributes`, `document_artifacts`).
* **Local SQLite Development Migrations (`classpath:db/migration/sqlite`):**
  * `V1` through `V6`: Baseline local tables.
  * `V7__create_canonical_domain_entities.sql`: Schema parity additions for domain tables.
