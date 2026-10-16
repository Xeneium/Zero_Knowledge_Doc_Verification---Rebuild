# 🎓 ZKDoc Platform — Viva Defense & Technical Q&A Guide

This guide equips students, developers, and presenters with rigorous, mathematically and architecturally defensible answers to 25 common viva, oral examination, and technical audit questions regarding the **Zero-Knowledge Document Verification Platform**.

---

## 📑 Question Categories
1. [Cryptography & Zero-Knowledge Proofs](#1-cryptography--zero-knowledge-proofs)
2. [Java 21 & Backend Architecture](#2-java-21--backend-architecture)
3. [Security, Privacy & Threat Modeling](#3-security-privacy--threat-modeling)
4. [Verification Workflows & Edge Cases](#4-verification-workflows--edge-cases)

---

## 1. Cryptography & Zero-Knowledge Proofs

### Q1: What specific zero-knowledge proof system does ZKDoc implement, and why?
**Answer:**
ZKDoc implements **Bulletproof range proofs** over 64-bit unsigned integers, integrated via the Weavechain cryptographic engine. We chose Bulletproofs over zk-SNARKs (e.g., Groth16) because:
1. **No Trusted Setup:** zk-SNARKs require a complex multi-party computation ("toxic waste" ceremony) to generate proving and verification keys. If compromised, the system's soundness is broken. Bulletproofs rely strictly on the Discrete Logarithm assumption over standard elliptic curves and require **zero trusted setup**.
2. **Compact Range Proofs:** Bulletproofs were specifically designed for range proofs ($v \in [0, 2^n - 1]$), producing proofs of size $O(\log n)$ (typically under 1 KB).
3. **Transparent Auditability:** Evaluators can inspect the elliptic curve parameters without trusting third-party ceremonies.

### Q2: What is a Pedersen Commitment and why is it used?
**Answer:**
A Pedersen commitment takes a secret numerical value $v$ and a random blinding scalar $r$, mapping them to an elliptic curve point:
$$C = v \cdot G + r \cdot H$$
where $G$ and $H$ are publicly known generator points on the curve whose discrete logarithm relation $\log_G(H)$ is unknown.
- **Perfect Hiding:** For any commitment $C$, every possible value $v$ has an equally likely blinding factor $r$. Thus, an attacker with infinite computing power cannot infer $v$.
- **Computational Binding:** Under the Discrete Logarithm assumption, it is computationally impossible for a dishonest prover to find another pair $(v', r') \neq (v, r)$ that opens to the same curve point $C$.

### Q3: How is a predicate like "GPA $\ge 3.50$" proven without revealing the GPA?
**Answer:**
1. The credential stores Alice's GPA fixed-point integer $v = 380$ committed as $C = 380 \cdot G + r \cdot H$.
2. To prove $v \ge 350$, Alice calculates the shifted commitment:
   $$C' = C - 350 \cdot G = (v - 350) \cdot G + r \cdot H$$
3. Alice then constructs a Bulletproof demonstrating that the value committed in $C'$ lies in the positive integer range $[0, 2^{64}-1]$.
4. If $v < 350$, $v - 350$ would be negative, which in modular arithmetic wraps around to a value exceeding $2^{64}-1$, causing proof generation/verification to fail.
5. The verifier verifies the Bulletproof against $C'$. They learn that $(v - 350) \ge 0$, but learn nothing about the exact value of $v$.

### Q4: Why does ZKDoc use RSASSA-PSS instead of standard RSASSA-PKCS1-v1_5 for digital signatures?
**Answer:**
RSASSA-PKCS1-v1_5 is a deterministic padding scheme with well-documented vulnerabilities to Bleichenbacher-style chosen-ciphertext attacks and padding oracle attacks.  
ZKDoc mandates **RSASSA-PSS (RFC 8017)**:
- It incorporates a randomized salt (32 bytes matching SHA-256).
- It provides a **provably secure reduction** to the RSA problem in the Random Oracle Model.
- Modern compliance frameworks (FIPS 186-4, BSI, NIST SP 800-131A) recommend transitioning away from PKCS#1 v1.5 to RSASSA-PSS.

### Q5: What is the difference between SHA-256 integrity and a digital signature?
**Answer:**
- **SHA-256 Hash:** Provides **integrity** (proves the document was not altered since hashing). However, anyone can compute a SHA-256 hash of any file; it does not prove *who* created or approved the document.
- **Digital Signature (RSASSA-PSS):** Combines the SHA-256 digest with the issuer's private key. It provides **authenticity** and **non-repudiation** (mathematically proving that only the holder of that specific private key could have generated the signature).

---

## 2. Java 21 & Backend Architecture

### Q6: Why was Java 21 LTS chosen for this project?
**Answer:**
Java 21 delivers key language and runtime advantages essential for secure cryptographic platforms:
1. **Records:** Immutable data carriers (e.g., `DocumentRecord`, `RegisteredKey`, `ZkpStatement`) eliminate boilerplate, enforce constructor validation, and guarantee state immutability.
2. **Pattern Matching & Sealed Interfaces:** Used for domain state transitions (e.g., credential states `ACTIVE`, `SUSPENDED`, `REVOKED`) ensuring exhaustive switch handling at compile time.
3. **Enhanced Security & Modern Cryptography APIs:** Native support for modern padding schemes, Edwards-curve math, and robust random number generation (`SecureRandom`).
4. **Virtual Threads (Project Loom):** Enables high-throughput, non-blocking I/O handling during CPU-bound cryptographic operations.

### Q7: How is the multi-module Maven repository structured?
**Answer:**
The project uses strict separation of concerns across 7 modules:
- [`zkdoc-core-crypto`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-core-crypto): Zero external web dependencies; contains pure cryptographic algorithms.
- [`zkdoc-document`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-document): Document intake, storage abstraction, and hashing.
- [`zkdoc-keys`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-keys): Key lifecycle and trust management.
- [`zkdoc-credential`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-credential): Verifiable credentials and capabilities.
- [`zkdoc-zkp`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-zkp): Pedersen commitment bindings and Bulletproof engine.
- [`zkdoc-api`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-api): Spring Boot REST controllers, security configurations, filters.
- [`frontend`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend): React 19 / TypeScript single-page application.

This ensures that cryptographic primitives have zero coupling to Spring or database libraries.

### Q8: How does the platform manage schema evolution and database migrations?
**Answer:**
Database changes are version-controlled using **Flyway** migrations located under [`zkdoc-api/src/main/resources/db/migration/`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-api/src/main/resources/db/migration). Every DDL change (e.g., `V1__init.sql`, `V2__audit_hash_chain.sql`) is executed sequentially at startup and validated against a checksum hash in Flyway's schema history table, preventing schema drift across development, CI/CD, and production environments.

---

## 3. Security, Privacy & Threat Modeling

### Q9: What is "Minimal Disclosure" and how does ZKDoc guarantee it?
**Answer:**
Minimal Disclosure (GDPR Article 5(1)(c) *Data Minimisation*) states that personal data must be adequate, relevant, and limited to what is strictly necessary in relation to the purposes for which they are processed.  
ZKDoc guarantees this through:
1. **Zero Raw File Transfer in Mode B:** Verifiers verify issuer signatures via capability tokens rather than downloading student diplomas.
2. **Zero Plaintext Values in Predicates:** Verifiers receive a boolean validation outcome (`verified: true`) rather than raw numbers.
3. **No Private Key Infiltration:** Private keys are non-exportable and never returned by public REST endpoints.

### Q10: Why are JWT access tokens stored exclusively in browser memory rather than `localStorage` or `sessionStorage`?
**Answer:**
`localStorage` and `sessionStorage` are directly accessible by any JavaScript running in the origin. If a third-party npm package or subtle cross-site scripting (XSS) vulnerability exists, malicious script can exfiltrate stored tokens (`localStorage.getItem('token')`).  
Per **ADR-003**, ZKDoc stores tokens strictly in React memory state ([`authStore`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/state/authStore.ts)). When the page is reloaded, the token is forgotten, completely neutralizing persistence-based token theft attacks.

### Q11: How does the system defend against Denial of Service (DoS) attacks on compute-heavy ZKP verification endpoints?
**Answer:**
Computing and verifying Bulletproofs is CPU-intensive. An attacker could flood `/api/zkp/verify` to exhaust server resources.  
ZKDoc deploys a **Bucket4j Token Bucket rate limiter** ([`BucketRateLimitService`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-api/src/main/java/com/zkdoc/api/ratelimit/BucketRateLimitService.java)):
- Limits verification requests per IP and per authenticated user.
- Enforces bursting limits with sliding replenishment.
- Rejects requests exceeding threshold with HTTP 429 (`Too Many Requests`) and standard `Retry-After` headers before triggering any cryptographic operations.

### Q12: How does the audit logging system prevent database administrator tampering?
**Answer:**
The audit log is implemented as a **cryptographic SHA-256 hash chain**:
$$\text{Hash}_n = \text{SHA-256}(\text{Hash}_{n-1} \parallel \text{Timestamp} \parallel \text{Actor} \parallel \text{Action} \parallel \text{Payload})$$
If a database administrator directly modifies or deletes an existing audit record via SQL, the hash chain breaks from that point forward. The system provides an automated integrity check endpoint (`POST /api/admin/audit-logs/reconcile`) that recomputes the chain from genesis and alerts on any discrepancy.

---

## 4. Verification Workflows & Edge Cases

### Q13: What happens if an attacker modifies one letter in `demo-degree-diploma.pdf`?
**Answer:**
The attacker's file will generate a completely different SHA-256 digest due to the avalanche effect. When uploaded to the verifier in Mode A:
1. The platform computes the file's digest.
2. It looks up the document registry.
3. If no matching digest exists, or if the digest does not match the issuer's registered signature envelope, the verifier displays an immediate **`INTEGRITY VIOLATION`** warning.

### Q14: What is the difference between Verification Mode A and Mode B?
**Answer:**
- **Mode A (Presented Artifact):** Verifier has the full digital document file (e.g., student provides PDF). Verifier uploads file $\rightarrow$ system hashes file $\rightarrow$ verifies hash matches registered record and issuer signature.
- **Mode B (Registry Capability):** Verifier does NOT possess the document file. Holder shares a lightweight Capability Token or QR Code $\rightarrow$ verifier queries registry $\rightarrow$ system checks capability expiration and issuer signature validity without transferring the document.

### Q15: What prevents a revoked or expired credential from being verified?
**Answer:**
Every verification check queries the live trust registry for the credential's lifecycle status. If an issuer or holder has marked the credential as `SUSPENDED` or `REVOKED`, the verification engine aborts with a `CREDENTIAL_NOT_ACTIVE` error, even if the cryptographic signature and mathematical Bulletproof proof are valid.

### Q16: Can a holder prove "GPA $\ge 4.5$" when their real GPA is 3.80?
**Answer:**
No. If the holder attempts to prove a threshold greater than their secret value ($380 - 450 = -70$), the negative value wraps around modulo the elliptic curve order, resulting in an astronomical integer outside $[0, 2^{64}-1]$. The Bulletproof prover algorithm will either fail to generate a valid proof or produce a proof that the verifier's mathematical verification algorithm immediately rejects.
