# Zero-Knowledge Document Verification Platform
## Secure Development & Engineering Baseline

**Document Version:** 1.0.0  
**Phase:** Phase 1 — Project Foundation & Engineering Infrastructure  

---

### 1. Security Architecture Principles

1. **Defense-in-Depth:** Security is applied across all layers—transport headers, HTTP filtering, service authorization, and cryptographic integrity.
2. **Fail-Closed Semantics:** Any missing, invalid, or corrupted parameter results in immediate rejection (`ApiErrorResponse`).
3. **No Sensitive Information Disclosure:** Internal stack traces, raw SQL queries, and internal class hierarchies are never exposed to clients.
4. **Structured Logging Sanitization:**
   * Correlation IDs are tracked via SLF4J MDC (`MdcCorrelationIdFilter`).
   * Passwords, private keys, raw attribute values, blinding factors, and witness data must **never** be logged.

---

### 2. HTTP Security Baseline

Configured via `SecurityConfiguration.java`:

* **Content-Security-Policy (CSP):** `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'`
* **Frame Protection:** `X-Frame-Options: DENY`
* **MIME Sniffing Prevention:** `X-Content-Type-Options: nosniff`
* **Referrer Policy:** `strict-origin-when-cross-origin`
* **Session Management:** `STATELESS` (JWT-based).

---

### 3. Resource & DoS Protection Limits

Configured in `application.yml`:

* **Max Multipart File Size:** 25MB (`spring.servlet.multipart.max-file-size: 25MB`).
* **Max Request Size:** 25MB (`spring.servlet.multipart.max-request-size: 25MB`).
* **Tomcat Max Post Size:** 25MB (`server.tomcat.max-http-form-post-size: 26214400`).
* **Rate Limiting:** Bucket4j token bucket rate limiting on public verification endpoints.
