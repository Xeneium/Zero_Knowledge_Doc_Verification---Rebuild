/**
 * Authoritative REST endpoint paths matching Spring Boot controllers.
 */
export const ENDPOINTS = {
  // Document Operations (DocumentController)
  DOCUMENTS: '/api/documents',
  DOCUMENT_VERIFY: (id: string) =>
    `/api/documents/${encodeURIComponent(id)}/verify`,
  DOCUMENT_VERIFY_UPLOAD: (id: string) =>
    `/api/documents/${encodeURIComponent(id)}/verify-upload`,
  DOCUMENT_DIRECT_VERIFY_UPLOAD: '/api/documents/verify-upload',
  DOCUMENT_QR: (id: string) => `/api/documents/${encodeURIComponent(id)}/qr`,

  // Credential Operations (CredentialController)
  CREDENTIALS: '/api/credentials',
  CREDENTIAL_ISSUE: '/api/credentials/issue',
  CREDENTIAL_VERIFY: (id: string) =>
    `/api/credentials/${encodeURIComponent(id)}/verify`,
  CREDENTIAL_LIFECYCLE: (id: string) =>
    `/api/credentials/${encodeURIComponent(id)}/lifecycle`,
  CREDENTIAL_LIFECYCLE_HISTORY: (id: string) =>
    `/api/credentials/${encodeURIComponent(id)}/lifecycle/history`,
  CREDENTIAL_SUSPEND: (id: string) =>
    `/api/credentials/${encodeURIComponent(id)}/suspend`,
  CREDENTIAL_RESUME: (id: string) =>
    `/api/credentials/${encodeURIComponent(id)}/resume`,
  CREDENTIAL_REVOKE: (id: string) =>
    `/api/credentials/${encodeURIComponent(id)}/revoke`,

  // Verification Capabilities & QR Codes (VerificationCapabilityController)
  CAPABILITIES: '/api/capabilities',
  CAPABILITY_QR: (token: string) =>
    `/api/capabilities/${encodeURIComponent(token)}/qr`,
  CAPABILITY_REVOKE: (id: string) =>
    `/api/capabilities/${encodeURIComponent(id)}/revoke`,

  // Public Verification (PublicVerificationController)
  PUBLIC_VERIFY_TOKEN: (token: string) =>
    `/api/public/verify/${encodeURIComponent(token)}`,
  PUBLIC_VERIFY_CAPABILITY: (token: string) =>
    `/api/public/verify/capability/${encodeURIComponent(token)}`,
  PUBLIC_VERIFY_UPLOAD: '/api/public/verify/upload',

  // Zero-Knowledge Predicates (CredentialPredicateController)
  VERIFY_PREDICATE: '/api/v2/zkp/credential-predicates/verify',

  // Holder Operations (HolderCredentialController)
  HOLDER_CREDENTIALS: '/api/holder/credentials',
  HOLDER_CREDENTIAL_BY_ID: (id: string) =>
    `/api/holder/credentials/${encodeURIComponent(id)}`,
  HOLDER_CREDENTIAL_PROVISION: '/api/holder/credentials/provision',
  HOLDER_CREDENTIAL_SUSPEND: (id: string) =>
    `/api/holder/credentials/${encodeURIComponent(id)}/suspend`,
  HOLDER_CREDENTIAL_ACTIVATE: (id: string) =>
    `/api/holder/credentials/${encodeURIComponent(id)}/activate`,
  HOLDER_CREDENTIAL_REVOKE: (id: string) =>
    `/api/holder/credentials/${encodeURIComponent(id)}/revoke`,
  HOLDER_GENERATE_PROOF: '/api/holder/credentials/proofs/generate',

  // Administration & Keys (KeyRegistryAdminController & RecoveryAdminController)
  ADMIN_KEYS: '/api/admin/keys',
  ADMIN_KEY_METADATA: (keyId: string) =>
    `/api/admin/keys/${encodeURIComponent(keyId)}`,
  ADMIN_RECOVERY_STATUS: '/api/admin/recovery/status',

  // Audit Logs (AuditAdminController)
  AUDIT_EVENTS: '/api/admin/audit/events',
  AUDIT_VERIFY_INTEGRITY: '/api/admin/audit/verify-integrity',

  // Health Probe
  HEALTH: '/actuator/health',
} as const;
