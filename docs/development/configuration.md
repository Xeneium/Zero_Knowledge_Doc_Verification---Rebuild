# Zero-Knowledge Document Verification Platform
## Configuration Architecture & Secrets Baseline

**Document Version:** 1.0.0  
**Phase:** Phase 1 — Project Foundation & Engineering Infrastructure  

---

### 1. Configuration Separation

Configuration follows the Twelve-Factor App methodology:

```
[Default / Common] (application.yml)
  ├── Server limits & Tomcat buffers (25MB)
  ├── Actuator probes & endpoints (health, info)
  ├── Structured MDC logging patterns
  └── Default profile activation (local)
        │
        ├── [Profile: local] -> SQLite, in-memory, classpath:db/migration/sqlite
        │
        └── [Profile: postgres / prod] -> PostgreSQL 16+, HikariCP, classpath:db/migration/postgresql
```

---

### 2. Environment Variables Reference

| Variable | Description | Default | Security Sensitivity |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `local` | Low |
| `SERVER_PORT` | HTTP listening port | `8080` | Low |
| `SPRING_DATASOURCE_URL` | JDBC Connection URL | `jdbc:postgresql://localhost:5432/zkdoc` | Medium |
| `SPRING_DATASOURCE_USERNAME`| PostgreSQL Database user | `zkdoc_app` | Medium |
| `SPRING_DATASOURCE_PASSWORD`| PostgreSQL Database password | `""` | **High (Secret)** |
| `HIKARI_MAX_POOL_SIZE` | Max HikariCP pool connections| `10` | Low |
| `ZKDOC_PUBLIC_VERIFICATION_BASE_URL` | Public verification host | `http://localhost:8080` | Low |

---

### 3. Secrets Hygiene & Sanitization Policy

* **Zero Plaintext Secrets in Source:** No private keys, database passwords, or JWT signing secrets may ever be committed to Git.
* **Gitleaks Pre-Commit Scans:** Automated scanning is configured via `.gitleaks.toml` and verified in CI.
* **Developer Environment:** Use `.env.example` as a template. The active `.env` file is excluded via `.gitignore`.
