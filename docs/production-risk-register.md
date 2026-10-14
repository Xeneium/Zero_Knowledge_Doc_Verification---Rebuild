# Production Risk Register — Zero-Knowledge Document Verification Platform

**Document Version:** 1.0.0  
**Phase:** 24 — Production Readiness Assessment & Final Security Gate  
**Status:** Approved for Conditional Go  
**Last Evaluated:** October 2026  

---

## 1. Risk Methodology & Scoring Matrix

Risks are quantified based on standard risk scoring methodologies:
$$\text{Risk Score} = \text{Likelihood (1–5)} \times \text{Impact (1–5)}$$

| Score Range | Severity Level | Definition & Operational Action |
| :--- | :--- | :--- |
| **16 – 25** | **Critical** | Block deployment until remediation is applied and validated. |
| **10 – 15** | **High** | Requires formal acceptance by Chief Information Security Officer (CISO) / Lead Architect with scheduled mitigation roadmap. |
| **5 – 9** | **Medium** | Acceptable for controlled pilot deployment under operational monitoring. |
| **1 – 4** | **Low** | Negligible operational impact; address during routine maintenance cycles. |

---

## 2. Comprehensive Risk Matrix

| Risk ID | Category | Risk Description | Likelihood (1-5) | Impact (1-5) | Initial Score | Mitigation Strategy & Controls | Residual Risk | Risk Owner | Acceptance Criteria |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- | :---: | :--- | :--- |
| **R-01** | **Scalability / DoS** | **In-Memory Rate Limiting Multi-Node Inconsistency**: Rate limiting uses in-memory `ConcurrentHashMap` with Bucket4j. If deployed across multiple horizontal application replicas without sticky sessions or distributed store, quotas multiply by replica count. | 4 | 3 | **12 (High)** | For single-node pilot, sticky session routing or single instance container deployment. For multi-node cluster, integrate distributed Redis-backed Bucket4j store (`bucket4j-redis`). | Low (single-node) / Medium (cluster) | Lead Backend Engineer | Rate limits enforced strictly per IP/token regardless of node distribution. |
| **R-02** | **Key Management** | **Software-Based RSA Private Key Storage**: System signing private keys are loaded from PEM files / environment variables rather than dedicated Hardware Security Module (HSM) or cloud KMS (AWS KMS, Azure Key Vault, HashiCorp Vault). Memory dumping or container filesystem compromise could expose issuer key. | 2 | 5 | **10 (High)** | Inject signing keys strictly via ephemeral container secrets mounted at runtime; restrict filesystem read permissions (0400/root only); enforce key rotation protocol. Plan PKCS#11 / HSM provider integration for enterprise tier. | Medium | Lead Security Architect | Private keys never committed to version control; memory dumps disabled via core dump flags; keys rotated every 90 days. |
| **R-03** | **Availability / DR** | **Single PostgreSQL Primary in Default Compose**: Production docker-compose deploys a standalone PostgreSQL 16 container without automated streaming replication or failover clustering (e.g., Patroni / AWS RDS Multi-AZ). | 3 | 4 | **12 (High)** | Deploy managed PostgreSQL (RDS, Cloud SQL, or High-Availability Patroni cluster) for production. In Compose pilot, execute automated hourly WAL archiving and daily snapshot restores verified via `verify-restore.sh`. | Medium | DevOps / SRE Lead | RPO $\le$ 1 hour, RTO $\le$ 30 minutes validated via quarterly recovery drills. |
| **R-04** | **Storage / Cluster** | **Local Filesystem Document Storage**: Uploaded documents and proof artifacts are persisted to local container volume mount (`/app/data/storage`). Horizontal scaling causes partitioned file accessibility. | 3 | 3 | **9 (Med)** | For single container pilot, dedicated persistent volume mount. For clustered deployment, abstract storage layer via S3-compatible object storage (MinIO, AWS S3, Azure Blob) with server-side AES-256 encryption. | Low | Platform Architect | All document binaries accessible uniformly by any servicing backend instance; SHA-256 hash verified upon read. |
| **R-05** | **Cryptographic Replay** | **Cross-Verifier Nonce Replay Vulnerability**: Bulletproof predicate proofs include cryptographically generated nonces to prevent replay against a single verifier. If distinct verifiers operate independently without shared replay state, a proof could theoretically be presented to Verifier B within its validity window. | 2 | 2 | **4 (Low)** | Enforce short epoch timestamps in proof statements ($\le$ 5 minutes); include verifier identity (`verifierId` / audience claim) inside the proof commitment context; maintain Redis distributed nonce cache across verifier nodes. | Low | Cryptographic Engineer | Prover nonces strictly rejected if presented past validity TTL or to mismatched `verifierId`. |
| **R-06** | **Network Security** | **Upstream TLS Termination Dependency**: Backend Spring Boot container operates over HTTP internally on port 8080 and relies on upstream reverse proxy (Nginx, Traefik, AWS ALB) for TLS 1.3 termination, HSTS header injection, and WAF inspection. Direct exposure bypasses transport encryption. | 2 | 5 | **10 (High)** | Bind Spring Boot port 8080 strictly to localhost or private Docker overlay network (`zkdoc-net`). Prohibit public port mapping of 8080 on the host interface. Reverse proxy must strictly enforce TLS 1.3 and forward client IP via trusted headers. | Low | SRE / Network Security | Direct external HTTP requests to 8080 are unreachable; all public ingress requires HTTPS with valid certificates. |
| **R-07** | **Authentication** | **JWT Secret Entropy & Invalidation**: Weak or static `ZKDOC_JWT_SECRET` in environment could allow signature forgery; absence of server-side token revocation list prevents immediate revocation before token expiry (15m default). | 2 | 4 | **8 (Med)** | Enforce minimum 256-bit high-entropy secret generation on container boot; enforce short token lifetimes (15 minutes access, 7 days refresh with database revocation tracking in `refresh_token` table). | Low | Backend Security Engineer | System halts startup if `ZKDOC_JWT_SECRET` has $<32$ characters; revoked refresh tokens immediately terminate session. |
| **R-08** | **Data Integrity** | **Concurrent Credential State Transitions**: Simultaneous requests to revoke or suspend a credential could theoretically race if optimistic locking were absent or isolation level permitted dirty reads. | 1 | 4 | **4 (Low)** | Entity-level optimistic locking with `@Version` on `CredentialEntity`; `@Transactional(isolation = Isolation.READ_COMMITTED)` on state transitions; verified by 50-thread concurrent state transition test suite. | Negligible | Database Engineer | Zero state corruption or race conditions observed across 50 concurrent revocation attempts. |
| **R-09** | **Compliance / Legal** | **User Misunderstanding of ZKP Claims**: End-users or auditors assuming "Zero-Knowledge" implies arbitrary computation circuits or complete anonymization across all verification workflows. | 4 | 2 | **8 (Med)** | Publish clear capability matrix in API documentation and `/api/capabilities` endpoint: distinguish SHA-256 hash matching, RSA-PSS signature verification, and Pedersen commitment Bulletproof range proofs. Do not claim arbitrary zk-SNARK capabilities. | Low | Product & Compliance Lead | Public documentation, API OpenAPI specs, and user UI explicitly state cryptographic primitives employed. |
| **R-10** | **Operational Monitoring** | **Metrics & Alerting Blind Spots in Container Restarts**: Container crashes or high JVM heap exhaustion during large file hashing (up to 50MB) could cause unmonitored downtime if alerting is unconfigured. | 2 | 3 | **6 (Med)** | Streaming SHA-256 hashing (constant $O(1)$ memory consumption); Prometheus metrics exported via `/actuator/prometheus`; health probe monitoring via Docker healthchecks and liveness probes; alerts configured for heap usage $>80\%$. | Low | DevOps / SRE Lead | Heap memory stays under 256MB during concurrent 50MB file uploads; Prometheus metrics actively scraped. |

---

## 3. High & Medium Risk Treatment Action Plan

### 3.1. R-01 (In-Memory Rate Limiting Cluster Inconsistency)
- **Immediate Pilot Action:** Deploy on single application container or configure sticky routing on load balancer. Limit host concurrency to configured memory thresholds.
- **Phase 25 Roadmap Action:** Migrate `Bucket4j` backing cache to `LettuceBasedProxyManager` backed by a Redis cluster.

### 3.2. R-02 (Software RSA Private Key Management)
- **Immediate Pilot Action:** Secure key files with Unix permissions `0400`, owned by non-root UID 10001 (`appuser`). Inject keys via Docker secrets or Kubernetes secrets. Prevent core dumps using `ulimit -c 0`.
- **Phase 25 Roadmap Action:** Implement SunPKCS11 provider integration to interface with AWS CloudHSM or YubiKey HSM.

### 3.3. R-03 (Single PostgreSQL Database Node)
- **Immediate Pilot Action:** Configure automated cron-driven backup script (`backup-db.sh`) with hourly pg_dump, SHA-256 backup verification, and weekly restore validation.
- **Phase 25 Roadmap Action:** Migrate database to AWS RDS Multi-AZ or Patroni high-availability replication cluster with automated failover.

### 3.4. R-06 (Upstream TLS Termination Dependency)
- **Immediate Pilot Action:** Ensure Docker Compose binding does not expose `0.0.0.0:8080`. Bind only to internal bridge network. Ensure Nginx or Traefik reverse proxy terminates TLS 1.3 with Mozilla Modern SSL configuration and enforces HSTS.
- **Phase 25 Roadmap Action:** Mutual TLS (mTLS) between reverse proxy and Spring Boot application containers.

---

## 4. Risk Acceptance Sign-off

| Role | Name / Title | Decision | Date |
| :--- | :--- | :---: | :--- |
| **Lead Cryptographic Engineer** | Cryptographic Review Team | **Accepted with Conditions** | October 2026 |
| **Security Architect / CISO** | Platform Security Gatekeeper | **Accepted with Conditions** | October 2026 |
| **Lead DevOps / SRE Engineer** | Infrastructure Operations Lead | **Accepted with Conditions** | October 2026 |
| **Lead Software Engineer** | Core Backend Engineering Lead | **Accepted with Conditions** | October 2026 |

*The Zero-Knowledge Document Verification Platform is accepted for **CONDITIONAL GO** deployment subject to the fulfillment of all mandatory preconditions set forth in the Production Deployment Checklist.*
