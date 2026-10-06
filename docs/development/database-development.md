# Zero-Knowledge Document Verification Platform
## Database Development & Migration Guide

**Document Version:** 1.0.0  
**Phase:** Phase 1 — Project Foundation & Engineering Infrastructure  

---

### 1. Database Engine Strategy

* **Production Engine:** PostgreSQL 16+ (Mandated by ADR-004 and `storage-architecture.md`).
* **Connection Pooling:** HikariCP with bounded sizing (`maximum-pool-size: 10`, `minimum-idle: 2`, `connection-timeout: 30s`).
* **Migration Framework:** Flyway 10+.

---

### 2. Migration Conventions

1. **Versioned Scripts:** Sequential numbering format `V{version}__{description}.sql`.
2. **Directory Separation:**
   * PostgreSQL migrations reside in `classpath:db/migration/postgresql/`.
   * Standalone local SQLite migrations reside in `classpath:db/migration/sqlite/`.
3. **Immutability:** Once committed, a migration script must **never** be edited. Subsequent schema changes require a new sequential migration (`V2__...sql`, etc.).
4. **Primary Keys:** Standardize on UUID v4 primary keys for high-entropy distributed identification.
5. **Audit Columns:** Tables record explicit UTC timestamps using `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`.

---

### 3. Foundation Schema Tables (PostgreSQL)

* `key_registry`: Public cryptographic verification keys and status lifecycle.
* `documents`: Document integrity records, SHA-256 digests, RSA-PSS signatures, and capability tokens.
* `credentials`: Verifiable credentials, canonical digests, and issuer signatures.
* `attribute_commitments`: Ristretto255 Pedersen commitment points and binding attestations.
* `verification_sessions`: Anti-replay nonces and verifier session state.
* `verification_audit_events`: Immutable security audit log.
