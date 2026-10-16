import { apiRequest } from './client';
import type {
  KeyMetadataResponse,
  KeyRegistrationRequest,
  KeyRegisteredResponse,
  KeyStatusChangeRequest,
  KeyRotateRequest,
  RecoveryReconciliationReport,
  AuditQueryFilterParams,
  AuditQueryResult,
  AuditIntegrityResult,
  CredentialLifecycleResponse,
  CredentialLifecycleEvent,
  CredentialVerificationResult,
  CredentialPredicateVerificationRequest,
  CredentialPredicateVerificationResponse,
} from './types';

export const adminApi = {
  // ==========================================
  // Cryptographic Key Trust Registry
  // ==========================================

  async getKeys(): Promise<KeyMetadataResponse[]> {
    return apiRequest<KeyMetadataResponse[]>('/api/admin/keys', {
      method: 'GET',
    });
  },

  async getKey(keyId: string): Promise<KeyMetadataResponse> {
    return apiRequest<KeyMetadataResponse>(
      `/api/admin/keys/${encodeURIComponent(keyId)}`,
      { method: 'GET' },
    );
  },

  async registerKey(
    request: KeyRegistrationRequest,
  ): Promise<KeyRegisteredResponse> {
    return apiRequest<KeyRegisteredResponse>('/api/admin/keys', {
      method: 'POST',
      body: request,
    });
  },

  async changeKeyStatus(
    keyId: string,
    request: KeyStatusChangeRequest,
  ): Promise<void> {
    await apiRequest<void>(
      `/api/admin/keys/${encodeURIComponent(keyId)}/status`,
      {
        method: 'PATCH',
        body: request,
      },
    );
  },

  async rotateKey(keyId: string, request: KeyRotateRequest): Promise<void> {
    await apiRequest<void>(
      `/api/admin/keys/${encodeURIComponent(keyId)}/rotate`,
      {
        method: 'POST',
        body: request,
      },
    );
  },

  // ==========================================
  // Platform Recovery & State Reconciliation
  // ==========================================

  async getRecoveryStatus(): Promise<RecoveryReconciliationReport> {
    return apiRequest<RecoveryReconciliationReport>(
      '/api/admin/recovery/status',
      {
        method: 'GET',
      },
    );
  },

  async reconcileRecovery(): Promise<RecoveryReconciliationReport> {
    return apiRequest<RecoveryReconciliationReport>(
      '/api/admin/recovery/reconcile',
      {
        method: 'POST',
      },
    );
  },

  // ==========================================
  // Security Audit Trail & Integrity
  // ==========================================

  async queryAuditEvents(
    params: AuditQueryFilterParams = {},
  ): Promise<AuditQueryResult> {
    const searchParams = new URLSearchParams();
    if (params.category) searchParams.set('category', params.category);
    if (params.type) searchParams.set('type', params.type);
    if (params.actor) searchParams.set('actor', params.actor);
    if (params.targetType) searchParams.set('targetType', params.targetType);
    if (params.targetId) searchParams.set('targetId', params.targetId);
    if (params.correlationId)
      searchParams.set('correlationId', params.correlationId);
    if (params.from) searchParams.set('from', params.from);
    if (params.to) searchParams.set('to', params.to);
    if (params.page !== undefined)
      searchParams.set('page', params.page.toString());
    if (params.size !== undefined)
      searchParams.set('size', params.size.toString());

    const qs = searchParams.toString();
    const endpoint = `/api/admin/audit/events${qs ? `?${qs}` : ''}`;
    return apiRequest<AuditQueryResult>(endpoint, { method: 'GET' });
  },

  async verifyAuditIntegrity(
    startSeq?: number,
    endSeq?: number,
  ): Promise<AuditIntegrityResult> {
    const searchParams = new URLSearchParams();
    if (startSeq !== undefined)
      searchParams.set('startSeq', startSeq.toString());
    if (endSeq !== undefined) searchParams.set('endSeq', endSeq.toString());

    const qs = searchParams.toString();
    const endpoint = `/api/admin/audit/verify-integrity${qs ? `?${qs}` : ''}`;
    return apiRequest<AuditIntegrityResult>(endpoint, { method: 'GET' });
  },

  // ==========================================
  // Credential Oversight & Lifecycle Administration
  // ==========================================

  async getCredentialLifecycle(
    credentialId: string,
  ): Promise<CredentialLifecycleResponse> {
    return apiRequest<CredentialLifecycleResponse>(
      `/api/credentials/${encodeURIComponent(credentialId)}/lifecycle`,
      { method: 'GET' },
    );
  },

  async getCredentialLifecycleHistory(
    credentialId: string,
  ): Promise<CredentialLifecycleEvent[]> {
    return apiRequest<CredentialLifecycleEvent[]>(
      `/api/credentials/${encodeURIComponent(credentialId)}/lifecycle/history`,
      { method: 'GET' },
    );
  },

  async verifyCredential(
    credentialId: string,
  ): Promise<CredentialVerificationResult> {
    return apiRequest<CredentialVerificationResult>(
      `/api/credentials/${encodeURIComponent(credentialId)}/verify`,
      { method: 'GET' },
    );
  },

  async suspendCredential(
    credentialId: string,
    reason?: string,
  ): Promise<CredentialLifecycleResponse> {
    return apiRequest<CredentialLifecycleResponse>(
      `/api/credentials/${encodeURIComponent(credentialId)}/suspend`,
      {
        method: 'POST',
        body: reason ? { reason } : {},
      },
    );
  },

  async resumeCredential(
    credentialId: string,
    reason?: string,
  ): Promise<CredentialLifecycleResponse> {
    return apiRequest<CredentialLifecycleResponse>(
      `/api/credentials/${encodeURIComponent(credentialId)}/resume`,
      {
        method: 'POST',
        body: reason ? { reason } : {},
      },
    );
  },

  async revokeCredential(
    credentialId: string,
    reason?: string,
  ): Promise<CredentialLifecycleResponse> {
    return apiRequest<CredentialLifecycleResponse>(
      `/api/credentials/${encodeURIComponent(credentialId)}/revoke`,
      {
        method: 'POST',
        body: reason ? { reason } : {},
      },
    );
  },

  // ==========================================
  // Predicate Proof Verification
  // ==========================================

  async verifyCredentialPredicate(
    payload: CredentialPredicateVerificationRequest,
  ): Promise<CredentialPredicateVerificationResponse> {
    return apiRequest<CredentialPredicateVerificationResponse>(
      '/api/v2/zkp/credential-predicates/verify',
      {
        method: 'POST',
        body: payload,
      },
    );
  },

  // ==========================================
  // Health & System Status
  // ==========================================

  async getHealth(): Promise<{ status: string }> {
    return apiRequest<{ status: string }>('/api/health', { method: 'GET' });
  },
};
