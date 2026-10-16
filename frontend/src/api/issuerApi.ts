import { apiRequest, apiBlobRequest } from './client';
import { ENDPOINTS } from './endpoints';
import type {
  DocumentUploadResponse,
  DocumentVerificationResult,
  IssueCredentialRequest,
  IssueCredentialResponse,
  CredentialLifecycleResponse,
  CredentialLifecycleEvent,
  CredentialVerificationResult,
  CreateCapabilityRequest,
  CapabilityResponse,
} from './types';

/**
 * Service providing authoritative Issuer API operations interacting with:
 * - DocumentController (`/api/documents`)
 * - CredentialController (`/api/credentials`)
 * - VerificationCapabilityController (`/api/capabilities`)
 */
export const issuerApi = {
  /**
   * Uploads and secures a document artifact.
   * Spring Boot calculates authoritative SHA-256 digest, signs with RSA-PSS, and securely stores artifact.
   */
  async uploadDocument(file: File): Promise<DocumentUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return apiRequest<DocumentUploadResponse>(ENDPOINTS.DOCUMENTS, {
      method: 'POST',
      body: formData,
    });
  },

  /**
   * Mode A: Verifies document content integrity against the server's persisted copy.
   */
  async verifyStoredDocument(
    documentId: string,
  ): Promise<DocumentVerificationResult> {
    return apiRequest<DocumentVerificationResult>(
      ENDPOINTS.DOCUMENT_VERIFY(documentId),
    );
  },

  /**
   * Mode B: Verifies uploaded byte copy against recorded SHA-256 digest and signature without trusting server copy.
   */
  async verifyUploadedDocument(
    documentId: string,
    file: File,
  ): Promise<DocumentVerificationResult> {
    const formData = new FormData();
    formData.append('file', file);
    return apiRequest<DocumentVerificationResult>(
      ENDPOINTS.DOCUMENT_VERIFY_UPLOAD(documentId),
      {
        method: 'POST',
        body: formData,
      },
    );
  },

  /**
   * Retrieves the raw PNG bytes of the public verification QR code for a document.
   */
  async getDocumentQrBlob(documentId: string): Promise<Blob> {
    return apiBlobRequest(ENDPOINTS.DOCUMENT_QR(documentId));
  },

  /**
   * Issues a signed multi-attribute credential.
   * Enforces object-level authorization (issuerId must match caller identity unless ADMIN).
   */
  async issueCredential(
    request: IssueCredentialRequest,
  ): Promise<IssueCredentialResponse> {
    return apiRequest<IssueCredentialResponse>(ENDPOINTS.CREDENTIAL_ISSUE, {
      method: 'POST',
      body: request,
    });
  },

  /**
   * Fetches the authoritative lifecycle status and audit metadata for a credential.
   */
  async getCredentialLifecycle(
    credentialId: string,
  ): Promise<CredentialLifecycleResponse> {
    return apiRequest<CredentialLifecycleResponse>(
      ENDPOINTS.CREDENTIAL_LIFECYCLE(credentialId),
    );
  },

  /**
   * Fetches the complete immutable transition audit history for a credential.
   */
  async getCredentialLifecycleHistory(
    credentialId: string,
  ): Promise<CredentialLifecycleEvent[]> {
    return apiRequest<CredentialLifecycleEvent[]>(
      ENDPOINTS.CREDENTIAL_LIFECYCLE_HISTORY(credentialId),
    );
  },

  /**
   * Authoritatively verifies a credential's digital signature and validity against the key registry.
   */
  async verifyCredential(
    credentialId: string,
    asOf?: string,
  ): Promise<CredentialVerificationResult> {
    const query = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
    return apiRequest<CredentialVerificationResult>(
      `${ENDPOINTS.CREDENTIAL_VERIFY(credentialId)}${query}`,
    );
  },

  /**
   * Transitions a credential from ACTIVE to SUSPENDED.
   */
  async suspendCredential(
    credentialId: string,
    reason?: string,
  ): Promise<CredentialLifecycleResponse> {
    return apiRequest<CredentialLifecycleResponse>(
      ENDPOINTS.CREDENTIAL_SUSPEND(credentialId),
      {
        method: 'POST',
        body: { reason: reason || null },
      },
    );
  },

  /**
   * Transitions a credential from SUSPENDED back to ACTIVE.
   */
  async resumeCredential(
    credentialId: string,
    reason?: string,
  ): Promise<CredentialLifecycleResponse> {
    return apiRequest<CredentialLifecycleResponse>(
      ENDPOINTS.CREDENTIAL_RESUME(credentialId),
      {
        method: 'POST',
        body: { reason: reason || null },
      },
    );
  },

  /**
   * Permanently revokes a credential (irreversible transition to REVOKED).
   */
  async revokeCredential(
    credentialId: string,
    reason?: string,
  ): Promise<CredentialLifecycleResponse> {
    return apiRequest<CredentialLifecycleResponse>(
      ENDPOINTS.CREDENTIAL_REVOKE(credentialId),
      {
        method: 'POST',
        body: { reason: reason || null },
      },
    );
  },

  /**
   * Mints a verification capability for a credential or document.
   */
  async createCapability(
    request: CreateCapabilityRequest,
  ): Promise<CapabilityResponse> {
    return apiRequest<CapabilityResponse>(ENDPOINTS.CAPABILITIES, {
      method: 'POST',
      body: request,
    });
  },

  /**
   * Retrieves the raw PNG bytes of a capability QR code.
   */
  async getCapabilityQrBlob(capabilityToken: string): Promise<Blob> {
    return apiBlobRequest(ENDPOINTS.CAPABILITY_QR(capabilityToken));
  },
};
