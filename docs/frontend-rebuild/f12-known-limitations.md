# Phase F12: Known Limitations, Deferred Work, and Architectural Boundaries

**Project:** Zero-Knowledge Document Verification Platform  
**Component:** System Architecture, Frontend Rebuild, and Deployment Model  
**Phase:** F12 (Production Acceptance, Deployment Readiness, and Final Quality Gate)  
**Date:** October 2026  
**Status:** Authoritative Architectural Boundaries Documented  

---

## 1. Overview & Purpose

This document provides a transparent, evidence-based inventory of known limitations, deferred capabilities, and architectural boundaries across the Zero-Knowledge Document Verification Platform following the Phase F12 acceptance audit. Documenting these boundaries ensures that operators, security reviewers, and platform engineers understand the exact operational limits of the current software baseline and do not make unwarranted assumptions regarding cryptographic or functional capabilities.

---

## 2. Cryptographic & Zero-Knowledge Proving Boundaries

### 2.1 Numeric Threshold Proofs vs General-Purpose ZKPs
- **Current Capability:**
  The platform implements zero-knowledge range/predicate proofs based on the Bulletproof protocol over the secp256k1 elliptic curve group. This supports verifiable statements of the form $\text{attribute} \ge \text{threshold}$ (or $\le$, $=$, etc.) over 64-bit signed integers (e.g. $GPA \ge 300$, $\text{age} \ge 21$) without revealing the underlying attribute value.
- **Limitation:**
  The system does **not** implement a general-purpose ZK-SNARK (e.g. Groth16, PLONK) or ZK-STARK proof system for arbitrary computational circuits. Complex multi-variate predicates, graph relationship proofs, or string matching proofs are not supported.
- **Operator Recommendation:**
  Do not represent the platform as a universal ZKP circuit compiler. Feature documentation should describe the cryptographic capability as "Zero-Knowledge Predicate & Range Verification via Bulletproofs".

### 2.2 In-Process Key Generation in Local Development
- **Current Capability:**
  The `local` profile generates document and credential signing keys in-process at application startup for frictionless local developer testing.
- **Limitation:**
  In-process startup keys do not survive application restarts. Any credentials or documents secured in local profile mode cannot be cryptographically verified after a server reboot.
- **Operator Recommendation:**
  Production deployments must activate the `postgres` or `prod` profile and provision persistent public key anchors in the `key_registry` table using keys stored in an external Key Management Service (KMS) or secure environment injection.

---

## 3. Backend & Routing Limitations

### 3.1 Direct Browser Address-Bar Deep-Linking (`DEF-F11-001`)
- **Limitation:**
  In `SpaForwardController.java`, the route forwarding mapping is:
  ```java
  @GetMapping(value = {"/", "/verify", "/credentials", "/verification", "/predicate-verification", "/audit-logs", "/administration"})
  public String forward() { return "forward:/index.html"; }
  ```
  The routes `/login`, `/issuer`, `/holder`, and `/forbidden` were added during the frontend rebuild and are not present in this array.
- **Impact:**
  - In-app navigation via client-side links works smoothly across all routes.
  - Refreshing or directly typing `http://localhost:8080/issuer` into an external browser address bar produces an HTTP 404 error from Spring Boot.
- **Recommended Follow-up:**
  Expand the `@GetMapping` array in `SpaForwardController.java` to encompass all client-side routes in the next backend maintenance release.

### 3.2 Jackson Deserialization Strictness (`DEF-F11-002`)
- **Limitation:**
  `CredentialPredicateController.java` strictly fails with HTTP 400 Bad Request if any JSON key outside of `{"proofVersion", "credentialId", "attributeName", "threshold", "nonce", "proofBytes"}` is passed.
- **Impact:**
  Client requests must adhere strictly to these 6 fields. Any client-side state properties accidentally forwarded in the JSON body cause verification rejection.
- **Mitigation:**
  `verifierApi.ts` explicitly constructs bounded payloads containing only these exact 6 properties.

---

## 4. Operational & Deployment Prerequisites

### 4.1 External Identity Provider (IdP) Prerequisite
- **Limitation:**
  The platform contains no built-in user registration, password management, or JWT minting server. It expects pre-signed RSASSA-PSS Bearer tokens issued by an external OAuth2/OIDC identity provider.
- **Required Action:**
  Deployers must operate an external IdP (Keycloak, Auth0, Okta, Authentik) and register the IdP's public signing key in the `key_registry` table with purpose `JWT_SIGNING`.

### 4.2 Reverse Proxy TLS Termination
- **Limitation:**
  The embedded Spring Boot container and standalone JAR run unencrypted HTTP on port 8080.
- **Required Action:**
  Direct public internet exposure of port 8080 is forbidden. Operators must place the service behind a reverse proxy (Nginx, Caddy, Cloudflare, AWS ALB) enforcing TLS 1.3, HSTS (`max-age=31536000`), and secure cookies.

### 4.3 Database Scalability & High Availability
- **Limitation:**
  The default development configuration uses single-file SQLite (`./data/zkdoc.db`). SQLite does not support horizontal scaling or concurrent high-throughput write operations.
- **Required Action:**
  Production deployments must utilize a managed PostgreSQL 16+ instance configured with automated backups, point-in-time recovery, and connection pooling.

---

## 5. Deferred Security & Feature Capabilities

| Feature / Capability | Classification | Current Status | Recommended Milestone |
|---|---|---|---|
| **Automated Silent JWT Token Refresh** | Deferred Feature | Explicit re-authentication on expiry; in-flight requests discarded gracefully via epoch guard. | Post-Release (F13) |
| **WebAuthn / Passkey Authentication** | Deferred Feature | Standard Bearer JWT intake supported. | Future Roadmap |
| **Hardware Security Module (HSM) PKCS#11** | Deferred Architecture | Environment-injected file keys supported; DB stores only public keys. | Enterprise Roadmap |
| **Push Notification & Webhook Dispatch** | Deferred Feature | Synchronous REST polling and manual queries supported. | Future Roadmap |
| **Multi-Tenancy Key Partitioning** | Deferred Feature | Role-based and owner-based tenancy enforced via object-level security. | Multi-Tenant Milestone |

---

## 6. Required Operational Decisions for Launch

Before deploying the platform into production, the following infrastructure decisions must be finalized by the project leadership:
1. **IdP Selection:** Confirm the enterprise identity provider and configure standard roles (`ISSUER`, `HOLDER`, `VERIFIER`, `ADMIN`).
2. **Key Custody Architecture:** Decide whether document signing keys will be stored in AWS KMS, Azure Key Vault, HashiCorp Vault, or encrypted local volume mounts.
3. **Backup Encryption Secret:** Generate and securely store a 256-bit passphrase for `BACKUP_ENCRYPTION_KEY` and `BACKUP_HMAC_KEY`.
4. **Disaster Recovery Target:** Establish recovery time objective (RTO $\le 1$ hour) and recovery point objective (RPO $\le 24$ hours) using the automated backup scripts.
