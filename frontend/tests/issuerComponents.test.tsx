import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import {
  DocumentUploadCard,
  CredentialIssuanceCard,
  CredentialLifecycleCard,
  DocumentVerifyQrCard,
  IssuerActivityTable,
} from '../src/features/issuer';
import { IssuerDashboardPage } from '../src/routes/IssuerDashboardPage';
import { authStore } from '../src/state/authStore';
import { issuerStore } from '../src/state/issuerStore';
import { issuerApi } from '../src/api/issuerApi';
import type {
  DocumentVerificationResult,
  CapabilityResponse,
} from '../src/api/types';

describe('Issuer Workflows & UI Components', () => {
  beforeEach(() => {
    authStore.clearSession();
    issuerStore.clearActivity();
    window.sessionStorage.clear();
    vi.restoreAllMocks();

    // Authenticate as test issuer
    const payload = btoa(
      JSON.stringify({
        sub: 'urn:zkdoc:issuer:alice',
        roles: ['ROLE_ISSUER'],
        exp: 9999999999,
      }),
    );
    authStore.setToken(`header.${payload}.sig`);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('DocumentUploadCard', () => {
    it('renders upload card with drag-and-drop dropzone and 25 MB bound', () => {
      render(<DocumentUploadCard />);

      expect(
        screen.getByRole('heading', { name: /secure document upload/i }),
      ).toBeInTheDocument();
      expect(screen.getByText(/25 MB Max Bound/i)).toBeInTheDocument();
      expect(
        screen.getByText(/Click to select or drag and drop document artifact/i),
      ).toBeInTheDocument();
    });

    it('displays validation error when file exceeds 25 MB limit', async () => {
      render(<DocumentUploadCard />);

      const file = new File(['content'], 'oversize.pdf', {
        type: 'application/pdf',
      });
      Object.defineProperty(file, 'size', { value: 26 * 1024 * 1024 });

      const input = document.querySelector(
        'input[type="file"]',
      ) as HTMLInputElement;
      expect(input).not.toBeNull();

      await userEvent.upload(input, file);

      expect(
        await screen.findByText(/exceeds maximum limit of 25 MB/i),
      ).toBeInTheDocument();
    });

    it('uploads valid document and displays authoritative UUID and SHA-256 digest', async () => {
      const mockResult = {
        id: '123e4567-e89b-12d3-a456-426614174000',
        digest:
          'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      };
      vi.spyOn(issuerApi, 'uploadDocument').mockResolvedValueOnce(mockResult);

      const onSuccess = vi.fn();
      render(<DocumentUploadCard onSuccess={onSuccess} />);

      const file = new File(['authentic document bytes'], 'diploma.pdf', {
        type: 'application/pdf',
      });
      const input = document.querySelector(
        'input[type="file"]',
      ) as HTMLInputElement;

      await userEvent.upload(input, file);

      expect(screen.getByText('diploma.pdf')).toBeInTheDocument();

      const uploadBtn = screen.getByRole('button', {
        name: /upload & secure document/i,
      });
      await userEvent.click(uploadBtn);

      await waitFor(() => {
        expect(
          screen.getByText(/document successfully uploaded & secured/i),
        ).toBeInTheDocument();
      });

      expect(screen.getByText(mockResult.id)).toBeInTheDocument();
      expect(onSuccess).toHaveBeenCalledWith(mockResult);

      // Recorded in issuerStore
      expect(issuerStore.getState().activities[0].id).toBe(mockResult.id);
    });
  });

  describe('CredentialIssuanceCard', () => {
    it('renders credential issuance form with prefilled issuer identity', () => {
      render(<CredentialIssuanceCard />);

      expect(
        screen.getByRole('heading', { name: /direct credential issuance/i }),
      ).toBeInTheDocument();
      expect(screen.getByText(/Object Auth Enforced/i)).toBeInTheDocument();

      const issuerInput = screen.getByLabelText(/issuer identity/i);
      expect(issuerInput).toHaveValue('urn:zkdoc:issuer:alice');
    });

    it('adds and removes dynamic credential attributes', async () => {
      render(<CredentialIssuanceCard />);

      // Initially 3 default attributes
      expect(screen.getByDisplayValue('Jane Doe')).toBeInTheDocument();
      expect(screen.getByDisplayValue('Research Fellow')).toBeInTheDocument();

      const addBtn = screen.getByRole('button', {
        name: /add new attribute row/i,
      });
      await userEvent.click(addBtn);

      const attributeInputs = screen.getAllByPlaceholderText(/attribute name/i);
      expect(attributeInputs).toHaveLength(4);

      // Remove last attribute
      const removeButtons = screen.getAllByLabelText(/remove attribute/i);
      await userEvent.click(removeButtons[removeButtons.length - 1]);

      expect(screen.getAllByPlaceholderText(/attribute name/i)).toHaveLength(3);
    });

    it('validates required fields, opens review dialog, and successfully issues credential', async () => {
      const mockIssued = {
        credentialId: '987e6543-e21b-43d2-b654-426614174111',
        digest:
          'f4b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b999',
      };
      vi.spyOn(issuerApi, 'issueCredential').mockResolvedValueOnce(mockIssued);

      const onSuccess = vi.fn();
      render(<CredentialIssuanceCard onSuccess={onSuccess} />);

      const reviewBtn = screen.getByRole('button', {
        name: /review & issue credential/i,
      });
      await userEvent.click(reviewBtn);

      // Review dialog opens
      expect(
        await screen.findByText(/confirm credential issuance/i),
      ).toBeInTheDocument();
      expect(screen.getByText(/urn:zkdoc:issuer:alice/i)).toBeInTheDocument();

      const confirmBtn = screen.getByRole('button', {
        name: /confirm & authorize/i,
      });
      await userEvent.click(confirmBtn);

      await waitFor(() => {
        expect(
          screen.getByText(/credential successfully issued & signed/i),
        ).toBeInTheDocument();
      });

      expect(screen.getByText(mockIssued.credentialId)).toBeInTheDocument();
      expect(onSuccess).toHaveBeenCalledWith(mockIssued);
      expect(issuerStore.getState().activities[0].id).toBe(
        mockIssued.credentialId,
      );
    });
  });

  describe('CredentialLifecycleCard', () => {
    it('looks up credential lifecycle and displays status and metadata', async () => {
      const mockLifecycle = {
        credentialId: 'cred-uuid-555',
        issuerId: 'urn:zkdoc:issuer:alice',
        status: 'ACTIVE' as const,
        version: 1,
        issuedAt: '2026-10-14T10:00:00Z',
      };
      vi.spyOn(issuerApi, 'getCredentialLifecycle').mockResolvedValueOnce(
        mockLifecycle,
      );

      render(<CredentialLifecycleCard />);

      const input = screen.getByLabelText(/credential id/i);
      await userEvent.type(input, 'cred-uuid-555');

      const fetchBtn = screen.getByRole('button', { name: /fetch status/i });
      await userEvent.click(fetchBtn);

      await waitFor(() => {
        expect(
          screen.getByText(/current lifecycle status/i),
        ).toBeInTheDocument();
      });

      expect(screen.getByText('ACTIVE')).toBeInTheDocument();
      expect(screen.getByText('Version: 1')).toBeInTheDocument();
    });

    it('triggers suspend modal and transitions credential status', async () => {
      const mockActive = {
        credentialId: 'cred-uuid-555',
        issuerId: 'urn:zkdoc:issuer:alice',
        status: 'ACTIVE' as const,
        version: 1,
      };
      const mockSuspended = {
        credentialId: 'cred-uuid-555',
        issuerId: 'urn:zkdoc:issuer:alice',
        status: 'SUSPENDED' as const,
        version: 2,
        message: 'Credential suspended',
      };

      vi.spyOn(issuerApi, 'getCredentialLifecycle').mockResolvedValueOnce(
        mockActive,
      );
      const suspendSpy = vi
        .spyOn(issuerApi, 'suspendCredential')
        .mockResolvedValueOnce(mockSuspended);

      render(<CredentialLifecycleCard initialCredentialId="cred-uuid-555" />);

      await waitFor(() => {
        expect(
          screen.getByRole('button', { name: /suspend credential/i }),
        ).toBeInTheDocument();
      });

      await userEvent.click(
        screen.getByRole('button', { name: /suspend credential/i }),
      );

      // Confirmation modal
      expect(
        await screen.findByText(/confirm suspend credential/i),
      ).toBeInTheDocument();

      const reasonInput = screen.getByPlaceholderText(
        /identity re-verification required/i,
      );
      fireEvent.change(reasonInput, {
        target: { value: 'Temporary suspension for audit' },
      });

      const confirmBtn = screen.getByRole('button', {
        name: /^confirm suspend$/i,
      });
      await userEvent.click(confirmBtn);

      await waitFor(() => {
        expect(screen.getByText('Credential suspended')).toBeInTheDocument();
      });

      expect(screen.getByText('SUSPENDED')).toBeInTheDocument();
      expect(suspendSpy).toHaveBeenCalledWith(
        'cred-uuid-555',
        'Temporary suspension for audit',
      );
    });
  });

  describe('DocumentVerifyQrCard', () => {
    it('runs Mode A stored copy verification and displays result', async () => {
      const mockVerifyResult: DocumentVerificationResult = {
        id: 'doc-uuid-888',
        verified: true,
      };
      const verifySpy = vi
        .spyOn(issuerApi, 'verifyStoredDocument')
        .mockResolvedValueOnce(mockVerifyResult);

      render(<DocumentVerifyQrCard initialDocumentId="doc-uuid-888" />);

      const verifyBtn = screen.getByRole('button', {
        name: /run mode a verify/i,
      });
      await userEvent.click(verifyBtn);

      await waitFor(() => {
        expect(
          screen.getByText(/verified \(digest & signature match\)/i),
        ).toBeInTheDocument();
      });

      expect(verifySpy).toHaveBeenCalledWith('doc-uuid-888');
    });

    it('mints capability token and renders capability preview', async () => {
      const mockCap: CapabilityResponse = {
        id: 'cap-uuid-1',
        capabilityToken: 'cap-token-xyz-1234567890',
        mode: 'MODE_A_PRESENTED_ARTIFACT',
        protocolVersion: '1.0',
        status: 'ACTIVE',
        expiresAt: '2026-10-14T22:00:00Z',
      };
      vi.spyOn(issuerApi, 'createCapability').mockResolvedValueOnce(mockCap);
      vi.spyOn(issuerApi, 'getCapabilityQrBlob').mockResolvedValueOnce(
        new Blob(['qr-bytes'], { type: 'image/png' }),
      );

      render(<DocumentVerifyQrCard initialDocumentId="doc-uuid-888" />);

      const mintBtn = screen.getByRole('button', { name: /mint capability/i });
      await userEvent.click(mintBtn);

      await waitFor(() => {
        expect(screen.getByText(/capability qr ready/i)).toBeInTheDocument();
      });

      expect(screen.getByText(/cap-token-xyz-12/i)).toBeInTheDocument();
    });
  });

  describe('IssuerActivityTable', () => {
    it('displays empty state when no activities are recorded', () => {
      render(<IssuerActivityTable />);

      expect(
        screen.getByText(/no recent issuer activity in this session/i),
      ).toBeInTheDocument();
    });

    it('displays activities and filters by document and credential', async () => {
      issuerStore.addActivity({
        id: 'doc-1',
        type: 'document',
        title: 'Report.pdf',
        digest: 'digest-1',
      });
      issuerStore.addActivity({
        id: 'cred-1',
        type: 'credential',
        title: 'Alice Degree',
        digest: 'digest-2',
      });

      render(<IssuerActivityTable />);

      expect(screen.getByText('Report.pdf')).toBeInTheDocument();
      expect(screen.getByText('Alice Degree')).toBeInTheDocument();

      // Filter Documents only
      const docsBtn = screen.getByRole('button', { name: /documents \(1\)/i });
      await userEvent.click(docsBtn);

      expect(screen.getByText('Report.pdf')).toBeInTheDocument();
      expect(screen.queryByText('Alice Degree')).not.toBeInTheDocument();

      // Clear Session
      const clearBtn = screen.getByRole('button', {
        name: /clear session log/i,
      });
      await userEvent.click(clearBtn);

      expect(
        screen.getByText(/no recent issuer activity in this session/i),
      ).toBeInTheDocument();
    });
  });

  describe('IssuerDashboardPage Integration', () => {
    it('renders issuer workspace with role badge and switches tabs', async () => {
      render(
        <MemoryRouter initialEntries={['/issuer']}>
          <Routes>
            <Route path="/issuer" element={<IssuerDashboardPage />} />
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.getByRole('heading', { name: /issuer operations workspace/i }),
      ).toBeInTheDocument();
      expect(screen.getByText(/role_issuer active/i)).toBeInTheDocument();
      expect(screen.getByText('urn:zkdoc:issuer:alice')).toBeInTheDocument();

      // Overview action cards
      expect(screen.getByText(/session activity ledger/i)).toBeInTheDocument();

      // Switch to Document Upload tab
      const uploadTab = screen.getByRole('tab', { name: /document upload/i });
      await userEvent.click(uploadTab);

      expect(
        screen.getByText(/click to select or drag and drop document artifact/i),
      ).toBeInTheDocument();

      // Switch to Issue Credential tab
      const issueTab = screen.getByRole('tab', { name: /issue credential/i });
      await userEvent.click(issueTab);

      expect(
        screen.getByRole('heading', { name: /direct credential issuance/i }),
      ).toBeInTheDocument();
    });
  });
});
