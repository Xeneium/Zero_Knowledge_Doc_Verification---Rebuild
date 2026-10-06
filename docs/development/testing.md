# Zero-Knowledge Document Verification Platform
## Testing Architecture & Quality Gates

**Document Version:** 1.0.0  
**Phase:** Phase 1 — Project Foundation & Engineering Infrastructure  

---

### 1. Test Pyramid & Conventions

The platform enforces a multi-tiered test strategy:

1. **Unit Tests (JUnit 5 + Mockito):** Fast, deterministic, in-memory validation of isolated algorithms, canonical encoders, and SPI interfaces.
2. **Architecture Tests (ArchUnit):** Enforce layer encapsulation, module dependencies, and naming conventions (`ArchitectureBoundaryTest`).
3. **Database Integration Tests (Testcontainers + PostgreSQL 16):** Validate Flyway migration scripts and schema integrity against a real PostgreSQL instance (`PostgresFlywayMigrationIntegrationTest`).
4. **Security & Controller Tests (MockMvc + Spring Security Test):** Validate authentication barriers, role-based access control, and adversarial request rejection.

---

### 2. Executing Tests via CLI

* Run all unit tests, architecture tests, and integration tests:
  ```powershell
  .\mvnw.cmd test
  ```
* Run a specific test class:
  ```powershell
  .\mvnw.cmd test -Dtest=ArchitectureBoundaryTest
  ```
* Full build verification:
  ```powershell
  .\mvnw.cmd clean verify
  ```

---

### 3. Docker Environment Handling

* `AbstractPostgresIntegrationTest` uses `@Testcontainers(disabledWithoutDocker = true)` and `@EnabledIf("isDockerAvailable")`.
* When run on developer workstations where Docker is offline, PostgreSQL integration tests are gracefully skipped without failing the unit test build.
* In GitHub Actions CI (where Docker is always present), the full PostgreSQL Testcontainers suite executes automatically.
