import { apiRequest } from './client';
import { ENDPOINTS } from './endpoints';
import { authStore } from '../state/authStore';
import type {
  PublicVerificationResult,
  CapabilityPublicVerificationResponse,
  DocumentVerificationResult,
  DocumentVerificationOutcome,
  CredentialVerificationResult,
  CredentialLifecycleResponse,
  CredentialPredicateVerificationRequest,
  CredentialPredicateVerificationResponse,
} from './types';

/**
 * Authoritative Verifier API client wrapping backend verification endpoints.
 * All operations interact directly with verified Spring Boot controllers with zero mocks.
 */
export const verifierApi = {
  /**
   * Public verification of an opaque token or verification capability.
   * Minimal disclosure: returns only signatureValid and documentActive.
   * Endpoint: GET /api/public/verify/{token}
   */
  publicVerifyToken(token: string): Promise<PublicVerificationResult> {
    return apiRequest<PublicVerificationResult>(
      ENDPOINTS.PUBLIC_VERIFY_TOKEN(token),
    );
  },

  /**
   * Public verification of capability details (mode, protocol, validity, failureReason).
   * Minimal disclosure: does not reveal underlying document bytes or private attributes.
   * Endpoint: GET /api/public/verify/capability/{token}
   */
  publicVerifyCapability(
    token: string,
  ): Promise<CapabilityPublicVerificationResponse> {
    return apiRequest<CapabilityPublicVerificationResponse>(
      ENDPOINTS.PUBLIC_VERIFY_CAPABILITY(token),
    );
  },

  /**
   * Mode B: Verifies an uploaded file against a stored document copy (SHA-256 + RSA-PSS signature).
   * Requires authenticated role (ROLE_VERIFIER, ROLE_ISSUER, ROLE_HOLDER, or ROLE_ADMIN).
   * Endpoint: POST /api/documents/{id}/verify-upload
   */
  verifyDocumentUploadedCopy(
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
   * Mode B: Direct document upload verification without requiring an embedded QR code.
   * If candidateId is provided, compares candidate file digest against that registration.
   * If candidateId is omitted, looks up registration matching candidate file digest.
   * Dynamically routes to /api/documents/verify-upload if authenticated, or /api/public/verify/upload if unauthenticated.
   */
  async verifyUploadedDocumentDirect(
    file: File,
    candidateId?: string,
  ): Promise<DocumentVerificationOutcome> {
    const formData = new FormData();
    formData.append('file', file);
    if (candidateId && candidateId.trim()) {
      formData.append('documentId', candidateId.trim());
    }

    const isAuthenticated = authStore.getState().isAuthenticated;
    const endpoint = isAuthenticated
      ? ENDPOINTS.DOCUMENT_DIRECT_VERIFY_UPLOAD
      : ENDPOINTS.PUBLIC_VERIFY_UPLOAD;

    return apiRequest<DocumentVerificationOutcome>(endpoint, {
      method: 'POST',
      body: formData,
    });
  },

  /**
   * Mode A: Verifies the integrity of a stored document copy against its cryptographic signature.
   * Requires authenticated role.
   * Endpoint: GET /api/documents/{id}/verify
   */
  verifyDocumentStoredCopy(
    documentId: string,
  ): Promise<DocumentVerificationResult> {
    return apiRequest<DocumentVerificationResult>(
      ENDPOINTS.DOCUMENT_VERIFY(documentId),
    );
  },

  /**
   * Authoritatively verifies an issued credential's signature, digest, and operational status.
   * Requires authenticated role.
   * Endpoint: GET /api/credentials/{id}/verify
   */
  verifyCredential(
    credentialId: string,
    asOf?: string,
  ): Promise<CredentialVerificationResult> {
    const endpoint = asOf
      ? `${ENDPOINTS.CREDENTIAL_VERIFY(credentialId)}?asOf=${encodeURIComponent(asOf)}`
      : ENDPOINTS.CREDENTIAL_VERIFY(credentialId);
    return apiRequest<CredentialVerificationResult>(endpoint);
  },

  /**
   * Retrieves current credential lifecycle status and transition metadata.
   * Requires authenticated role.
   * Endpoint: GET /api/credentials/{id}/lifecycle
   */
  getCredentialLifecycle(
    credentialId: string,
  ): Promise<CredentialLifecycleResponse> {
    return apiRequest<CredentialLifecycleResponse>(
      ENDPOINTS.CREDENTIAL_LIFECYCLE(credentialId),
    );
  },

  /**
   * Zero-Knowledge Predicate Verification.
   * Evaluates Bulletproof range proof against committed attribute without disclosing private values.
   * Requires ROLE_VERIFIER or ROLE_ADMIN.
   * Endpoint: POST /api/v2/zkp/credential-predicates/verify
   */
  verifyPredicateProof(
    payload: CredentialPredicateVerificationRequest,
  ): Promise<CredentialPredicateVerificationResponse> {
    return apiRequest<CredentialPredicateVerificationResponse>(
      ENDPOINTS.VERIFY_PREDICATE,
      {
        method: 'POST',
        body: payload,
      },
    );
  },
};
