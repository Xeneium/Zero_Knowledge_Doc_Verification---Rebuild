import { describe, it, expect, beforeEach } from 'vitest';
import { adminStore } from '../src/state/adminStore';
import type {
  KeyMetadataResponse,
  RecoveryReconciliationReport,
  AuditQueryResult,
  AuditIntegrityResult,
  SecurityAuditEventResponse,
  CredentialLifecycleResponse,
  CredentialLifecycleEvent,
} from '../src/api/types';

describe('adminStore State Management', () => {
  beforeEach(() => {
    adminStore.reset();
  });

  it('initializes with default empty state', () => {
    const state = adminStore.getState();
    expect(state.keys).toEqual([]);
    expect(state.isLoadingKeys).toBe(false);
    expect(state.selectedKey).toBeNull();
    expect(state.recoveryReport).toBeNull();
    expect(state.isReconciling).toBe(false);
    expect(state.auditQueryResult).toBeNull();
    expect(state.auditIntegrity).toBeNull();
    expect(state.credentialOversight.lifecycle).toBeNull();
    expect(state.credentialOversight.history).toEqual([]);
  });

  describe('Key Registry Store Management', () => {
    const mockKey: KeyMetadataResponse = {
      id: 'key-uuid-1',
      keyId: 'issuer-key-01',
      publicKeyPem: '-----BEGIN PUBLIC KEY-----\nMIIB...',
      algorithm: 'RSA-PSS',
      status: 'ACTIVE',
      purpose: 'CREDENTIAL_SIGNING',
      ownerType: 'ISSUER',
      ownerId: 'issuer-alpha',
      fingerprint: 'abc123sha',
      replacementKeyId: null,
      createdAt: '2026-01-01T00:00:00Z',
      activatedAt: '2026-01-01T00:00:00Z',
      retiredAt: null,
      revokedAt: null,
    };

    it('sets keys and updates key list', () => {
      adminStore.setLoadingKeys(true);
      expect(adminStore.getState().isLoadingKeys).toBe(true);

      adminStore.setKeys([mockKey]);
      expect(adminStore.getState().keys).toHaveLength(1);
      expect(adminStore.getState().keys[0].keyId).toBe('issuer-key-01');

      adminStore.setLoadingKeys(false);
      expect(adminStore.getState().isLoadingKeys).toBe(false);
    });

    it('sets and clears selected key', () => {
      adminStore.setSelectedKey(mockKey);
      expect(adminStore.getState().selectedKey?.keyId).toBe('issuer-key-01');

      adminStore.setSelectedKey(null);
      expect(adminStore.getState().selectedKey).toBeNull();
    });

    it('handles key registry error message', () => {
      adminStore.setKeysError('Failed to fetch key registry');
      expect(adminStore.getState().keysError).toBe('Failed to fetch key registry');
    });
  });

  describe('System Recovery & Reconciliation Management', () => {
    const mockReport: RecoveryReconciliationReport = {
      timestamp: '2026-10-15T09:05:00Z',
      status: 'HEALTHY',
      schemaValid: true,
      schemaVersion: 'V2',
      totalArtifactsChecked: 10,
      validArtifacts: 10,
      missingArtifacts: [],
      corruptedArtifacts: [],
      totalKeysChecked: 4,
      activeKeys: 4,
      revokedKeys: 0,
      invalidKeys: [],
      totalCredentialsChecked: 15,
      validCredentials: 15,
      invalidCredentials: [],
      auditChainValid: true,
      totalAuditEvents: 100,
      auditFailureReason: null,
      summary: 'All cryptographic anchors and records verified',
    };

    it('sets recovery report and reconciliation state', () => {
      adminStore.setLoadingRecovery(true);
      expect(adminStore.getState().isLoadingRecovery).toBe(true);

      adminStore.setRecoveryReport(mockReport);
      expect(adminStore.getState().recoveryReport?.status).toBe('HEALTHY');
      expect(adminStore.getState().recoveryReport?.totalKeysChecked).toBe(4);

      adminStore.setLoadingRecovery(false);
      expect(adminStore.getState().isLoadingRecovery).toBe(false);

      adminStore.setIsReconciling(true);
      expect(adminStore.getState().isReconciling).toBe(true);

      adminStore.setIsReconciling(false);
      expect(adminStore.getState().isReconciling).toBe(false);
    });

    it('handles recovery error message', () => {
      adminStore.setRecoveryError('Recovery service unavailable');
      expect(adminStore.getState().recoveryError).toBe('Recovery service unavailable');
    });
  });

  describe('Security Audit Store Management', () => {
    const mockEvent: SecurityAuditEventResponse = {
      eventId: 'evt-1',
      sequenceNumber: 1,
      eventType: 'AUTH_SUCCESS',
      eventCategory: 'AUTHENTICATION',
      timestamp: '2026-10-15T08:00:00Z',
      actorSubject: 'urn:zkdoc:holder:alice',
      actorType: 'HOLDER',
      targetType: 'SESSION',
      targetId: 'sess-123',
      action: 'LOGIN',
      outcome: 'SUCCESS',
      reasonCode: null,
      correlationId: null,
      clientIpHash: 'iphash123',
      metadataJson: '{"detail":"ok"}',
      previousEventHash: 'genesis',
      eventHash: 'hash-001',
    };

    const mockQueryResult: AuditQueryResult = {
      items: [mockEvent],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    it('updates audit query result and selected audit event', () => {
      adminStore.setLoadingAudit(true);
      expect(adminStore.getState().isLoadingAudit).toBe(true);

      adminStore.setAuditQueryResult(mockQueryResult);
      expect(adminStore.getState().auditQueryResult?.items).toHaveLength(1);

      adminStore.setLoadingAudit(false);
      expect(adminStore.getState().isLoadingAudit).toBe(false);

      adminStore.setSelectedAuditEvent(mockEvent);
      expect(adminStore.getState().selectedAuditEvent?.sequenceNumber).toBe(1);

      adminStore.setSelectedAuditEvent(null);
      expect(adminStore.getState().selectedAuditEvent).toBeNull();
    });

    it('updates audit filter parameters', () => {
      adminStore.setAuditFilter({ category: 'KEY', page: 2 });
      expect(adminStore.getState().auditFilter.category).toBe('KEY');
      expect(adminStore.getState().auditFilter.page).toBe(2);
    });

    it('handles audit hash chain integrity state', () => {
      adminStore.setIsVerifyingAudit(true);
      expect(adminStore.getState().isVerifyingAudit).toBe(true);

      const mockIntegrity: AuditIntegrityResult = {
        valid: true,
        checkedCount: 150,
        tamperedSequence: null,
        failureReason: null,
      };

      adminStore.setAuditIntegrity(mockIntegrity);
      expect(adminStore.getState().auditIntegrity?.valid).toBe(true);

      adminStore.setIsVerifyingAudit(false);
      expect(adminStore.getState().isVerifyingAudit).toBe(false);
    });
  });

  describe('Credential Oversight Store Management', () => {
    const mockLifecycle: CredentialLifecycleResponse = {
      credentialId: 'cred-uuid-456',
      issuerId: 'urn:zkdoc:issuer:gov',
      status: 'ACTIVE',
      version: 1,
    };

    const mockHistoryEvent: CredentialLifecycleEvent = {
      id: 'evt-1',
      credentialId: 'cred-uuid-456',
      eventType: 'CREATION',
      actor: 'urn:zkdoc:issuer:gov',
      actorRole: 'ISSUER',
      fromStatus: 'ACTIVE',
      toStatus: 'ACTIVE',
      reason: 'Initial issuance',
      timestamp: '2026-01-01T00:00:00Z',
    };

    it('updates credential lifecycle state, history, and clears on reset', () => {
      adminStore.setCredentialOversight({
        credentialId: 'cred-uuid-456',
        lifecycle: mockLifecycle,
        history: [mockHistoryEvent],
      });

      expect(adminStore.getState().credentialOversight.credentialId).toBe('cred-uuid-456');
      expect(adminStore.getState().credentialOversight.lifecycle?.status).toBe('ACTIVE');
      expect(adminStore.getState().credentialOversight.history).toHaveLength(1);

      adminStore.resetCredentialOversight();
      expect(adminStore.getState().credentialOversight.credentialId).toBe('');
      expect(adminStore.getState().credentialOversight.lifecycle).toBeNull();
      expect(adminStore.getState().credentialOversight.history).toEqual([]);
    });
  });

  describe('Listener Subscriptions', () => {
    it('notifies subscribers upon state change', () => {
      let callCount = 0;
      const unsubscribe = adminStore.subscribe(() => {
        callCount++;
      });

      adminStore.setLoadingKeys(true);
      expect(callCount).toBe(1);

      adminStore.setLoadingKeys(false);
      expect(callCount).toBe(2);

      unsubscribe();
      adminStore.setLoadingKeys(true);
      expect(callCount).toBe(2);
    });
  });
});
