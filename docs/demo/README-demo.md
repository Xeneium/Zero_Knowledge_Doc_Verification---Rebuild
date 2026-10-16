# 🚀 ZKDoc Platform — Quick Demonstration & Evaluation Guide

Welcome to the **Zero-Knowledge Document Verification Platform (ZKDoc)** demonstration environment. This guide is crafted for project evaluators, faculty reviewers, interviewers, and technical auditors who wish to quickly launch, test, and understand the platform.

---

## ⚡ 60-Second Quick Start

### Option A: Interactive Assistant (Recommended)

Run the automated setup assistant from the repository root:

**On Windows (PowerShell):**
```powershell
.\scripts\demo-setup.ps1
```

**On Linux / macOS (Bash):**
```bash
chmod +x ./scripts/demo-setup.sh ./scripts/generate-demo-tokens.sh
./scripts/demo-setup.sh
```

The script will:
1. Verify **Java 21 LTS** installation.
2. Confirm synthetic PDF test fixtures (`demo-degree-diploma.pdf`, `demo-degree-diploma-tampered.pdf`, `demo-training-certificate.pdf`).
3. Ensure artifact storage directory (`./data/artifacts`).
4. Boot the Spring Boot application with `--zkdoc.demo.seed-enabled=true`.

---

### Option B: Direct Maven Command

If you prefer launching directly via Maven:

**Windows:**
```powershell
.\mvnw.cmd spring-boot:run -pl zkdoc-api -Dspring-boot.run.arguments="--zkdoc.demo.seed-enabled=true"
```

**Linux / macOS:**
```bash
./mvnw spring-boot:run -pl zkdoc-api -Dspring-boot.run.arguments="--zkdoc.demo.seed-enabled=true"
```

Once started, open your browser at **[http://localhost:8080](http://localhost:8080)**.

---

### Option C: Docker Compose

```bash
docker compose up --build
```
This builds and launches the full stack including the containerized PostgreSQL database and Spring Boot backend.

---

## 🔑 Pre-Seeded Demonstration Accounts

When `zkdoc.demo.seed-enabled=true`, the platform seeds 4 realistic, synthetic test identities equipped with deterministic RS256 Bearer JWTs:

| Role | Username | Subject URN | Core Responsibilities & Demo Workflows |
| :--- | :--- | :--- | :--- |
| **`ADMIN`** | `admin-operator` | `urn:zkdoc:admin:operator-root` | • System trust anchor management<br/>• Key lifecycle (register / revoke signing keys)<br/>• Immutable audit log inspection and SHA-256 hash-chain verification |
| **`ISSUER`** | `issuer-agent` | `urn:zkdoc:issuer:acme-university` | • Document intake and SHA-256 fingerprinting<br/>• RSASSA-PSS digital signature application<br/>• Dynamic credential issuance & Pedersen commitment generation |
| **`HOLDER`** | `alice-chen` | `urn:zkdoc:holder:alice-student` | • Identity wallet holding pre-issued degree credential<br/>• Client-side generation of real Bulletproof range proofs (`GPA >= 3.50`) without revealing GPA<br/>• Time-bound QR capability export |
| **`VERIFIER`** | `verifier-auditor` | `urn:zkdoc:verifier:regulator-agent` | • Public document authenticity verification (Mode A / Mode B)<br/>• QR-code verification (camera scan or file drag-and-drop)<br/>• Cryptographic proof evaluation against commitment anchors |

### 1-Click Login (Evaluation Mode)
When you navigate to **[http://localhost:8080/login](http://localhost:8080/login)**, a **"Demo Quick Connect"** panel will appear above the token form. Clicking any role automatically loads its cryptographically signed token, triggers live claim decoding in the preview card, and allows instant session connection.

Alternatively, fetch raw tokens via CLI at any time:
```powershell
.\scripts\generate-demo-tokens.ps1
```

---

## 🧭 Key Demonstration Routes

- **Application Overview:** [http://localhost:8080/](http://localhost:8080/)
- **Session Authentication / Quick-Connect:** [http://localhost:8080/login](http://localhost:8080/login)
- **Public Document & Capability Verifier:** [http://localhost:8080/verify](http://localhost:8080/verify)
- **Holder Wallet & ZKP Prover:** [http://localhost:8080/credentials](http://localhost:8080/credentials)
- **Issuer Document & Credential Studio:** [http://localhost:8080/documents](http://localhost:8080/documents)
- **System Administration & Audit Explorer:** [http://localhost:8080/admin](http://localhost:8080/admin)
- **System Health Actuator:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Demo Tokens Public REST API:** [http://localhost:8080/api/public/demo-tokens](http://localhost:8080/api/public/demo-tokens)

---

## 📂 Pre-Generated Synthetic Test Fixtures

Test documents are located in [`demo/fixtures/`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/demo/fixtures):

1. **`demo-degree-diploma.pdf`**:
   - **Type:** Official Bachelor of Science Diploma issued to Alice Chen.
   - **Properties:** GPA 3.80, Honors Magna Cum Laude, SHA-256: `cc5e040169f6139608b9649c83cc8c936470afc030bfe9d04530858f5675db80`.
   - **Purpose:** Used for happy-path verification and zero-knowledge predicate proof generation.
2. **`demo-degree-diploma-tampered.pdf`**:
   - **Type:** Modified clone of the above diploma with forged GPA ("4.00") and forged honors ("Summa Cum Laude").
   - **Properties:** SHA-256: `5bc2b6811613db86ecde175a32cbff585d9728a7197ffea88b067cfaa715934b`.
   - **Purpose:** Demonstrates cryptographic tamper detection (hash mismatch).
3. **`demo-training-certificate.pdf`**:
   - **Type:** Cloud Security Architect training completion certificate.
   - **Purpose:** Secondary credential for multi-credential workflows.

---

## 📚 Complete Demonstration Documentation

Explore the detailed manuals in this suite:

1. **[Data Catalog](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/demo/demo-data-catalog.md):** Complete breakdown of all synthetic users, keys, commitments, and documents.
2. **[Architecture & Trust Boundary](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/demo/demo-architecture.md):** Architectural diagrams, data flow, cryptographic primitive mappings, and minimal-disclosure enforcement.
3. **[Live Presentation Script](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/demo/demo-presentation-script.md):** Step-by-step 8–12 minute live demonstration script with exact talk tracks and recovery fallbacks.
4. **[Viva Q&A Guide](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/demo/demo-viva-questions.md):** 25+ academic and technical defense questions with defensible answers.
5. **[Troubleshooting Guide](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/demo/demo-troubleshooting.md):** Diagnostic playbooks for port collisions, token errors, camera issues, and database state.
