/**
 * Backend DTOs strictly synchronized with Spring Boot Java domain contracts.
 */

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId?: string;
  validationErrors?: Record<string, string>;
}

export interface HealthStatusResponse {
  status: 'UP' | 'DOWN' | 'UNKNOWN' | (string & {});
  components?: Record<
    string,
    { status: string; details?: Record<string, unknown> }
  >;
}

export interface KeyStatusResponse {
  activeKeyId: string;
  algorithm: string;
  purpose: string;
  publicKeyFingerprint: string;
  expiresAt?: string;
}

// ==========================================
// Document API Domain Types
// ==========================================

export interface DocumentUploadResponse {
  id: string; // UUID
  digest: string; // SHA-256 Hex string
}

export interface DocumentVerificationResult {
  id?: string; // UUID
  documentId?: string; // UUID alias matching Java VerificationResult
  verified: boolean;
}

// ==========================================
// Credential API Domain Types
// ==========================================

export type CredentialStatus = 'ACTIVE' | 'SUSPENDED' | 'REVOKED' | 'EXPIRED';

export interface AttributeRequestDto {
  name: string;
  type?: string; // STRING, INTEGER, DATE, BOOLEAN
  value: string;
}

export interface IssueCredentialRequest {
  issuerId: string;
  credentialId?: string;
  schemaId?: string;
  issuerKeyId?: string;
  issuedAt?: string;
  expiresAt?: string;
  status?: CredentialStatus;
  attributes: AttributeRequestDto[];
}

export interface IssueCredentialResponse {
  credentialId: string;
  digest: string;
}

export interface CredentialVerificationResult {
  credentialId: string;
  verified: boolean;
  message?: string;
  asOf?: string;
  status?: CredentialStatus;
  revokedAt?: string;
  suspendedAt?: string;
}

export interface CredentialLifecycleResponse {
  credentialId: string;
  issuerId: string;
  status: CredentialStatus;
  issuedAt?: string;
  expiresAt?: string;
  suspendedAt?: string;
  suspensionReason?: string;
  revokedAt?: string;
  revocationReason?: string;
  version: number;
  message?: string;
}

export interface CredentialLifecycleEvent {
  id: string;
  credentialId: string;
  eventType: string;
  actor: string;
  actorRole: string;
  fromStatus: CredentialStatus;
  toStatus: CredentialStatus;
  reason?: string;
  timestamp: string;
  metadataJson?: string;
}

export interface SuspendCredentialRequest {
  reason?: string;
}

export interface ResumeCredentialRequest {
  reason?: string;
}

export interface RevokeCredentialRequest {
  reason?: string;
}

// ==========================================
// Verification Capabilities & QR Codes
// ==========================================

export type VerificationMode =
  'MODE_A_PRESENTED_ARTIFACT' | 'MODE_B_REGISTRY_CAPABILITY';

export interface CreateCapabilityRequest {
  credentialId?: string;
  documentId?: string;
  mode: VerificationMode;
  ttlSeconds?: number;
}

export interface CapabilityResponse {
  id: string;
  capabilityToken: string;
  mode: VerificationMode;
  protocolVersion: string;
  status: string;
  expiresAt: string;
}

// ==========================================
// Public Verification & ZKP Evaluation
// ==========================================

export interface PublicVerificationResult {
  signatureValid: boolean;
  documentActive: boolean;
}

export interface CapabilityPublicVerificationResponse {
  verified: boolean;
  valid: boolean;
  status: string;
  mode: string;
  protocolVersion: string;
  signatureValid: boolean;
  active: boolean;
  failureReason: string | null;
}

export interface VerificationRequest {
  credentialId?: string;
  verificationReference?: string;
}

export interface VerificationResponse {
  valid: boolean;
  status: CredentialStatus | (string & {});
  issuerId?: string;
  credentialId?: string;
  issuedAt?: string;
  revokedAt?: string;
}

export interface CredentialPredicateVerificationRequest {
  proofVersion: string;
  credentialId: string;
  attributeName: string;
  threshold: number;
  nonce: string; // Base64 encoded
  proofBytes: string; // Base64 encoded
}

export interface CredentialPredicateVerificationResponse {
  verified: boolean;
}

// ==========================================
// Holder Operations (Phase F7)
// ==========================================

export interface ProvisionHolderCredentialRequest {
  credentialId: string;
}

export interface HolderCredentialResponse {
  id: string;
  holderId: string;
  credentialId: string;
  credentialDigestHex: string;
  credentialDigest?: string; // fallback alias
  status: string;
  version: number;
  provisionedAt: string;
  updatedAt: string;
}

export interface GeneratePredicateProofRequest {
  holderCredentialId: string;
  attributeName: string;
  threshold: number;
  nonceHex: string;
  operator?: string;
}

export interface HolderPredicateProofResponse {
  holderCredentialId: string;
  credentialId: string;
  credentialDigestHex: string;
  credentialDigest?: string;
  attributeName: string;
  operator: string;
  threshold: number;
  commitmentPointHex: string;
  commitmentPoint?: string;
  attestationSignatureHex: string;
  attestationSignature?: string;
  attestationKeyId: string;
  nonceHex: string;
  transcriptNonce?: string;
  proofVersion: string | number;
  bitSize: number;
  proofBytesHex: string;
  proofBytes?: string;
}

// ==========================================
// Audit Logs & Security Trail (Phase F9)
// ==========================================

export type AuditEventCategory =
  | 'AUTHENTICATION'
  | 'AUTHORIZATION'
  | 'CREDENTIAL'
  | 'DOCUMENT'
  | 'QR'
  | 'ZKP'
  | 'ABUSE'
  | 'ADMIN'
  | 'KEY';

export interface SecurityAuditEventResponse {
  eventId: string;
  sequenceNumber: number;
  eventType: string;
  eventCategory: AuditEventCategory | (string & {});
  timestamp: string;
  actorSubject: string;
  actorType: string;
  targetType: string | null;
  targetId: string | null;
  action: string;
  outcome: 'SUCCESS' | 'FAILURE' | 'DENIED' | (string & {});
  reasonCode: string | null;
  correlationId: string | null;
  clientIpHash: string | null;
  metadataJson: string | null;
  previousEventHash: string;
  eventHash: string;
}

export interface AuditQueryResult {
  items: SecurityAuditEventResponse[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface AuditIntegrityResult {
  valid: boolean;
  checkedCount: number;
  tamperedSequence: number | null;
  failureReason: string | null;
}

export interface AuditQueryFilterParams {
  category?: AuditEventCategory;
  type?: string;
  actor?: string;
  targetType?: string;
  targetId?: string;
  correlationId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

// Backwards-compatibility aliases
export type AuditLogEntry = SecurityAuditEventResponse;
export interface PagedAuditLogsResponse {
  content: SecurityAuditEventResponse[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

// ==========================================
// Key Registry Administration (Phase F9)
// ==========================================

export type KeyPurpose =
  | 'JWT_SIGNING'
  | 'CREDENTIAL_SIGNING'
  | 'DOCUMENT_SIGNING'
  | 'COMMITMENT_ATTESTATION_SIGNING';

export type KeyStatus = 'ACTIVE' | 'RETIRING' | 'RETIRED' | 'REVOKED';

export type KeyOwnerType =
  | 'PLATFORM'
  | 'ISSUER'
  | 'SERVICE'
  | 'SECURITY_DOMAIN';

export interface KeyRegistrationRequest {
  keyId: string;
  purpose: KeyPurpose;
  status?: KeyStatus;
  algorithm?: string;
  ownerId?: string;
  ownerType?: KeyOwnerType;
  publicKeyPem: string;
  privateKeyFileEnvironment?: string;
}

export interface KeyStatusChangeRequest {
  status: KeyStatus;
  reason?: string;
  replacementKeyId?: string;
}

export interface KeyRotateRequest {
  replacementKeyId?: string;
}

export interface KeyRegisteredResponse {
  id: string;
  keyId: string;
}

export interface KeyMetadataResponse {
  id: string;
  keyId: string;
  purpose: KeyPurpose;
  status: KeyStatus;
  ownerId: string;
  ownerType: KeyOwnerType;
  algorithm: string;
  fingerprint: string;
  publicKeyPem: string;
  createdAt: string;
  activatedAt: string | null;
  retiredAt: string | null;
  revokedAt: string | null;
  replacementKeyId: string | null;
}

// ==========================================
// Platform Recovery & Reconciliation (Phase F9)
// ==========================================

export type RecoveryStatus = 'HEALTHY' | 'WARNING' | 'CORRUPTED';

export interface RecoveryReconciliationReport {
  timestamp: string;
  status: RecoveryStatus;
  schemaValid: boolean;
  schemaVersion: string;
  totalArtifactsChecked: number;
  validArtifacts: number;
  missingArtifacts: string[];
  corruptedArtifacts: string[];
  totalKeysChecked: number;
  activeKeys: number;
  revokedKeys: number;
  invalidKeys: string[];
  totalCredentialsChecked: number;
  validCredentials: number;
  invalidCredentials: string[];
  auditChainValid: boolean;
  totalAuditEvents: number;
  auditFailureReason: string | null;
  summary: string;
}

