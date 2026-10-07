# Credential Issuance Threat Model & Mitigations

**Document Version:** 1.0.0  
**Phase:** Phase 4 — Credential Issuance & RSA-PSS Trust Chain  
**Status:** Approved Security Specification  

---

## 1. Threat Matrix

| Threat ID | Threat Description | Attack Vector | Mitigation / Architectural Control |
|:---|:---|:---|:---|
| **TH-CRED-01** | **Payload Tampering** | Adversary alters attributes in the database to change age, citizenship, or status | SHA-256 digest recomputation over canonical payload; signature verification fails immediately. |
| **TH-CRED-02** | **Signature Forgery** | Attacker crafts a fake signature on forged attributes | RSASSA-PSS with 2048+ bit keys; mathematically infeasible without private signing key. |
| **TH-CRED-03** | **Key Purpose Confusion** | Attacker uses a JWT signing key to sign a verifiable credential | `KeyRegistryService` strictly checks `key.purpose() == CREDENTIAL_SIGNING` before signing and verifying. |
| **TH-CRED-04** | **Cross-Issuer Impersonation** | Issuer A attempts to sign a credential claiming to be Issuer B | `KeyRegistryService.sign` enforces `expectedOwnerId == key.ownerId()`. Keys cannot be used by other owners. |
| **TH-CRED-05** | **Signing with Revoked Key** | Compromised key used after revocation timestamp | `KeyStatus.REVOKED` keys cannot sign and are unconditionally rejected during verification. |
| **TH-CRED-06** | **Suspended Authority Issuance** | Sanctioned or suspended issuer attempts to issue credentials | `CredentialService` queries `IssuerStore` and aborts issuance if status is not `ACTIVE`. |
| **TH-CRED-07** | **Replay of Expired Credentials** | Presenting an authentic but expired credential | `CredentialService.verify` checks `now <= expiresAt` and returns `operationalStatusValid=false`. |
| **TH-CRED-08** | **Unauthenticated Issuance** | Anonymous attacker calls `/api/credentials` | Spring Security requires `hasRole('ISSUER')` with valid Bearer JWT. |
