# Zero-Knowledge Document Verification Platform
## Local Development Guide

**Document Version:** 1.0.0  
**Phase:** Phase 1 — Project Foundation & Engineering Infrastructure  

---

### 1. Prerequisites

* **Java Development Kit:** Eclipse Temurin JDK 21 LTS installed and set as `JAVA_HOME`.
* **Build System:** Apache Maven 3.9+ (or bundled `./mvnw` / `mvnw.cmd`).
* **Node.js:** Node.js v20+ / npm v10+ (for frontend bundling via `frontend-maven-plugin`).
* **Container Runtime (Optional for Postgres):** Docker Engine / Docker Desktop with Compose v2.

---

### 2. Quick Start Options

#### Option A: Containerized PostgreSQL Local Stack (Recommended)
1. Start PostgreSQL 16 via Docker Compose:
   ```bash
   docker compose up -d postgres
   ```
2. Run the Spring Boot API with the `postgres` profile:
   ```bash
   ./mvnw spring-boot:run -pl zkdoc-api -Dspring-boot.run.profiles=postgres
   ```
   Or in PowerShell:
   ```powershell
   .\mvnw.cmd spring-boot:run -pl zkdoc-api -Dspring-boot.run.profiles=postgres
   ```

#### Option B: Standalone Lightweight Local Run (SQLite)
If Docker is not running on your workstation, you can run entirely in-process using SQLite:
```powershell
.\mvnw.cmd spring-boot:run -pl zkdoc-api
```
The active profile defaults to `local`, automatically initializing `./data/zkdoc.db` with SQLite Flyway migrations.

---

### 3. Verifying Local Health & Probes

Once the service starts on port 8080:
* **Liveness:** `http://localhost:8080/actuator/health/liveness` -> `{"status":"UP"}`
* **Readiness:** `http://localhost:8080/actuator/health/readiness` -> `{"status":"UP"}`
* **API Health:** `http://localhost:8080/api/health` -> `{"status":"UP"}`
