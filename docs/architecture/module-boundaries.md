# Zero-Knowledge Document Verification Platform
## Module Boundaries & Dependency Architecture Specification

**Document Version:** 1.0.0  
**Phase:** Phase 1 — Project Foundation & Engineering Infrastructure  
**Status:** Approved Architectural Baseline  

---

### 1. Architectural Overview

The platform is structured as an enterprise-grade **modular monolith**. High-risk security domains and cryptographic components are strictly segregated into distinct Maven modules with unidirectional dependency graphs to prevent leaky abstractions, accidental coupling, and cyclic dependencies.

```
                    +---------------------------+
                    |        zkdoc-api          |
                    | (REST, Security, Actuator)|
                    +---------------------------+
                       /          |          \
                      /           |           \
                     v            v            v
        +----------------+ +---------------+ +---------------+
        | zkdoc-document | |zkdoc-credential| |  zkdoc-keys   |
        +----------------+ +---------------+ +---------------+
                 \                |                 /
                  \               v                /
                   \       +-------------+        /
                    \      |  zkdoc-zkp  |       /
                     \     +-------------+      /
                      \           |            /
                       v          v           v
                    +---------------------------+
                    |    zkdoc-core-crypto      |
                    |  (Pure SPI Abstraction)   |
                    +---------------------------+
```

---

### 2. Module Responsibilities & Contracts

#### 2.1. `zkdoc-core-crypto`
* **Role:** Pure cryptographic abstraction SPI (Service Provider Interface).
* **Forbidden Dependencies:**
  * Must **never** depend on Spring Web (`org.springframework.web..`), Servlet API (`jakarta.servlet..`), or HTTP transports.
  * Must **never** depend on persistence frameworks (`org.springframework.jdbc..`, JPA, `java.sql..`).
  * Must **never** depend on presentation or DTO models.
* **Exports:** SPI contracts for `DigestService`, `SignatureService`, `KeyProvider`, `CommitmentService`, `RangeProofEngine`, and `CanonicalizationService`.

#### 2.2. `zkdoc-keys`
* **Role:** Public key registry lifecycle, RSA key metadata management, and key state transitions (`ACTIVE`, `RETIRING`, `RETIRED`, `REVOKED`).
* **Dependencies:** Depends strictly on `zkdoc-core-crypto` and Spring Data/JDBC infrastructure.

#### 2.3. `zkdoc-document`
* **Role:** Document metadata handling, SHA-256 integrity calculation, RSA-PSS signature verification, and capability token generation.
* **Dependencies:** Depends on `zkdoc-core-crypto`, `zkdoc-keys`. Does not depend on ZKP or presentation layers.

#### 2.4. `zkdoc-credential`
* **Role:** Verifiable credential representation, RFC 8785 canonical JSON serialization, and cryptographic authenticity verification.
* **Dependencies:** Depends on `zkdoc-core-crypto` and `zkdoc-keys`.

#### 2.5. `zkdoc-zkp`
* **Role:** Zero-knowledge predicate range proof generation and verification (Bulletproofs over Ristretto255 Pedersen commitments).
* **Dependencies:** Depends on `zkdoc-core-crypto` and `zkdoc-credential`. Third-party curve and proof libraries are encapsulated behind SPI boundaries.

#### 2.6. `zkdoc-api`
* **Role:** Edge HTTP presentation layer, Spring Security filters, OAuth2/JWT token parsing, rate limiting, and Actuator monitoring.
* **Dependencies:** Aggregates domain modules. REST controllers exist **only** within `com.zkdoc.api..`.

---

### 3. Boundary Enforcement Strategy

1. **Maven Multi-Module Enclosure:** Module compilation classpaths enforce compile-time physical boundaries.
2. **ArchUnit Automated Rules:** Executed as part of `mvn test` in `ArchitectureBoundaryTest`:
   * Asserts pure separation of `com.zkdoc.core.crypto..` from web and database classes.
   * Asserts controller encapsulation exclusively within `com.zkdoc.api..`.
   * Enforces public visibility of core SPI contracts.
