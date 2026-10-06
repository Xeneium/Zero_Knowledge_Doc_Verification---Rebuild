# Zero-Knowledge Document Verification Platform
## Abuse Model & Operational Rate Limiting Architecture

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### 1. Threat Scenarios & Abuse Vectors

The platform exposes both public unauthenticated endpoints (QR code verification) and computationally intensive cryptographic operations (Bulletproof range verification, RSA-PSS signature verification, file digesting). These create distinct abuse vectors:

```
+-------------------------------------------------------------------------------+
|                             Identified Abuse Vectors                          |
+-------------------------------------------------------------------------------+
| 1. Public QR Enumeration Attack    | Brute-forcing 256-bit opaque tokens      |
| 2. ZKP CPU Exhaustion Attack       | Flooding range proof verification jobs   |
| 3. Large File Upload Memory Flooding| Submitting 1GB streams to exhaust heap   |
| 4. Proxy Header Spoofing (XFF)     | Forging X-Forwarded-For to bypass limits |
| 5. Unbounded Memory Leak in Filter | Using unbounded ConcurrentHashMap for IPs|
+-------------------------------------------------------------------------------+
```

---

### 2. Multi-Tier Rate Limiting Architecture

#### 2.1. Network / Edge Layer (Reverse Proxy / Ingress)
* **WAF Enforcement:** Global connection rate limits (100 req/sec per CIDR block).
* **TLS Termination:** Blocks unauthenticated HTTP/1.1 pipelining attacks and malformed HTTP frames.

#### 2.2. Application Layer: Endpoint-Specific Limits

| Endpoint Pattern | Auth Level | Mechanism | Limit | Action on Breach |
| :--- | :--- | :--- | :--- | :--- |
| `GET /api/public/verify/{token}` | Public | Sliding Window / Token Bucket | 30 requests / min per IP | HTTP 429 Too Many Requests |
| `POST /api/v2/zkp/**/verify` | Role: `VERIFIER` | Token Bucket per Subject + IP | 10 requests / min per user | HTTP 429 + Audit Log Event |
| `POST /api/documents` | Role: `ISSUER` | Concurrency Limiter + Token Bucket | 5 concurrent uploads, 20/min | HTTP 429 + Resource Throttling |
| `POST /api/credentials` | Role: `ISSUER` | Token Bucket per Issuer | 60 requests / min per issuer | HTTP 429 |
| `POST /api/admin/**` | Role: `ADMIN` | Strict Sequential Execution | 10 requests / min per admin | HTTP 429 + Security Alert |

---

### 3. Protection Against Memory Exhaustion (Bounded Defense)

* **Prohibited Implementation:** Using an unevicted `ConcurrentHashMap<String, RateTracker>` where arbitrary client IPs are inserted without size limits. An attacker spoofing random IPs could exhaust the JVM heap.
* **Mandated Architecture:**
  1. **Local Bounded Cache:** If running single-node, use a Guava / Caffeine cache bounded at a maximum of 10,000 entries with `expireAfterWrite = 1 minute`. When the cache reaches capacity, least-recently-used (LRU) eviction prevents Out-Of-Memory (OOM) errors.
  2. **Distributed Redis:** In production multi-node environments, state is stored in Redis using Bucket4j / Redis token bucket algorithms with automatic key expiration (TTL).

---

### 4. Trusted Proxy Behavior & IP Extraction

* **Security Vulnerability:** Blindly trusting the `X-Forwarded-For` header allows an attacker to bypass IP rate limits by sending a random spoofed header (e.g. `X-Forwarded-For: 1.2.3.4`).
* **Enforced Policy:**
  * Only parse `X-Forwarded-For` if the immediate upstream connection originates from a known, configured **Trusted Proxy Subnet** (e.g., Kubernetes internal ingress IP, AWS ALB CIDR).
  * If the connection is directly from the client or from an untrusted peer, the direct socket remote address (`request.getRemoteAddr()`) is used exclusively.
  * The client IP is hashed with a rotating secret salt before being recorded in audit tables (`client_ip_hash = SHA256(ip + dailySalt)`).
