# Zero-Knowledge Document Verification Platform
## Architecture Decision Records (ADRs)

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### Index of Architecture Decisions

* [ADR-001: Java Runtime Baseline — Java 21 LTS](#adr-001-java-runtime-baseline--java-21-lts)
* [ADR-002: Framework Baseline — Spring Boot 3.4.x GA](#adr-002-framework-baseline--spring-boot-34x-ga)
* [ADR-003: Architecture Topology — Modular Monolith](#adr-003-architecture-topology--modular-monolith)
* [ADR-004: Production Relational Store — PostgreSQL 16+](#adr-004-production-relational-store--postgresql-16)
* [ADR-005: Document Storage Architecture — Object Storage with Metadata Separation](#adr-005-document-storage-architecture--object-storage-with-metadata-separation)
* [ADR-006: Digital Signature Scheme — RSASSA-PSS with SHA-256](#adr-006-digital-signature-scheme--rsassa-pss-with-sha-256)
* [ADR-007: Cryptographic Digest Algorithm — SHA-256](#adr-007-cryptographic-digest-algorithm--sha-256)
* [ADR-008: Commitment Scheme — Ristretto255 Pedersen Commitments](#adr-008-commitment-scheme--ristretto255-pedersen-commitments)
* [ADR-009: ZKP Engine Governance — Isolated RangeProofEngine SPI](#adr-009-zkp-engine-governance--isolated-rangeproofengine-spi)
* [ADR-010: Key Management & Status Policy — Four-State Lifecycle](#adr-010-key-management--status-policy--four-state-lifecycle)
* [ADR-011: Holder Private Material Custody — Client-Side Isolation](#adr-011-holder-private-material-custody--client-side-isolation)
* [ADR-012: QR Verification Modes — Registered Artifact vs Presented Artifact](#adr-012-qr-verification-modes--registered-artifact-vs-presented-artifact)
* [ADR-013: ZKP Statement Binding — Full Transcript Absorption](#adr-013-zkp-statement-binding--full-transcript-absorption)
* [ADR-014: API Authentication — Strict NimbusJwtDecoder with kid Resolution](#adr-014-api-authentication--strict-nimbusjwtdecoder-with-kid-resolution)
* [ADR-015: Abuse Protection — Bounded Sliding Window & Redis Token Bucket](#adr-015-abuse-protection--bounded-sliding-window--redis-token-bucket)
* [ADR-016: Security Audit Model — Append-Only Metadata Trail](#adr-016-security-audit-model--append-only-metadata-trail)

---

### ADR-001: Java Runtime Baseline — Java 21 LTS

* **Context:** The current repository POM configured experimental `java.version: 26`. Java 26 is a future non-LTS development version that lacks commercial production support, Docker base image stability, and broad enterprise deployment readiness. Java 21 is the current Long-Term Support (LTS) release with enterprise stability and widespread cryptographic provider compatibility.
* **Decision:** Establish **Java 21 LTS** as the official production runtime baseline. Any local testing on newer JVMs must compile and pass against `-release 21`.
* **Alternatives Considered:** 
  * *Java 26:* Rejected due to lack of LTS status, absence of stable base images, and experimental runtime flags.
  * *Java 17:* Considered, but lacks Project Loom (Virtual Threads) and latest Panama/vector enhancements present in Java 21.
* **Security Implications:** Java 21 receives ongoing security quarterly CPU updates from major vendors (Adoptium, Red Hat, Amazon Corretto, Oracle).
* **Operational Implications:** Full Docker distroless support, guaranteed Kubernetes stability, full APM agent compatibility.
* **Trade-offs:** Cannot use preview features specific to Java 22-26 without explicit flags.
* **Consequences:** All build scripts, Dockerfiles, and CI jobs will target JDK 21.

---

### ADR-002: Framework Baseline — Spring Boot 3.4.x GA

* **Context:** The current repository POM declared `spring-boot-starter-parent: 4.1.1`, which is not a released GA version (Spring Boot 3.x is current production GA).
* **Decision:** Adopt the latest **Spring Boot 3.4.x GA** (or supported 3.3.x GA) release as the managed parent baseline.
* **Alternatives Considered:** Experimental version strings. Rejected to adhere to the strict production GA rule.
* **Security Implications:** Access to active Spring Security and Spring Framework vulnerability patches.
* **Operational Implications:** Fully compatible with standard Maven Central dependencies and third-party starters.
* **Consequences:** Dependency management is predictable and stable.

---

### ADR-003: Architecture Topology — Modular Monolith

* **Context:** The platform requires strong domain boundaries across credentials, documents, ZKPs, and keys, but distributed microservices would add distributed transaction overhead, network latency, and deployment complexity.
* **Decision:** Architect the platform as a **strictly partitioned Modular Monolith** where each domain is an independent Maven module with unidirectional dependencies, deployed as a single Spring Boot executable.
* **Alternatives Considered:** Microservices architecture. Rejected as premature distributed complexity.
* **Security Implications:** In-process call security, single perimeter attack surface, clear internal package encapsulation.
* **Operational Implications:** Simple single-container deployment, shared database transactions where needed.
* **Consequences:** Architecture boundaries must be enforced via ArchUnit and Maven module constraints.

---

### ADR-004: Production Relational Store — PostgreSQL 16+

* **Context:** The current repository uses local SQLite (`./data/zkdoc.db`). SQLite lacks multi-master concurrent writing, Row-Level Security, strong network authentication, and enterprise horizontal scaling.
* **Decision:** Design the production schema and Flyway migrations for **PostgreSQL 16+**. Local development may use containerized PostgreSQL or testcontainers.
* **Alternatives Considered:** SQLite for production (rejected due to concurrency and security limits); MySQL (rejected due to PostgreSQL's superior JSONB, BYTEA, and cryptographic extension support).
* **Security Implications:** Native TLS encryption, SCRAM-SHA-256 password hashing, connection pooling isolation.
* **Operational Implications:** Standard enterprise backup (WAL-E / pgBackRest) and high availability (Patroni).
* **Consequences:** All Flyway migrations will use standard PostgreSQL DDL syntax.

---

### ADR-005: Document Storage Architecture — Object Storage with Metadata Separation

* **Context:** Storing large PDF/image binary documents directly in relational database tables causes table bloat, slows query caching, and increases backup sizes.
* **Decision:** Store only cryptographic digests, signatures, and metadata in PostgreSQL; store raw document bytes in private S3/Blob storage referenced by an opaque `object_storage_key`.
* **Alternatives Considered:** PostgreSQL `BYTEA` / Large Objects (BLOBs) for all files. Rejected for production scaling.
* **Security Implications:** Object storage enforces Server-Side Encryption (KMS/SSE-S3). Every file read re-verifies SHA-256 before processing.
* **Operational Implications:** Scalable, cost-effective storage for large file volumes.
* **Consequences:** System requires S3 client adapter configuration.

---

### ADR-006: Digital Signature Scheme — RSASSA-PSS with SHA-256

* **Context:** Document and credential authenticity requires legally defensible, tamper-evident digital signatures.
* **Decision:** Mandate **RSASSA-PSS** (PKCS #1 v2.2) with SHA-256, MGF1-SHA256, and 32-byte salt length for all document and credential signatures.
* **Alternatives Considered:** RSASSA-PKCS1-v1_5 (rejected due to padding oracle vulnerabilities); ECDSA (considered, but RSA-PSS provides widespread institutional and regulatory compliance).
* **Security Implications:** Probabilistic signature scheme with provable security reduction to the RSA problem.
* **Consequences:** Fixed 2048-bit or 4096-bit RSA keys required in registry.

---

### ADR-007: Cryptographic Digest Algorithm — SHA-256

* **Context:** The platform requires collision-resistant, deterministic hashing for document byte streams and canonical credentials.
* **Decision:** Standardize uniformly on **SHA-256** (FIPS 180-4).
* **Alternatives Considered:** SHA-512 (larger digest, unnecessary overhead), BLAKE3 (non-standardized in legacy enterprise environments), MD5/SHA-1 (cryptographically broken, forbidden).
* **Security Implications:** 128 bits of collision resistance; 256 bits of pre-image resistance.
* **Consequences:** All digests are fixed 32-byte arrays or 64-character hex strings.

---

### ADR-008: Commitment Scheme — Ristretto255 Pedersen Commitments

* **Context:** Zero-knowledge range proofs require a homomorphic commitment scheme that hides the attribute while binding the prover.
* **Decision:** Adopt **Pedersen commitments over the prime-order Ristretto255 group** ($C = vG + rH$).
* **Alternatives Considered:** Secp256k1 Pedersen commitments (has cofactor complications), KZG polynomial commitments (requires trusted setup).
* **Security Implications:** Group order is prime ($\ell$), eliminating small subgroup attacks. Information-theoretically hiding.
* **Consequences:** Attributes for ZKP must be represented as 64-bit integers.

---

### ADR-009: ZKP Engine Governance — Isolated RangeProofEngine SPI

* **Context:** Directly embedding third-party ZKP library classes across controllers and services creates tight coupling and exposes the system to upstream library churn.
* **Decision:** Encapsulate all Bulletproof and commitment operations behind a clean, project-owned Java interface: `RangeProofEngine`.
* **Alternatives Considered:** Direct coupling to Weavechain / Dalek classes throughout the codebase. Rejected.
* **Security Implications:** Centralized cryptographic parameter validation and transcript handling in one audited adapter.
* **Operational Implications:** The underlying native or Java proving library can be swapped without touching domain logic.
* **Consequences:** Adapter must be thoroughly covered by unit and adversarial test suites.

---

### ADR-010: Key Management & Status Policy — Four-State Lifecycle

* **Context:** Digital signatures must remain verifiable for years, while compromised keys must be immediately revocable.
* **Decision:** Implement a formal four-state lifecycle: `ACTIVE`, `RETIRING`, `RETIRED`, `REVOKED`. `RETIRED` keys verify past signatures; `REVOKED` keys fail all verifications immediately.
* **Alternatives Considered:** Simple boolean `isActive`. Rejected because it conflates scheduled retirement with emergency compromise.
* **Security Implications:** Enforces fail-closed verification on compromised keys while preserving historical auditability.
* **Consequences:** Key verification service must consult key status on every operation.

---

### ADR-011: Holder Private Material Custody — Client-Side Isolation

* **Context:** In a zero-knowledge architecture, if the server retains the prover's private blinding factor $r$, the privacy model is compromised.
* **Decision:** Prover secrets ($r$, witness) are delivered directly to the holder client and **never persisted in the server database**.
* **Alternatives Considered:** Storing blinding factors encrypted in the database. Rejected because a server compromise would expose all blinders.
* **Security Implications:** True zero-knowledge privacy for the holder. Server has zero knowledge of attribute values.
* **Operational Implications:** Holder must back up their credentials locally.
* **Consequences:** Server cannot generate proofs on behalf of holders.

---

### ADR-012: QR Verification Modes — Registered Artifact vs Presented Artifact

* **Context:** Verifying a QR code attached to a document can mean two different things: verifying the database metadata via a capability link, or verifying that a physical/digital file in hand matches the record.
* **Decision:** Formalize two distinct operational modes: **Mode A (Registered Artifact)** via fast opaque capability token without loading document bytes; and **Mode B (Presented Artifact)** via fresh upload and direct byte comparison.
* **Alternatives Considered:** Loading the full document on every QR scan. Rejected due to bandwidth, latency, and memory exhaustion risks.
* **Security Implications:** Mode A proves the metadata record is authentic; Mode B proves a presented file matches the record.
* **Consequences:** Separate endpoints and UI workflows are implemented and documented.

---

### ADR-013: ZKP Statement Binding — Full Transcript Absorption

* **Context:** Bulletproofs verify range membership, but without binding public context, an attacker can replay a proof for a different credential, attribute, or threshold.
* **Decision:** Mandate that `credentialId`, `credentialDigest`, `attributeName`, `threshold`, $C$, $C'$, and fresh verifier `nonce` are absorbed into the Fiat-Shamir transcript.
* **Alternatives Considered:** Binding only the threshold. Rejected because it allows cross-credential replay.
* **Security Implications:** Complete cryptographic immunity against proof substitution and replay attacks.
* **Consequences:** Prover and verifier must follow identical byte-order transcript absorption sequences.

---

### ADR-014: API Authentication — Strict NimbusJwtDecoder with kid Resolution

* **Context:** Protected API endpoints require robust authentication without coupling to a specific commercial identity provider.
* **Decision:** Implement Spring Security Resource Server using `NimbusJwtDecoder` with dynamic public key resolution from the `key_registry` matching the JWT `kid`, enforcing `iss`, `aud`, `exp`, and allowed algorithms (`RS256`, `PS256`).
* **Alternatives Considered:** Shared HMAC symmetric secret (`HS256`). Rejected due to key distribution risks.
* **Security Implications:** Eliminates algorithm confusion attacks and unauthorized token issuance.
* **Consequences:** Identity providers must register their public signing key in the platform registry.

---

### ADR-015: Abuse Protection — Bounded Sliding Window & Redis Token Bucket

* **Context:** Unauthenticated public QR endpoints and compute-heavy ZKP verification endpoints are vulnerable to DoS attacks.
* **Decision:** Implement rate limiting using bounded sliding windows (in-memory for standalone, Redis token bucket via Bucket4j for distributed deployment), keyed by sanitized IP and subject.
* **Alternatives Considered:** Unbounded `ConcurrentHashMap`. Rejected because it leads to heap exhaustion under IP spoofing attacks.
* **Security Implications:** Protects CPU and database resources from exhaustion attacks.
* **Consequences:** Rate limiting filter configured in Spring Security chain before expensive controllers.

---

### ADR-016: Security Audit Model — Append-Only Metadata Trail

* **Context:** Regulatory compliance and security monitoring require non-repudiable audit logs of all verification events.
* **Decision:** Implement an append-only PostgreSQL table (`verification_audit_events`) capturing actor, correlation ID, event type, result, and timestamp, with strict exclusion of sensitive payloads, blinders, and file bytes.
* **Alternatives Considered:** Logging to application console / log files only. Rejected due to difficulty querying and enforcing retention.
* **Security Implications:** Tamper-evident audit trail without leaking private credential attributes or proving secrets.
* **Consequences:** Audit service called asynchronously or inside verification transactions.

---

### ADR-017: Authoritative Domain Model & Strongly Typed Value Objects

* **Context:** Primitives (`String`, `UUID`, `long`) when used directly across security-sensitive domain operations can be accidentally swapped, incorrectly validated, or mutated, causing cryptographic verification failures.
* **Decision:** Establish strongly typed, immutable Value Objects (`CredentialId`, `IssuerId`, `KeyId`, `AttributeName`, `AttributeValue`, `Digest`, `CredentialVersion`, `CanonicalizationVersion`, `DocumentId`, `ArtifactId`) enforcing non-nullness, string patterns, normalization, and bounds at instantiation time.
* **Alternatives Considered:** Raw Java standard types throughout the domain. Rejected due to type confusion and scattered validation logic.
* **Security Implications:** Invariants are guaranteed by construction; invalid identifiers or malformed attributes cannot reach cryptographic pipelines.
* **Consequences:** Domain models use dedicated types with explicit factory methods.

---

### ADR-018: Explicit Attribute Type Hierarchy & Commitment Eligibility

* **Context:** ZKP range proofs and Pedersen commitments require 64-bit numeric scalar inputs, whereas general credential claims can be arbitrary strings, booleans, or dates.
* **Decision:** Explicitly type attributes (`INTEGER`, `STRING`, `BOOLEAN`, `DATE`, `DATE_TIME`, `IDENTIFIER`) and restrict commitment and predicate proof eligibility strictly to `INTEGER` attributes ($v \in [0, 2^{63}-1]$).
* **Alternatives Considered:** Untyped `Map<String, Object>` or stringifying all values. Rejected because untyped maps create cryptographic ambiguity and cannot be safely converted to scalar points.
* **Security Implications:** Prevents type-confusion attacks and mathematical failures during elliptic curve point multiplication.
* **Consequences:** Attempting to commit non-INTEGER attributes is rejected at the domain validation boundary.

---

### ADR-019: Deterministic Binary Canonicalization Protocol (`zkdoc-canonical-v1`)

* **Context:** Cryptographic signatures require identical byte sequences across different platforms, architectures, and JVM runtimes. JSON serialization is vulnerable to field reordering, whitespace variance, and floating point divergence.
* **Decision:** Implement a deterministic binary encoding protocol with a 4-byte magic header (`ZKDC`), 2-byte version (`0x00 0x01`), big-endian integers, length-prefixed NFC UTF-8 strings, and ascending lexicographical attribute sorting.
* **Alternatives Considered:** Canonical JSON (RFC 8785) or Java Serialization. Rejected because binary frames are more performant, zero-overhead, completely immune to JSON library upgrade differences, and eliminate Java serialization gadget vulnerabilities.
* **Security Implications:** Bit-exact reproducibility and complete protection against serialization malleability attacks.
* **Consequences:** All credentials canonicalize using `CanonicalCredentialEncoder` prior to digital signing or hashing.

---

### ADR-020: Operational Lifecycle Decoupling from Canonical Cryptographic Payload

* **Context:** A verifiable credential has both cryptographic validity (signature over authentic claims) and operational lifecycle status (`ACTIVE`, `SUSPENDED`, `REVOKED`, `EXPIRED`). Including mutable status inside the signed bytes means that revoking or suspending a credential invalidates the issuer's original signature.
* **Decision:** Exclude operational `CredentialStatus` from the canonical binary payload. The digital signature covers only permanent claims and validity timestamps; status transitions are tracked in the database and evaluated at verification time.
* **Alternatives Considered:** Signing status inside the credential payload. Rejected because revocation would require generating a new digital signature or tampering with the original evidence.
* **Security Implications:** Digital signatures remain permanent historical proof of issuance, while revocation can be administratively executed without cryptographic invalidation.
* **Consequences:** Verification logic independently verifies both signature authenticity and active operational status.

---

### ADR-021: Separation of Document Identity from Physical Storage Artifacts

* **Context:** Digital documents frequently exist in multiple representations (e.g. original upload, compressed archive, signed PDF) and storing raw binary content in database rows causes severe table bloat.
* **Decision:** Decouple document domain identity (`Document`) from physical file representations (`DocumentArtifact`). Raw bytes are stored in private object storage; database records retain only SHA-256 digests, RSA signatures, metadata, and storage keys.
* **Alternatives Considered:** Storing binary files directly in PostgreSQL `BYTEA` columns. Rejected due to scaling, backup, and memory limits.
* **Security Implications:** High-performance relational queries; document tampering is detected on every read by verifying the content's SHA-256 digest against the signed database record.
* **Consequences:** Application services interact with metadata and storage keys, with separate artifact version tracking.

---

### ADR-022: Relational Persistence Boundary and Explicit Object-Relational Mapping

* **Context:** Directly exposing relational entities (JPA/Hibernate or JDBC entities) to cryptographic engines creates tight coupling to database column names, types, and retrieval order.
* **Decision:** Establish an explicit persistence boundary where JDBC repositories map between relational database tables and pure immutable domain records without exposing database models to cryptographic or API layers.
* **Alternatives Considered:** Using JPA entities as cryptographic payloads. Rejected due to dirty tracking proxies, lazy loading, and unstable reflection serialization.
* **Security Implications:** Clean architectural separation ensures database schema evolution does not alter cryptographic hashes or signature verification.
* **Consequences:** Mappers are explicitly implemented in `JdbcCredentialStore`, `JdbcIssuerStore`, and `JdbcArtifactStore`.

---

### ADR-023: Strict Unicode Normalization (NFC) and Lexicographical Sorting

* **Context:** Unicode characters can have visually indistinguishable representations with different byte encodings (e.g., precomposed `é` vs decomposed `e` + combining acute accent), which causes signature verification failure if representations diverge between issuance and verification.
* **Decision:** Mandate Unicode Normalization Form C (`Normalizer.Form.NFC`) on all string attribute names, values, schema URIs, and issuer IDs, and sort all attributes lexicographically by their UTF-8 byte arrays before writing binary frames.
* **Alternatives Considered:** Default JVM String comparison or raw character order. Rejected due to locale dependencies and Unicode normalization discrepancies.
* **Security Implications:** Immunizes the platform against Unicode decomposition spoofing and ensures deterministic byte ordering across all platforms.
* **Consequences:** `CanonicalCredentialEncoder` normalizes all strings and enforces strict UTF-8 byte comparison.


---

### ADR-024: Cryptographic Key Purpose Separation & Purpose Confusion Prevention

* **Context:** A single cryptographic key pair used across multiple semantic operations creates dangerous cryptographic vulnerabilities (e.g., using a JWT signing key to sign a credential, or using a document key to sign an attribute commitment).
* **Decision:** Enforce four strictly distinct and non-overlapping `KeyPurpose` types: `JWT_SIGNING`, `CREDENTIAL_SIGNING`, `DOCUMENT_SIGNING`, and `COMMITMENT_ATTESTATION_SIGNING`. Cryptographic operations must validate that the key's registered purpose strictly matches the operation before signing or verification.
* **Alternatives Considered:** Generic multi-purpose keys (`ALL`, `SIGNING`). Rejected due to cross-protocol forgery attacks and lack of isolation.
* **Security Implications:** Eliminates cross-purpose signature forgery and ensures cryptographic operations remain confined to their intended domain.
* **Consequences:** All keys in `key_registry` are bound to exactly one purpose; any mismatch triggers an immediate fail-closed security exception.

---

### ADR-025: Four-State Key Lifecycle & Distinction Between Retirement and Revocation

* **Context:** Cryptographic key turnover must differentiate between scheduled key rotation and emergency key compromise. If retired keys are treated as revoked, historical documents and credentials become unverifiable.
* **Decision:** Implement a strict four-state key lifecycle: `ACTIVE`, `RETIRING`, `RETIRED`, and `REVOKED`. 
  - `ACTIVE`: Key is valid for both new signing operations and signature verification.
  - `RETIRING`: Key is undergoing scheduled phase-out; signing is restricted.
  - `RETIRED`: Signing is strictly prohibited; historical signature verification is fully permitted.
  - `REVOKED`: Key is compromised or permanently invalidated; signing is prohibited and historical verification fails closed.
* **Alternatives Considered:** Binary key status (`ACTIVE` / `INACTIVE`). Rejected because it conflates routine retirement with security compromise.
* **Security Implications:** Guarantees auditability and non-repudiation for historically valid credentials while immediately disabling compromised keys.
* **Consequences:** Key status transitions are strictly governed (`ACTIVE` -> `RETIRING` -> `RETIRED`, `ACTIVE`/`RETIRING`/`RETIRED` -> `REVOKED`), and `REVOKED` is terminal.

---

### ADR-026: Public Key Trust Registry & Contextual Resolution Policy

* **Context:** Verifying signatures solely based on a `keyId` header allows an attacker to supply their own key ID or register an arbitrary public key and forge signatures.
* **Decision:** Establish a centralized `KeyTrustResolver` that resolves trusted public keys only when all contextual constraints match: `keyId`, `expectedPurpose`, `expectedOwnerId`, and verification timestamp. A key ID alone does not confer trust.
* **Alternatives Considered:** Blind lookup by `keyId` without owner or purpose validation. Rejected as insecure.
* **Security Implications:** Protects against key substitution attacks, cross-tenant impersonation, and expired/unauthorized key verification.
* **Consequences:** All signature verification workflows must provide the expected owner and operational purpose when resolving public keys.

---

### ADR-027: Private-Key Custody Decoupling & Elimination of Constructor Key Generation

* **Context:** Generating ephemeral RSA keypairs inside service constructors or storing private keys in domain records causes credential invalidation on restart, key leakage in memory dumps, and inability to integrate with HSM / KMS.
* **Decision:** Prohibit private keys from ever entering domain entities (`RegisteredKey` stores only public key PEM, DER, and fingerprint). Separate private-key custody behind the `SigningKeyProvider` SPI. Provide `DevelopmentSigningKeyProvider` for test/dev with automated keypair consistency checks, while production relies on KMS/Vault.
* **Alternatives Considered:** Embedding `PrivateKey` objects directly in `RegisteredKey` or database columns. Rejected due to extreme security risk.
* **Security Implications:** Prevents private key leakage, guarantees key persistence across application restarts, and enables zero-trust KMS integration.
* **Consequences:** Signing services obtain private keys exclusively through the custody provider, which fails closed if the private key does not match the registered public key.

---

### ADR-028: Standard RSASSA-PSS (RFC 8017) Cryptographic Baseline

* **Context:** Legacy RSA PKCS#1 v1.5 padding is vulnerable to Bleichenbacher-style padding oracle attacks and lack of provable security.
* **Decision:** Mandate RFC 8017 RSASSA-PSS with SHA-256 digest, MGF1 with SHA-256 mask generation, 32-byte salt, trailer field 1, and a minimum key size of 2048 bits (recommending 3072+ bits).
* **Alternatives Considered:** RSA PKCS#1 v1.5 or raw RSA. Rejected due to cryptographic vulnerabilities and lack of modern regulatory compliance.
* **Security Implications:** Provably secure digital signatures resistant to padding oracle and chosen-ciphertext attacks.
* **Consequences:** All RSA signature generation and verification across `zkdoc-core-crypto`, `zkdoc-document`, and `zkdoc-credential` use `RsaPssSignatureService`.

---

### ADR-029: SHA-256 Key Fingerprinting over X.509 DER SubjectPublicKeyInfo

* **Context:** Identifying and comparing public keys via formatted PEM strings or custom encodings is fragile and vulnerable to formatting discrepancies (e.g. CRLF vs LF, header differences).
* **Decision:** Compute key fingerprints deterministically as lowercase hexadecimal `SHA-256(derBytes)` over standard ASN.1 X.509 `SubjectPublicKeyInfo` DER encoding.
* **Alternatives Considered:** Fingerprinting over PEM strings or raw modulus bytes. Rejected due to formatting variance and lack of algorithm parameters.
* **Security Implications:** Canonical, collision-resistant public key identification and instant duplicate detection in database indexes.
* **Consequences:** `KeyFingerprint` computes the fingerprint automatically whenever a public key is registered or parsed.

---

### ADR-030: Append-Only Cryptographic Key Lifecycle Audit Trail

* **Context:** Regulatory compliance and security monitoring require a complete, immutable record of every key lifecycle event (registration, activation, retirement, rotation, revocation).
* **Decision:** Implement dedicated append-only tables (`key_lifecycle_audit_events`) in PostgreSQL (V3) and SQLite (V8) recording event type, key ID, purpose, owner, actor, result, and timestamp.
* **Alternatives Considered:** Relying solely on application logging or updating mutable columns in `key_registry`. Rejected because mutable records do not preserve transition history.
* **Security Implications:** Non-repudiable audit log enabling security forensics in the event of suspected key compromise or unauthorized rotation.
* **Consequences:** `KeyRegistryService` publishes immutable audit events on every lifecycle state transition.

---

### ADR-031: Pure Deterministic Canonical Verification Invariant
* **Context:** In naive implementations, verifiers trust a database column (such as `signed = true` or `verified = 1`) or verify signatures against transient in-memory objects rather than stored canonical bytes.
* **Decision:** Mandate that credential verification MUST recompute the SHA-256 digest directly from the persisted `canonical_payload` bytes, re-decode the canonical binary payload using `CanonicalCredentialDecoder`, and verify the RSASSA-PSS signature against the reconstructed claim hash.
* **Security Implications:** Eliminates database tampering attacks, guarantees that payload changes immediately invalidate signatures, and ensures verifiers are mathematically coupled to the exact bytes signed by the issuer.
* **Consequences:** `CredentialService.verify` operates exclusively on reconstructed canonical bytes.

---

### ADR-032: Issuer Authority & Operational Status Enforcement in Issuance Chain
* **Context:** A revoked or suspended issuing authority could continue issuing or having its previously issued credentials treated as valid if verification only checked key math.
* **Decision:** Bind credential verification to the authoritative `IssuerStore`. If an issuer is `SUSPENDED` or `REVOKED`, credential verification fails closed regardless of whether the cryptographic key signature is valid.
* **Security Implications:** Prevents unauthorized issuance by rogue or compromised authorities and ensures administrative sanctions immediately take effect across all issued credentials.
* **Consequences:** `CredentialService` queries `IssuerStore` during both issuance and verification.

---

### ADR-033: Strict Owner-Bound Key Custody Signing
* **Context:** If a signing key ID could be passed without validating its owner, Issuer A could invoke a signing key registered to Issuer B, forging valid credentials under another entity's key.
* **Decision:** Enforce in `KeyRegistryService.sign` that the requesting issuer identity (`expectedOwnerId`) strictly matches the registered `ownerId` of the key, and that the key's purpose is strictly `CREDENTIAL_SIGNING`.
* **Security Implications:** Blocks cross-tenant key impersonation and privilege escalation in multi-issuer environments.
* **Consequences:** Signing requests must provide explicit owner identity and will fail closed if an owner mismatch occurs.

---

### ADR-034: Historical Key Resolution & Revocation Fallback Policy
* **Context:** When an issuer rotates signing keys annually, previously issued credentials must remain verifiable using retired keys, while compromised keys that are revoked must immediately fail closed.
* **Decision:** When verifying historical credentials, `CredentialService` utilizes `KeyRegistryService.verifyHistorical` which allows `RETIRED` keys if the credential was issued prior to retirement/revocation, but unconditionally rejects `REVOKED` keys.
* **Security Implications:** Maintains long-term verifiability of legitimately issued historical records while ensuring immediate fail-closed invalidation for compromised credentials.
* **Consequences:** Keys in `RETIRING` and `RETIRED` states participate in verification but cannot sign new credentials.

---

### ADR-035: Granular Structured Verification Result Model
* **Context:** Returning a simple boolean `verified: true/false` obscures whether a failure was caused by canonical payload tampering, signature corruption, key revocation, issuer suspension, or credential expiration.
* **Decision:** Enhance `CredentialVerificationResult` into a rich, structured record containing independent boolean trust indicators: `digestMatches`, `signatureValid`, `issuerActive`, `keyTrusted`, `operationalStatusValid`, and a diagnostic `failureReason`.
* **Security Implications:** Enables verifiers and automated security auditors to diagnose exact failure points and distinguish between malicious forgery attempts and routine expiration.
* **Consequences:** Verification responses provide transparent cryptographic and lifecycle diagnostics.

---

### ADR-036: Strict Separation of Issuance DTOs and In-Memory Domain Model
* **Context:** Exposing internal domain constructors directly on REST controllers risks parameter injection, unvalidated timestamps, and forged status fields.
* **Decision:** Maintain `IssueCredentialRequest` as a transport DTO that maps into `CredentialDraft`, while `CredentialController` validates the authenticated caller's identity against the requested `issuerId`.
* **Security Implications:** Prevents unauthorized principals from forging credentials under other issuer identities or setting inappropriate initial lifecycle states.
* **Consequences:** Credential creation is mediated through validated draft builders and security filters.



---

### ADR-037: Pedersen Commitment Scheme over Ristretto/Curve25519
* **Context:** Verifiable credentials require privacy-preserving attribute commitments that support both information-theoretic hiding and subsequent zero-knowledge range proofs without revealing raw values.
* **Decision:** Adopt Pedersen commitments over Curve25519 / Ristretto group ($C = vG + rH$) using Weavechain Bulletproofs generators with orthogonal generator points.
* **Security Implications:** Guarantees perfect hiding (adversary with unbounded computational power cannot determine $v$) and computational binding under the discrete logarithm assumption.
* **Consequences:** Commitments produce compact 32-byte compressed point representations compatible with Bulletproof range proof gadgets.

---

### ADR-038: Dual Artifact Separation (AttributeCommitment vs AttributeOpeningSecret)
* **Context:** In naive commitment implementations, the blinding factor $r$ is stored in the database or returned alongside public credentials, violating zero-knowledge boundaries and creating privacy leaks.
* **Decision:** Strictly decouple public commitment persistence (`AttributeCommitment`) from holder secret opening custody (`AttributeOpeningSecret`). Only the public record is saved to the relational database; the opening secret is returned once to the caller upon issuance.
* **Security Implications:** Eliminates data breach risks where compromised database dumps could expose blinding factors and deanonymize attributes.
* **Consequences:** Verifiers and database storage hold only $C$ and blinding fingerprint (SHA-256 hash of $r$).

---

### ADR-039: Attestation Signature Binding via KeyPurpose.COMMITMENT_ATTESTATION_SIGNING
* **Context:** Commitments without issuer attestations can be freely generated by provers to satisfy predicates with arbitrary values. Additionally, reusing general credential signing keys for commitment attestations creates purpose confusion.
* **Decision:** Require RSASSA-PSS digital signatures over deterministic attestation payloads (`MAGIC || Version || CredentialId || AttributeName || CommitmentBytes`) authorized strictly under `KeyPurpose.COMMITMENT_ATTESTATION_SIGNING`.
* **Security Implications:** Binds the commitment mathematically to the specific credential and attribute, preventing commitment swapping or credential injection attacks.
* **Consequences:** Verification fails closed if the attestation signature was generated under another key purpose or for a different attribute.

---

### ADR-040: Verifier Direct Commitment Resolution Boundary
* **Context:** Accepting client-supplied commitments on verification endpoints allows malicious provers to forge commitments to fake values that satisfy thresholds.
* **Decision:** The predicate verifier strictly resolves the authoritative commitment point from the trusted `CommitmentStore` using `(credentialId, attributeName)` and confirms that the credential is active in `CredentialTrustVerifier`. Any client request attempting to supply a commitment point is rejected.
* **Security Implications:** Completely neutralizes client-side commitment tampering and forgery attacks.
* **Consequences:** Provers only submit zero-knowledge range proofs; the verifier queries the authoritative database record.

---

### ADR-041: Mathematical Opening Verification Contract
* **Context:** Holder wallets and client-side testing routines must verify that their held blinding secret $r$ and attribute value $v$ correctly open the issued commitment point $C$ before constructing zero-knowledge proofs.
* **Decision:** Expose `verifyOpening(long purportedValue, byte[] blindingBytes, byte[] commitmentBytes)` in `PedersenCommitmentService` to re-evaluate $C' = vG + rH$ and perform constant-time comparison against $C$.
* **Security Implications:** Ensures holders detect corrupt or malicious issuance material locally before attempting network proof generation.
* **Consequences:** Facilitates automated testing and client-side proof validation.

---

### ADR-042: Blinding Factor Fingerprint Uniqueness & Anti-Collision Retry
* **Context:** Multiple commitments sharing the same blinding factor allow correlation across credentials or attributes.
* **Decision:** Enforce unique constraint on `blinding_fingerprint` (SHA-256 of $r$) in the persistence schema and implement automated retry logic in `PedersenCommitmentService` with fresh random scalar generation on collision.
* **Security Implications:** Ensures cryptographic freshness and prevents cross-attribute linkability or blinding reuse.
* **Consequences:** Up to 10 retry attempts with secure random regeneration ensure high resilience under concurrent issuance.
