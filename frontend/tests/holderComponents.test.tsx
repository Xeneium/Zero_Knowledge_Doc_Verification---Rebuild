import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import {
  HolderCredentialsListCard,
  ProvisionCredentialCard,
  HolderCredentialDetailModal,
  PredicateProofGeneratorCard,
  HolderCapabilityQrCard,
} from '../src/features/holder';
import { HolderDashboardPage } from '../src/routes/HolderDashboardPage';
import { authStore } from '../src/state/authStore';
import { holderStore } from '../src/state/holderStore';
import { holderApi } from '../src/api/holderApi';
import type {
  HolderCredentialResponse,
  HolderPredicateProofResponse,
  CapabilityResponse,
  CredentialVerificationResult,
} from '../src/api/types';

describe('Holder Workflows & UI Components', () => {
  const mockCredential: HolderCredentialResponse = {
    id: 'hc-uuid-1',
    holderId: 'urn:zkdoc:holder:alice',
    credentialId: '550e8400-e29b-41d4-a716-446655440000',
    credentialDigestHex:
      'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90',
    status: 'ACTIVE',
    version: 1,
    provisionedAt: '2026-10-15T00:00:00Z',
    updatedAt: '2026-10-15T00:00:00Z',
  };

  beforeEach(() => {
    authStore.clearSession();
    holderStore.reset();
    window.sessionStorage.clear();
    vi.restoreAllMocks();

    // Authenticate as test holder
    const payload = btoa(
      JSON.stringify({
        sub: 'urn:zkdoc:holder:alice',
        roles: ['ROLE_HOLDER'],
        exp: 9999999999,
      }),
    );
    authStore.setToken(`eyJhbGciOiJSUzI1NiJ9.${payload}.sig`);
  });

  describe('HolderCredentialsListCard', () => {
    it('fetches credentials on mount and renders wallet stats', async () => {
      const listSpy = vi
        .spyOn(holderApi, 'listCredentials')
        .mockResolvedValueOnce([mockCredential]);

      render(<HolderCredentialsListCard />);

      await waitFor(() => {
        expect(screen.getByText(/550e8400.*55440000/)).toBeInTheDocument();
      });

      expect(listSpy).toHaveBeenCalledTimes(1);
      // Metric badges
      expect(screen.getByText('Total Wallet Items')).toBeInTheDocument();
      expect(screen.getAllByText('1')).toHaveLength(2);
      expect(screen.getAllByText('ACTIVE').length).toBeGreaterThanOrEqual(1);
    });

    it('renders empty state when wallet has no credentials', async () => {
      vi.spyOn(holderApi, 'listCredentials').mockResolvedValueOnce([]);

      render(<HolderCredentialsListCard />);

      await waitFor(() => {
        expect(
          screen.getByText(/no credentials in wallet/i),
        ).toBeInTheDocument();
      });
    });

    it('filters credentials by status and search query', async () => {
      const credActive: HolderCredentialResponse = {
        ...mockCredential,
        id: 'hc-active',
        credentialId: '11111111-1111-1111-1111-111111111111',
        status: 'ACTIVE',
      };
      const credSuspended: HolderCredentialResponse = {
        ...mockCredential,
        id: 'hc-suspended',
        credentialId: '22222222-2222-2222-2222-222222222222',
        status: 'SUSPENDED',
      };

      vi.spyOn(holderApi, 'listCredentials').mockResolvedValueOnce([
        credActive,
        credSuspended,
      ]);

      render(<HolderCredentialsListCard />);

      await waitFor(() => {
        expect(screen.getByText(/11111111.*11111111/)).toBeInTheDocument();
      });

      expect(screen.getByText(/22222222.*22222222/)).toBeInTheDocument();

      // Click "ACTIVE" filter
      const activeBtn = screen.getByRole('button', { name: /^active$/i });
      await userEvent.click(activeBtn);

      expect(screen.getByText(/11111111.*11111111/)).toBeInTheDocument();
      expect(screen.queryByText(/22222222.*22222222/)).not.toBeInTheDocument();

      // Search query
      const searchInput = screen.getByPlaceholderText(/search uuid or digest/i);
      await userEvent.clear(searchInput);
      await userEvent.type(searchInput, '1111');

      expect(screen.getByText(/11111111.*11111111/)).toBeInTheDocument();
    });

    it('triggers suspend modal and executes self-suspension', async () => {
      vi.spyOn(holderApi, 'listCredentials').mockResolvedValueOnce([
        mockCredential,
      ]);
      const suspendedResp: HolderCredentialResponse = {
        ...mockCredential,
        status: 'SUSPENDED',
        version: 2,
      };
      const suspendSpy = vi
        .spyOn(holderApi, 'suspendCredential')
        .mockResolvedValueOnce(suspendedResp);

      render(<HolderCredentialsListCard />);

      await waitFor(() => {
        expect(
          screen.getByLabelText(/suspend credential/i),
        ).toBeInTheDocument();
      });

      await userEvent.click(screen.getByLabelText(/suspend credential/i));

      expect(
        screen.getByRole('heading', { name: /suspend credential/i }),
      ).toBeInTheDocument();

      const confirmBtn = screen.getByRole('button', {
        name: /confirm suspension/i,
      });
      await userEvent.click(confirmBtn);

      await waitFor(() => {
        expect(suspendSpy).toHaveBeenCalledWith(mockCredential.id);
      });
    });
  });

  describe('ProvisionCredentialCard', () => {
    it('validates UUID format and provisions credential to wallet', async () => {
      const provisionSpy = vi
        .spyOn(holderApi, 'provisionCredential')
        .mockResolvedValueOnce(mockCredential);

      render(<ProvisionCredentialCard />);

      const input = screen.getByLabelText(/credential identifier/i);
      const submitBtn = screen.getByRole('button', {
        name: /provision to wallet/i,
      });

      // Type invalid UUID
      await userEvent.type(input, 'not-a-uuid');
      await userEvent.click(submitBtn);

      expect(screen.getByText(/invalid uuid format/i)).toBeInTheDocument();
      expect(provisionSpy).not.toHaveBeenCalled();

      // Type valid UUID
      await userEvent.clear(input);
      await userEvent.type(input, mockCredential.credentialId);
      await userEvent.click(submitBtn);

      await waitFor(() => {
        expect(
          screen.getByText(/credential successfully provisioned/i),
        ).toBeInTheDocument();
      });

      expect(provisionSpy).toHaveBeenCalledWith({
        credentialId: mockCredential.credentialId,
      });
      expect(holderStore.getState().credentials).toHaveLength(1);
    });
  });

  describe('HolderCredentialDetailModal', () => {
    it('renders full metadata, verifies signature, and fetches lifecycle history', async () => {
      const verifyResult: CredentialVerificationResult = {
        credentialId: mockCredential.credentialId,
        verified: true,
        message: 'Signature matches key registry',
        status: 'ACTIVE',
      };
      const verifySpy = vi
        .spyOn(holderApi, 'verifyCredential')
        .mockResolvedValueOnce(verifyResult);

      const historyEvents = [
        {
          id: 'ev-1',
          credentialId: mockCredential.credentialId,
          eventType: 'CREDENTIAL_ISSUED',
          actor: 'urn:zkdoc:issuer:bob',
          actorRole: 'ISSUER',
          fromStatus: 'ACTIVE' as const,
          toStatus: 'ACTIVE' as const,
          timestamp: '2026-10-14T10:00:00Z',
        },
      ];
      const historySpy = vi
        .spyOn(holderApi, 'getCredentialLifecycleHistory')
        .mockResolvedValueOnce(historyEvents);

      render(
        <HolderCredentialDetailModal
          isOpen={true}
          onClose={vi.fn()}
          credential={mockCredential}
        />,
      );

      expect(
        screen.getByRole('heading', { name: /holder credential inspection/i }),
      ).toBeInTheDocument();
      expect(screen.getByText(mockCredential.id)).toBeInTheDocument();
      expect(screen.getByText(mockCredential.credentialId)).toBeInTheDocument();

      // Verify signature action
      const verifyBtn = screen.getByRole('button', {
        name: /verify signature/i,
      });
      await userEvent.click(verifyBtn);

      await waitFor(() => {
        expect(verifySpy).toHaveBeenCalledWith(mockCredential.credentialId);
      });
      expect(screen.getByText(/verified & active/i)).toBeInTheDocument();

      // Fetch history action
      const historyBtn = screen.getByRole('button', {
        name: /fetch history/i,
      });
      await userEvent.click(historyBtn);

      await waitFor(() => {
        expect(historySpy).toHaveBeenCalledWith(mockCredential.credentialId);
      });
      expect(screen.getByText('CREDENTIAL_ISSUED')).toBeInTheDocument();
    });
  });

  describe('PredicateProofGeneratorCard', () => {
    it('generates zero-knowledge Bulletproof proof and displays artifact', async () => {
      holderStore.setCredentials([mockCredential]);

      const mockProofResp: HolderPredicateProofResponse = {
        holderCredentialId: mockCredential.id,
        credentialId: mockCredential.credentialId,
        credentialDigestHex: mockCredential.credentialDigestHex,
        attributeName: 'age',
        operator: '>=',
        threshold: 18,
        commitmentPointHex: 'pt-hex-1234567890abcdef',
        attestationSignatureHex: 'sig-hex-abcdef1234567890',
        attestationKeyId: 'default-issuer-key-1',
        nonceHex: '0102030405060708',
        proofVersion: 'V1_BULLETPROOF',
        bitSize: 64,
        proofBytesHex: 'deadbeefcafe1234567890abcdef1234567890',
      };

      const proofSpy = vi
        .spyOn(holderApi, 'generatePredicateProof')
        .mockResolvedValueOnce(mockProofResp);

      render(
        <PredicateProofGeneratorCard
          initialHolderCredentialId={mockCredential.id}
        />,
      );

      const generateBtn = screen.getByRole('button', {
        name: /generate zero-knowledge proof/i,
      });
      await userEvent.click(generateBtn);

      await waitFor(() => {
        expect(
          screen.getByText(/zero-knowledge proof artifact generated/i),
        ).toBeInTheDocument();
      });

      expect(proofSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          holderCredentialId: mockCredential.id,
          attributeName: 'age',
          operator: '>=',
          threshold: 18,
        }),
      );

      expect(screen.getByText(/v1_bulletproof/i)).toBeInTheDocument();
      expect(holderStore.getState().generatedProofs).toHaveLength(1);
    });
  });

  describe('HolderCapabilityQrCard', () => {
    it('mints verification capability and renders QR blob image', async () => {
      holderStore.setCredentials([mockCredential]);

      const mockCap: CapabilityResponse = {
        id: 'cap-1',
        capabilityToken: 'tok-xyz-987654321',
        mode: 'MODE_A_PRESENTED_ARTIFACT',
        protocolVersion: '1.0',
        status: 'ACTIVE',
        expiresAt: '2026-10-16T00:00:00Z',
      };

      const capSpy = vi
        .spyOn(holderApi, 'createCapability')
        .mockResolvedValueOnce(mockCap);
      const qrSpy = vi
        .spyOn(holderApi, 'getCapabilityQrBlob')
        .mockResolvedValueOnce(new Blob(['qr-bytes'], { type: 'image/png' }));

      render(
        <HolderCapabilityQrCard
          initialCredentialId={mockCredential.credentialId}
        />,
      );

      const mintBtn = screen.getByRole('button', {
        name: /mint capability qr/i,
      });
      await userEvent.click(mintBtn);

      await waitFor(() => {
        expect(capSpy).toHaveBeenCalledWith({
          credentialId: mockCredential.credentialId,
          mode: 'MODE_A_PRESENTED_ARTIFACT',
          ttlSeconds: 3600,
        });
      });

      expect(qrSpy).toHaveBeenCalledWith('tok-xyz-987654321');
      expect(screen.getByText(/active capability token/i)).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /download png/i }),
      ).toBeInTheDocument();
    });
  });

  describe('HolderDashboardPage Integration', () => {
    it('renders holder operations workspace with active role and switches tabs', async () => {
      vi.spyOn(holderApi, 'listCredentials').mockResolvedValueOnce([
        mockCredential,
      ]);

      render(
        <MemoryRouter initialEntries={['/credentials']}>
          <Routes>
            <Route path="/credentials" element={<HolderDashboardPage />} />
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.getByRole('heading', {
          name: /holder credential wallet & privacy engine/i,
        }),
      ).toBeInTheDocument();
      expect(screen.getByText(/role_holder active/i)).toBeInTheDocument();
      expect(screen.getByText('urn:zkdoc:holder:alice')).toBeInTheDocument();

      // Switch to Provision Credential tab
      const provisionTab = screen.getByRole('tab', {
        name: /provision credential/i,
      });
      await userEvent.click(provisionTab);

      expect(
        screen.getByRole('heading', {
          name: /provision issued credential to wallet/i,
        }),
      ).toBeInTheDocument();

      // Switch to Zero-Knowledge Proofs tab
      const zkpTab = screen.getByRole('tab', {
        name: /zero-knowledge proofs/i,
      });
      await userEvent.click(zkpTab);

      expect(
        screen.getByRole('heading', {
          name: /zero-knowledge predicate proof generator/i,
        }),
      ).toBeInTheDocument();

      // Switch to Capability QR Codes tab
      const qrTab = screen.getByRole('tab', {
        name: /capability qr codes/i,
      });
      await userEvent.click(qrTab);

      expect(
        screen.getByRole('heading', {
          name: /mint verification capability & qr code/i,
        }),
      ).toBeInTheDocument();
    });
  });
});
