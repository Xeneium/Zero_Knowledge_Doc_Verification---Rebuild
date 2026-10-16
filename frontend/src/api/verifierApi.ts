import { apiRequest } from './client';
import { ENDPOINTS } from './endpoints';
import type {
  PublicVerificationResult,
  CapabilityPublicVerificationResponse,
  DocumentVerificationResult,
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
