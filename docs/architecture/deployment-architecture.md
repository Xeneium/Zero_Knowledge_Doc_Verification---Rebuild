# Zero-Knowledge Document Verification Platform
## Deployment Architecture Specification

**Document Version:** 1.0.0  
**Phase:** Phase 1 — Project Foundation & Engineering Infrastructure  
**Status:** Approved Architectural Baseline  

---

### 1. Deployment Topology

The platform is container-native and designed for horizontal scalability in modern container orchestration environments (Kubernetes, AWS ECS, or Docker Compose).

```
                      +-----------------------------+
                      | TLS Termination Load Balancer|
                      +-----------------------------+
                                     |
                     +---------------+---------------+
                     |                               |
                     v                               v
         +-----------------------+       +-----------------------+
         | zkdoc-api Container   |       | zkdoc-api Container   |
         | (Spring Boot / JRE 21)|       | (Spring Boot / JRE 21)|
         +-----------------------+       +-----------------------+
                     \                               /
                      \                             /
                       v                           v
              +---------------------------------------------+
              |           PostgreSQL 16+ Database           |
              |       (Flyway Migrations + HikariCP)        |
              +---------------------------------------------+
```

---

### 2. Runtime Environment & Container Hardening

* **Base Image:** `eclipse-temurin:21-jre-alpine` for minimal attack surface.
* **Unprivileged User:** The application runs as non-root system user `zkdoc:zkdoc` (UID/GID 10001).
* **Memory Management:** JVM container awareness configured via `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0`.
* **Entropy Generation:** Configured with `-Djava.security.egd=file:/dev/./urandom` for non-blocking secure cryptographic entropy.

---

### 3. Health & Readiness Probes

The container exposes Spring Boot Actuator endpoints on port 8080:

* **Liveness Probe:** `/actuator/health/liveness`
  * Validates internal application process state.
* **Readiness Probe:** `/actuator/health/readiness`
  * Validates database connectivity and connection pool health before admitting ingress traffic.
* **Security Constraints:** Health details are suppressed for unauthenticated traffic (`show-details: when-authorized`) to prevent disclosing internal topology.

---

### 4. Configuration & Secret Injection

* Secrets (database credentials, JWT signing keys) must **never** be baked into container images or checked into Git.
* In production, configuration is externalized via environment variables:
  * `SPRING_PROFILES_ACTIVE=postgres`
  * `SPRING_DATASOURCE_URL=jdbc:postgresql://<db-host>:5432/zkdoc`
  * `SPRING_DATASOURCE_USERNAME=<db-user>`
  * `SPRING_DATASOURCE_PASSWORD=<db-secret>` (injected from Kubernetes Secrets / AWS Secrets Manager)
