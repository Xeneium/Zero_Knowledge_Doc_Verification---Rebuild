# Zero-Knowledge Document Verification Platform
## Storage & Database Architecture Specification

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### 1. Hybrid Storage Strategy: Relational + Object Store

To prevent database bloat, performance degradation, and memory exhaustion from handling multi-megabyte binary documents in relational memory, the platform mandates a strict division of persistence responsibilities:

```
+-------------------------------------------------------------------------------+
|                       Relational Metadata Store (PostgreSQL)                  |
|  - Cryptographic digests (SHA-256)        - Digital signatures (RSA-PSS)     |
|  - Key registry & statuses                - Credential canonical metadata    |
|  - Attribute commitments (Pedersen C)     - Structured security audit events |
+-------------------------------------------------------------------------------+
                                      |
                                      | Reference via object_storage_key
                                      v
+-------------------------------------------------------------------------------+
|                      Object Storage Store (S3 / MinIO / Blob)                 |
|  - Raw uploaded document bytes            - Large binary attachment artifacts |
|  - Server-Side Encryption (KMS/SSE-S3)    - Immutability & Object Locking     |
+-------------------------------------------------------------------------------+
```

---

### 2. PostgreSQL Relational Schema Design

All tables reside in the default `public` schema (or dedicated `zkdoc` schema) under strict relational integrity constraints.

#### 2.1. Table: `key_registry`
Stores public cryptographic keys for issuers, JWT signing, and document verification.
* `id` (`UUID`, PK)
* `key_id` (`VARCHAR(64)`, UNIQUE, NOT NULL)
* `owner_id` (`VARCHAR(128)`, NOT NULL)
* `purpose` (`VARCHAR(32)`, NOT NULL)
* `algorithm` (`VARCHAR(32)`, NOT NULL)
* `public_key_pem` (`TEXT`, NOT NULL)
* `status` (`VARCHAR(16)`, NOT NULL) -- ACTIVE, RETIRING, RETIRED, REVOKED
* `created_at`, `activated_at`, `retired_at`, `revoked_at` (`TIMESTAMPTZ`)
* `revocation_reason` (`TEXT`)
* `replacement_key_id` (`VARCHAR(64)`)

#### 2.2. Table: `documents`
Stores document records, digests, and public verification capability tokens.
* `id` (`UUID`, PK)
* `filename` (`VARCHAR(255)`, NOT NULL)
* `content_type` (`VARCHAR(100)`, NOT NULL)
* `file_size` (`BIGINT`, NOT NULL)
* `sha256_digest` (`VARCHAR(64)`, NOT NULL) -- 64-char lowercase hex
* `rsa_signature` (`BYTEA`, NOT NULL)
* `key_id` (`VARCHAR(64)`, NOT NULL, FK -> `key_registry(key_id)`)
* `public_token` (`VARCHAR(64)`, UNIQUE, NOT NULL) -- 256-bit base64url capability token
* `object_storage_key` (`VARCHAR(512)`, NOT NULL) -- S3 object location
* `status` (`VARCHAR(16)`, NOT NULL) -- ACTIVE, SUSPENDED, REVOKED
* `created_at` (`TIMESTAMPTZ`, NOT NULL)

#### 2.3. Table: `credentials`
Stores authoritative verifiable credentials.
* `id` (`UUID`, PK)
* `schema_id` (`VARCHAR(255)`, NOT NULL)
* `issuer_id` (`VARCHAR(128)`, NOT NULL)
* `key_id` (`VARCHAR(64)`, NOT NULL, FK -> `key_registry(key_id)`)
* `version` (`INT`, NOT NULL)
* `issued_at` (`TIMESTAMPTZ`, NOT NULL)
* `expires_at` (`TIMESTAMPTZ`, NOT NULL)
* `status` (`VARCHAR(16)`, NOT NULL) -- ACTIVE, SUSPENDED, REVOKED, EXPIRED
* `canonical_digest` (`BYTEA`, NOT NULL) -- 32 bytes SHA-256
* `rsa_signature` (`BYTEA`, NOT NULL)
* `created_at` (`TIMESTAMPTZ`, NOT NULL)
* `attributes_json` (`JSONB`, NOT NULL) -- Non-sensitive attribute dictionary for convenience retrieval

#### 2.4. Table: `attribute_commitments`
Stores Pedersen commitments cryptographically bound to credentials.
* `id` (`UUID`, PK)
* `credential_id` (`UUID`, NOT NULL, FK -> `credentials(id)` ON DELETE RESTRICT)
* `attribute_name` (`VARCHAR(128)`, NOT NULL)
* `commitment_point` (`BYTEA`, NOT NULL) -- 32-byte Ristretto255 compressed point
* `blinding_factor_salt_hash` (`VARCHAR(64)`, NOT NULL) -- One-way hash for collision check (NO RAW BLINDER)
* `attestation_signature` (`BYTEA`, NOT NULL) -- Issuer RSA-PSS signature over commitment binding
* `attestation_key_id` (`VARCHAR(64)`, NOT NULL, FK -> `key_registry(key_id)`)
* `version` (`INT`, NOT NULL)
* `created_at` (`TIMESTAMPTZ`, NOT NULL)
* **Constraint:** `UNIQUE (credential_id, attribute_name)`

#### 2.5. Table: `verification_sessions`
Tracks challenge nonces for replay prevention.
* `id` (`UUID`, PK)
* `nonce` (`VARCHAR(64)`, UNIQUE, NOT NULL) -- 256-bit cryptographic nonce
* `credential_id` (`UUID`, NOT NULL)
* `attribute_name` (`VARCHAR(128)`, NOT NULL)
* `threshold` (`BIGINT`, NOT NULL)
* `verifier_identity` (`VARCHAR(128)`, NOT NULL)
* `status` (`VARCHAR(16)`, NOT NULL) -- PENDING, COMPLETED, EXPIRED
* `created_at` (`TIMESTAMPTZ`, NOT NULL)
* `expires_at` (`TIMESTAMPTZ`, NOT NULL)

#### 2.6. Table: `verification_audit_events`
Immutable security audit trail.
* `id` (`UUID`, PK)
* `correlation_id` (`VARCHAR(64)`, NOT NULL)
* `actor_subject` (`VARCHAR(128)`, NOT NULL)
* `actor_role` (`VARCHAR(32)`, NOT NULL)
* `event_type` (`VARCHAR(64)`, NOT NULL) -- DOCUMENT_VERIFIED, PREDICATE_PROOF_VERIFIED, etc.
* `resource_id` (`VARCHAR(128)`)
* `result` (`VARCHAR(16)`, NOT NULL) -- PASS, FAIL
* `reason_code` (`VARCHAR(64)`)
* `client_ip_hash` (`VARCHAR(64)`, NOT NULL) -- Anonymized one-way hash of client IP
* `timestamp` (`TIMESTAMPTZ`, NOT NULL)
* **Constraint:** Append-only table. Updates and deletes are prohibited by database permissions and triggers.

---

### 3. Object Storage Specification

1. **Bucket Structure:** Partitioned by tenant / date: `s3://zkdoc-documents/{year}/{month}/{documentId}.bin`
2. **Access Control:** Fully private bucket (`BlockPublicAcls = true`, `BlockPublicPolicy = true`). Access only via IAM role assumed by backend pods.
3. **Encryption at Rest:** SSE-KMS with Customer Managed Key (CMK), rotated annually.
4. **Data Integrity on Read:** When reading bytes from S3 (e.g., for re-verification), the application computes SHA-256 and asserts equality against `documents.sha256_digest`. Any divergence triggers an immediate security alert.
