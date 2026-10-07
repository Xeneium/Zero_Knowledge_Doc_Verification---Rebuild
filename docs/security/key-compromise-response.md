# Key Compromise & Emergency Response Runbook

## 1. Overview

In the event that an issuer or platform private signing key is suspected of being leaked, stolen, exfiltrated, or cryptographically weakened, this operational procedure must be executed immediately.

---

## 2. Emergency Response Workflow

```
[ Compromise Detected / Reported ]
                 |
                 v
+----------------------------------+
| 1. Declare Security Incident     |
|    - Identify compromised keyId  |
+----------------------------------+
                 |
                 v
+----------------------------------+
| 2. Emergency Revocation (API/CLI)|
|    POST /admin/keys/{id}/revoke  |
|    - Set status = REVOKED        |
|    - Set revoked_at = now()      |
|    - Record justification        |
+----------------------------------+
                 |
                 v
+----------------------------------+
| 3. Invalidate Custody / KMS      |
|    - Disable KMS Key / HSM Alias |
|    - Purge provider cache        |
+----------------------------------+
                 |
                 v
+----------------------------------+
| 4. Issue Replacement Key         |
|    - Generate fresh 3072-bit RSA |
|    - Register new ACTIVE key     |
|    - Rebind issuer signing key   |
+----------------------------------+
                 |
                 v
+----------------------------------+
| 5. Impact & Forensics Audit      |
|    - Query key_lifecycle_audit   |
|    - Query verification_audit    |
|    - Re-evaluate credential validity|
+----------------------------------+
```

---

## 3. Step-by-Step Execution

### Step 1: Emergency Revocation via Admin API
Execute an authorized administrative request to immediately revoke the key:

```bash
curl -X POST https://api.zkdoc.internal/api/v1/admin/keys/compromised-key-id-123/revoke \
  -H "Authorization: Bearer <SECURE_ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Private key exfiltration suspected during security audit incident #INC-9481"}'
```

The key status transitions atomically to `REVOKED`. All verification queries resolving this key will immediately fail closed.

### Step 2: Purge Private-Key Custody
- For AWS KMS: `aws kms disable-key --key-id <kms-id>`
- For HashiCorp Vault: `vault kv delete secret/zkdoc/keys/compromised-key-id-123`
- For Development Keystore: Purge key from provider in-memory cache.

### Step 3: Register Replacement Key
1. Generate a new RSASSA-PSS key pair (minimum 2048-bit, 3072-bit recommended).
2. Register the new public key with `status = ACTIVE` and the same purpose and owner.
3. Update the issuer record (`issuers` table) to point to the new `keyId`.

### Step 4: Audit & Impact Analysis
1. Inspect `key_lifecycle_audit_events` to verify the actor, timestamp, and result of the revocation.
2. Query `credentials` where `signing_key_id = 'compromised-key-id-123'` to identify issued credentials that may need re-issuance or suspension.
3. Query `verification_audit_events` to analyze whether forged credentials were submitted prior to or following the compromise.
