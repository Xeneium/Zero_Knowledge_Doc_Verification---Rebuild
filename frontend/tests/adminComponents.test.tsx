import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { SystemOverviewCard } from '../src/features/admin/components/SystemOverviewCard';
import { KeyRegistryCard } from '../src/features/admin/components/KeyRegistryCard';
import { RegisterKeyModal } from '../src/features/admin/components/RegisterKeyModal';
import { KeyStatusModal } from '../src/features/admin/components/KeyStatusModal';
import { KeyRotateModal } from '../src/features/admin/components/KeyRotateModal';
import { KeyPemModal } from '../src/features/admin/components/KeyPemModal';
import { CredentialOversightCard } from '../src/features/admin/components/CredentialOversightCard';
import { PredicateInspectionCard } from '../src/features/admin/components/PredicateInspectionCard';
import { AdminDashboardPage } from '../src/routes/AdminDashboardPage';
import { adminStore } from '../src/state/adminStore';
import { authStore } from '../src/state/authStore';
import { adminApi } from '../src/api/adminApi';
import type { KeyMetadataResponse, RecoveryReconciliationReport } from '../src/api/types';

describe('Admin Components & Dashboard Page', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    authStore.clearSession();
    adminStore.reset();
  });

  const setAuthAdmin = () => {
    const payload = btoa(
      JSON.stringify({
        sub: 'urn:zkdoc:admin:operator1',
        roles: ['ADMIN'],
        iss: 'https://auth.zkdoc.internal',
        aud: 'zkdoc-api',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    authStore.setToken(`header.${payload}.sig`);
  };

  const mockKey: KeyMetadataResponse = {
    id: 'key-uuid-1',
    keyId: 'mock-key-1',
    publicKeyPem: '-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...\n-----END PUBLIC KEY-----',
    algorithm: 'RSA-PSS',
    status: 'ACTIVE',
    purpose: 'CREDENTIAL_SIGNING',
    ownerType: 'ISSUER',
    ownerId: 'issuer-alpha',
    fingerprint: 'abc123sha256fingerprint',
    replacementKeyId: null,
    createdAt: '2026-01-01T00:00:00Z',
    activatedAt: '2026-01-01T00:00:00Z',
    retiredAt: null,
    revokedAt: null,
  };

  const mockRecoveryReport: RecoveryReconciliationReport = {
    timestamp: '2026-10-15T09:00:00Z',
    status: 'HEALTHY',
    schemaValid: true,
    schemaVersion: 'V2',
    totalArtifactsChecked: 10,
    validArtifacts: 10,
    missingArtifacts: [],
    corruptedArtifacts: [],
    totalKeysChecked: 1,
    activeKeys: 1,
    revokedKeys: 0,
    invalidKeys: [],
    totalCredentialsChecked: 5,
    validCredentials: 5,
    invalidCredentials: [],
    auditChainValid: true,
    totalAuditEvents: 42,
    auditFailureReason: null,
    summary: 'All cryptographic anchors and records verified',
  };

  describe('SystemOverviewCard', () => {
    it('renders system health and triggers reconciliation modal', async () => {
      setAuthAdmin();
      vi.spyOn(adminApi, 'getRecoveryStatus').mockResolvedValue(mockRecoveryReport);
      const reconcileSpy = vi.spyOn(adminApi, 'reconcileRecovery').mockResolvedValueOnce({
        ...mockRecoveryReport,
        summary: 'All records reconciled successfully',
      });

      render(<SystemOverviewCard />);

      expect(screen.getByText('Platform Integrity & Recovery Overview')).toBeInTheDocument();

      // Click Refresh Status to populate report
      const refreshBtn = screen.getByRole('button', { name: /Refresh recovery status/i });
      fireEvent.click(refreshBtn);

      await waitFor(() => {
        expect(screen.getByText('HEALTHY')).toBeInTheDocument();
        expect(screen.getByText(/All cryptographic anchors and records verified/i)).toBeInTheDocument();
      });

      // Open reconciliation modal
      const reconcileBtn = screen.getByRole('button', { name: /Run post-recovery reconciliation/i });
      fireEvent.click(reconcileBtn);

      expect(screen.getByText('Execute Platform Reconciliation')).toBeInTheDocument();

      // Confirm
      const confirmBtn = screen.getByRole('button', { name: /Execute Reconciliation/i });
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(reconcileSpy).toHaveBeenCalled();
        expect(screen.getByText('System-wide reconciliation completed successfully.')).toBeInTheDocument();
      });
    });
  });

  describe('KeyRegistryCard', () => {
    it('renders key registry table, fetches keys, and displays status badges', async () => {
      setAuthAdmin();
      vi.spyOn(adminApi, 'getKeys').mockResolvedValue([mockKey]);

      render(<KeyRegistryCard />);

      expect(screen.getByText('Cryptographic Trust Registry & Public Keys')).toBeInTheDocument();

      // Click Refresh to fetch
      const refreshBtn = screen.getByRole('button', { name: /Refresh key registry list/i });
      fireEvent.click(refreshBtn);

      await waitFor(() => {
        expect(screen.getByText('mock-key-1')).toBeInTheDocument();
      });

      expect(screen.getByText('RSA-PSS')).toBeInTheDocument();
      expect(screen.getByText('ACTIVE')).toBeInTheDocument();
      expect(screen.getByText('issuer-alpha (ISSUER)')).toBeInTheDocument();
    });

    it('opens PEM view modal when View PEM button is clicked', () => {
      setAuthAdmin();
      adminStore.setKeys([mockKey]);

      render(<KeyRegistryCard />);

      expect(screen.getByText('mock-key-1')).toBeInTheDocument();

      const pemBtn = screen.getByRole('button', { name: /View PEM for mock-key-1/i });
      fireEvent.click(pemBtn);

      expect(screen.getByText(/Public Key Specification: mock-key-1/i)).toBeInTheDocument();
    });
  });

  describe('RegisterKeyModal', () => {
    it('submits registration form with public key PEM', async () => {
      const onSuccess = vi.fn();
      const onClose = vi.fn();
      const registerSpy = vi.spyOn(adminApi, 'registerKey').mockResolvedValueOnce({
        id: 'new-key-1',
        keyId: 'new-key-1',
      });

      render(
        <RegisterKeyModal isOpen={true} onClose={onClose} onSuccess={onSuccess} />,
      );

      expect(screen.getByText('Register Cryptographic Public Key')).toBeInTheDocument();

      fireEvent.change(screen.getByLabelText(/Key Identifier/i), {
        target: { value: 'new-key-1' },
      });
      fireEvent.change(screen.getByLabelText(/Public Key \(X\.509 PEM\)/i), {
        target: { value: '-----BEGIN PUBLIC KEY-----\nABCD\n-----END PUBLIC KEY-----' },
      });
      fireEvent.change(screen.getByLabelText(/Owner ID/i), {
        target: { value: 'issuer-omega' },
      });

      const submitBtn = screen.getByRole('button', { name: /Register Key Anchor/i });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(registerSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            keyId: 'new-key-1',
            ownerId: 'issuer-omega',
          }),
        );
        expect(onSuccess).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
      });
    });
  });

  describe('KeyStatusModal', () => {
    it('renders allowed status transitions and warns on REVOKED', async () => {
      const onSuccess = vi.fn();
      const onClose = vi.fn();
      const changeStatusSpy = vi.spyOn(adminApi, 'changeKeyStatus').mockResolvedValueOnce(undefined);

      render(
        <KeyStatusModal
          keyMetadata={mockKey}
          isOpen={true}
          onClose={onClose}
          onSuccess={onSuccess}
        />,
      );

      expect(screen.getByText(/Update Key Lifecycle State: mock-key-1/i)).toBeInTheDocument();

      // Change status to REVOKED
      const statusSelect = screen.getByLabelText(/Target Status/i);
      fireEvent.change(statusSelect, { target: { value: 'REVOKED' } });

      expect(
        screen.getByText(/REVOKED is a terminal cryptographic lifecycle state/i),
      ).toBeInTheDocument();

      const reasonInput = screen.getByLabelText(/Transition Reason \/ Audit Code/i);
      fireEvent.change(reasonInput, { target: { value: 'Security audit test' } });

      const submitBtn = screen.getByRole('button', { name: /Update Lifecycle Status/i });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(changeStatusSpy).toHaveBeenCalledWith('mock-key-1', {
          status: 'REVOKED',
          reason: 'Security audit test',
          replacementKeyId: undefined,
        });
        expect(onSuccess).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
      });
    });
  });

  describe('KeyRotateModal', () => {
    it('submits rotation request with replacement key identifier', async () => {
      const onSuccess = vi.fn();
      const onClose = vi.fn();
      const rotateSpy = vi.spyOn(adminApi, 'rotateKey').mockResolvedValueOnce(undefined);

      render(
        <KeyRotateModal
          keyMetadata={mockKey}
          isOpen={true}
          onClose={onClose}
          onSuccess={onSuccess}
        />,
      );

      expect(screen.getByText(/Rotate Key Anchor: mock-key-1/i)).toBeInTheDocument();

      const input = screen.getByLabelText(/Replacement Key Identifier/i);
      fireEvent.change(input, { target: { value: 'replacement-key-2' } });

      const submitBtn = screen.getByRole('button', { name: /Execute Key Rotation/i });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(rotateSpy).toHaveBeenCalledWith('mock-key-1', {
          replacementKeyId: 'replacement-key-2',
        });
        expect(onSuccess).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
      });
    });
  });

  describe('KeyPemModal', () => {
    it('displays public key PEM certificate and copies to clipboard', () => {
      const onClose = vi.fn();
      const writeTextMock = vi.fn().mockResolvedValue(undefined);
      Object.assign(navigator, {
        clipboard: { writeText: writeTextMock },
      });

      render(
        <KeyPemModal keyMetadata={mockKey} isOpen={true} onClose={onClose} />,
      );

      expect(screen.getByText(/Public Key Specification: mock-key-1/i)).toBeInTheDocument();
      expect(screen.getByText(/RSA-PSS/i)).toBeInTheDocument();

      const copyBtn = screen.getByRole('button', { name: /Copy PEM/i });
      fireEvent.click(copyBtn);

      expect(writeTextMock).toHaveBeenCalledWith(mockKey.publicKeyPem);
    });
  });

  describe('CredentialOversightCard', () => {
    it('looks up credential lifecycle, verifies signature, and executes suspend action', async () => {
      setAuthAdmin();
      vi.spyOn(adminApi, 'getCredentialLifecycle').mockResolvedValue({
        credentialId: 'cred-999',
        issuerId: 'urn:zkdoc:issuer:gov',
        status: 'ACTIVE',
        version: 1,
      });
      vi.spyOn(adminApi, 'getCredentialLifecycleHistory').mockResolvedValue([
        {
          id: 'ev-1',
          credentialId: 'cred-999',
          eventType: 'ISSUED',
          actor: 'urn:zkdoc:issuer:gov',
          actorRole: 'ISSUER',
          fromStatus: 'ACTIVE',
          toStatus: 'ACTIVE',
          reason: 'Initial creation',
          timestamp: '2026-01-01T00:00:00Z',
        },
      ]);
      vi.spyOn(adminApi, 'verifyCredential').mockResolvedValueOnce({
        credentialId: 'cred-999',
        verified: true,
        message: 'Signature verified successfully',
      });
      const suspendSpy = vi.spyOn(adminApi, 'suspendCredential').mockResolvedValueOnce({
        credentialId: 'cred-999',
        issuerId: 'urn:zkdoc:issuer:gov',
        status: 'SUSPENDED',
        version: 2,
        suspensionReason: 'Audit pending',
      });

      render(<CredentialOversightCard />);

      // Perform lookup via placeholder
      const searchInput = screen.getByPlaceholderText(/Enter Credential UUID/i);
      fireEvent.change(searchInput, { target: { value: 'cred-999' } });

      const searchBtn = screen.getByRole('button', { name: /Inspect credential/i });
      fireEvent.click(searchBtn);

      await waitFor(() => {
        expect(screen.getByText(/Issuer: urn:zkdoc:issuer:gov/i)).toBeInTheDocument();
      });

      expect(screen.getByText(/Initial creation/i)).toBeInTheDocument();

      // Signature verification is performed automatically on lookup
      expect(screen.getByText('SIGNATURE VALID')).toBeInTheDocument();

      // Trigger suspend
      const suspendBtn = screen.getByRole('button', { name: /^Suspend$/i });
      fireEvent.click(suspendBtn);

      expect(screen.getByText('Confirm Credential SUSPEND')).toBeInTheDocument();

      const confirmBtn = screen.getByRole('button', { name: /Execute suspend/i });
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(suspendSpy).toHaveBeenCalledWith('cred-999', undefined);
      });
    });
  });

  describe('PredicateInspectionCard', () => {
    it('displays cryptographic proof architecture and verifier role notice', () => {
      render(<PredicateInspectionCard />);

      expect(
        screen.getByText('Zero-Knowledge Predicate & Bulletproof Operations'),
      ).toBeInTheDocument();
      expect(
        screen.getByText('Zero Knowledge Privacy'),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/Endpoint Authorization Notice/i),
      ).toBeInTheDocument();
    });
  });

  describe('AdminDashboardPage', () => {
    it('renders tabbed interface and switches between views', async () => {
      setAuthAdmin();
      vi.spyOn(adminApi, 'getRecoveryStatus').mockResolvedValue(mockRecoveryReport);
      vi.spyOn(adminApi, 'getKeys').mockResolvedValue([mockKey]);

      render(
        <MemoryRouter>
          <AdminDashboardPage />
        </MemoryRouter>,
      );

      expect(screen.getByText('Platform Administration & Trust Governance')).toBeInTheDocument();

      // Overview tab is active
      expect(screen.getByText('Platform Integrity & Recovery Overview')).toBeInTheDocument();

      // Click Key Registry tab
      const keysTab = screen.getByRole('tab', { name: /Trust Registry & Keys/i });
      fireEvent.click(keysTab);

      await waitFor(() => {
        expect(screen.getByText('Cryptographic Trust Registry & Public Keys')).toBeInTheDocument();
      });

      // Click Credential Oversight tab
      const credTab = screen.getByRole('tab', { name: /Credential Oversight/i });
      fireEvent.click(credTab);

      expect(screen.getByText('Credential Oversight & Lifecycle Administration')).toBeInTheDocument();

      // Click Predicate Operations tab
      const predicateTab = screen.getByRole('tab', { name: /Predicate-Proof Operations/i });
      fireEvent.click(predicateTab);

      expect(screen.getByText('Zero-Knowledge Predicate & Bulletproof Operations')).toBeInTheDocument();
    });
  });
});
