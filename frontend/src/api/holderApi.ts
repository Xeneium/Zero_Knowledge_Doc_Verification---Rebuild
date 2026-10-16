import { apiRequest, apiBlobRequest } from './client';
import { ENDPOINTS } from './endpoints';
import type {
  HolderCredentialResponse,
  ProvisionHolderCredentialRequest,
  GeneratePredicateProofRequest,
  HolderPredicateProofResponse,
  CreateCapabilityRequest,
  CapabilityResponse,
  CredentialVerificationResult,
  CredentialLifecycleEvent,
} from './types';

/**
 * Authoritative REST client for all Holder endpoints backed by:
 * - HolderCredentialController.java (/api/holder/credentials)
 * - VerificationCapabilityController.java (/api/capabilities)
 * - CredentialController.java (/api/credentials)
 */
export const holderApi = {
  /**
   * Retrieves all credentials provisioned to the authenticated holder.
   * Scoped to the caller's JWT subject.
   */
  async listCredentials(): Promise<HolderCredentialResponse[]> {
    return apiRequest<HolderCredentialResponse[]>(
      ENDPOINTS.HOLDER_CREDENTIALS,
      {
        method: 'GET',
      },
    );
  },

  /**
   * Retrieves a single holder credential record by its UUID.
   * Backend enforces strict IDOR protection (returns 404 if caller is not the owner).
   */
  async getCredentialById(id: string): Promise<HolderCredentialResponse> {
    return apiRequest<HolderCredentialResponse>(
      ENDPOINTS.HOLDER_CREDENTIAL_BY_ID(id),
      {
        method: 'GET',
      },
    );
  },

  /**
   * Provisions an issued credential to the authenticated holder's wallet.
   */
  async provisionCredential(
    payload: ProvisionHolderCredentialRequest,
  ): Promise<HolderCredentialResponse> {
    return apiRequest<HolderCredentialResponse>(
      ENDPOINTS.HOLDER_CREDENTIAL_PROVISION,
      {
        method: 'POST',
        body: payload,
      },
    );
  },

  /**
   * Holder self-suspends their credential.
   */
  async suspendCredential(id: string): Promise<HolderCredentialResponse> {
    return apiRequest<HolderCredentialResponse>(
      ENDPOINTS.HOLDER_CREDENTIAL_SUSPEND(id),
      {
        method: 'POST',
      },
    );
  },

  /**
   * Holder reactivates a suspended credential.
   */
  async activateCredential(id: string): Promise<HolderCredentialResponse> {
    return apiRequest<HolderCredentialResponse>(
      ENDPOINTS.HOLDER_CREDENTIAL_ACTIVATE(id),
      {
        method: 'POST',
      },
    );
  },

  /**
   * Holder permanently revokes their credential.
   */
  async revokeCredential(id: string): Promise<HolderCredentialResponse> {
    return apiRequest<HolderCredentialResponse>(
      ENDPOINTS.HOLDER_CREDENTIAL_REVOKE(id),
      {
        method: 'POST',
      },
    );
  },

  /**
   * Generates a zero-knowledge Bulletproof range/predicate proof.
   * Returns only public statement anchors and proof bytes.
   * Zero raw attributes, blinding factors, or witnesses are leaked.
   */
  async generatePredicateProof(
    payload: GeneratePredicateProofRequest,
  ): Promise<HolderPredicateProofResponse> {
    return apiRequest<HolderPredicateProofResponse>(
      ENDPOINTS.HOLDER_GENERATE_PROOF,
      {
        method: 'POST',
        body: payload,
      },
    );
  },

  /**
   * Mints a time-bounded verification capability token for offline QR presentation.
   */
  async createCapability(
    payload: CreateCapabilityRequest,
  ): Promise<CapabilityResponse> {
    return apiRequest<CapabilityResponse>(ENDPOINTS.CAPABILITIES, {
      method: 'POST',
      body: payload,
    });
  },

  /**
   * Retrieves the raw PNG QR code image for a verification capability.
   */
  async getCapabilityQrBlob(token: string): Promise<Blob> {
    return apiBlobRequest(ENDPOINTS.CAPABILITY_QR(token), {
      method: 'GET',
    });
  },

  /**
   * Checks the underlying cryptographic signature and validity of the credential.
   */
  async verifyCredential(
    credentialId: string,
  ): Promise<CredentialVerificationResult> {
    return apiRequest<CredentialVerificationResult>(
      ENDPOINTS.CREDENTIAL_VERIFY(credentialId),
      {
        method: 'GET',
      },
    );
  },

  /**
   * Retrieves chronological lifecycle event history for a credential.
   */
  async getCredentialLifecycleHistory(
    credentialId: string,
  ): Promise<CredentialLifecycleEvent[]> {
    return apiRequest<CredentialLifecycleEvent[]>(
      ENDPOINTS.CREDENTIAL_LIFECYCLE_HISTORY(credentialId),
      {
        method: 'GET',
      },
    );
  },
};
