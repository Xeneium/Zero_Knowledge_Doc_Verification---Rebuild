# Trust Registry Architecture & Verification Policy

## 1. Core Principle: Key ID Alone Is Never Trust

In modern security architectures, a `key_id` (`kid`) header is an identifier, **not** an authorization. An untrusted party could easily generate an RSA key pair, assign an arbitrary identifier (or copy a victim's key ID), and sign forged payloads.

The platform's `KeyTrustResolver` enforces **Contextual Public-Key Trust**:

$$\text{Trust}(\text{keyId}) \iff \begin{cases}
\text{Key exists in Registry} \\
\land\ \text{Key purpose matches expected operation purpose} \\
\land\ \text{Key owner matches expected entity/issuer domain} \\
\land\ \text{Algorithm conforms to RSASSA-PSS (SHA-256, 2048+ bits)} \\
\land\ \text{Key lifecycle allows verification (ACTIVE or RETIRED)} \\
\land\ \text{Key is not REVOKED}
\end{cases}$$

---

## 2. Trust Resolution Workflow

```
[ Incoming Signed Evidence ]
       | (keyId, ownerId, claimedSignature)
       v
+-------------------------------+
|       KeyTrustResolver        |
+-------------------------------+
       |
       +--> 1. Query key_registry by keyId
       |       - Fail closed if not found
       |
       +--> 2. Purpose Match: key.purpose == expectedPurpose?
       |       - Fail closed on purpose mismatch
       |
       +--> 3. Owner Match: key.ownerId == expectedOwnerId?
       |       - Fail closed on owner mismatch
       |
       +--> 4. Status Check:
       |       - ACTIVE: Permitted
       |       - RETIRED: Permitted for historical verification
       |       - REVOKED: Fail closed immediately
       |
       +--> 5. Algorithm Compliance:
       |       - Algorithm == "RSASSA-PSS"
       |       - Key Size >= 2048 bits
       v
[ Resolved Trusted PublicKey ]
       |
       v
[ RSASSA-PSS Signature Verification Engine ]
```

---

## 3. Historical Verification Policy

A major challenge in document and credential verification platforms is verifying evidence signed by keys that are no longer active:

1. **Scheduled Retirement:**
   - Keys reach end-of-life according to organizational key rotation schedules (e.g. 1 year).
   - The key is rotated to a replacement key (`rotateKey(oldKeyId, newKeyId)`).
   - Old key transitions to `RETIRED`, setting `retired_at = now()` and `replacement_key_id = newKeyId`.
   - Credentials or documents signed while the old key was active remain **cryptographically valid and verifiable indefinitely**.
   - Attempting to sign *new* credentials with the retired key is strictly rejected.

2. **Compromise Revocation:**
   - A key's private key is leaked, lost, or compromised.
   - The key transitions immediately to `REVOKED`, setting `revoked_at = now()` and capturing `revocation_reason`.
   - All subsequent attempts to verify evidence signed by this key fail closed, preventing forged evidence from passing.

---

## 4. Administrative Endpoints & Safeguards

The platform exposes administrative key management capabilities via `KeyRegistryAdminController`:

- `GET /api/v1/admin/keys/{keyId}`: Retrieve public key trust metadata (key ID, purpose, owner, status, fingerprint, timestamps, replacement key ID). **Private keys are never exposed.**
- `POST /api/v1/admin/keys`: Register a new trusted public key.
- `POST /api/v1/admin/keys/{keyId}/rotate`: Rotate an active key to a replacement key ID, automatically retiring the old key and establishing replacement traceability.
- `POST /api/v1/admin/keys/{keyId}/revoke`: Terminally revoke a compromised key with mandatory justification.
