import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { ManualReferenceCard } from '../src/features/verifier/components/ManualReferenceCard';
import { DocumentUploadVerifyCard } from '../src/features/verifier/components/DocumentUploadVerifyCard';
import { PredicateProofVerifyCard } from '../src/features/verifier/components/PredicateProofVerifyCard';
import { VerificationResultCard } from '../src/features/verifier/components/VerificationResultCard';
import { PublicVerificationPage } from '../src/routes/PublicVerificationPage';
import { authStore } from '../src/state/authStore';
import { verifierStore } from '../src/state/verifierStore';
import { verifierApi } from '../src/api/verifierApi';

describe('Verifier Components & Public Verification Page', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    authStore.clearSession();
    verifierStore.reset();
  });

  const setAuthenticatedVerifier = (username = 'bob') => {
    const payload = btoa(
      JSON.stringify({
        sub: `urn:zkdoc:verifier:${username}`,
        roles: ['VERIFIER'],
        iss: 'https://auth.zkdoc.internal',
        aud: 'zkdoc-api',
        exp: Math.floor(Date.now() / 1000) + 3600,
        iat: Math.floor(Date.now() / 1000),
      }),
    );
    authStore.setToken(`header.${payload}.sig`);
  };

  describe('ManualReferenceCard', () => {
    it('validates input and submits parsed canonical JSON payload', async () => {
      const onVerify = vi.fn();
      render(<ManualReferenceCard onVerify={onVerify} />);

      expect(
        screen.getByText('Manual Verification Reference Entry'),
      ).toBeInTheDocument();

      const input = screen.getByLabelText(/Verification Reference or Payload/i);
      const submitBtn = screen.getByRole('button', {
        name: /Verify Reference/i,
      });

      // Initially disabled when empty
      expect(submitBtn).toBeDisabled();

      // Enter canonical JSON
      const canonicalJson = JSON.stringify({
        v: 'QR_V1',
        m: 'MODE_B_REGISTRY_CAPABILITY',
        tok: 'valid-token-1234567890123456',
        uri: 'https://example.com/api/public/verify/valid-token-1234567890123456',
      });

      fireEvent.change(input, { target: { value: canonicalJson } });

      // Verify format preview pill appears
      expect(screen.getByText(/Detected format:/i)).toBeInTheDocument();
      expect(screen.getByText(/CANONICAL_JSON/i)).toBeInTheDocument();

      await userEvent.click(submitBtn);

      expect(onVerify).toHaveBeenCalledWith(
        expect.objectContaining({
          token: 'valid-token-1234567890123456',
          format: 'CANONICAL_JSON',
          mode: 'MODE_B_REGISTRY_CAPABILITY',
        }),
      );
    });

    it('displays error alert when invalid payload is submitted', async () => {
      const onVerify = vi.fn();
      render(<ManualReferenceCard onVerify={onVerify} />);

      const input = screen.getByLabelText(/Verification Reference or Payload/i);
      await userEvent.type(input, 'javascript:alert(1)');

      const submitBtn = screen.getByRole('button', {
        name: /Verify Reference/i,
      });
      await userEvent.click(submitBtn);

      expect(screen.getByText(/Validation Error/i)).toBeInTheDocument();
      expect(
        screen.getByText(/Dangerous or unsupported URL scheme/i),
      ).toBeInTheDocument();
      expect(onVerify).not.toHaveBeenCalled();
    });
  });

  describe('DocumentUploadVerifyCard', () => {
    it('prompts user to connect session when unauthenticated', () => {
      render(
        <MemoryRouter>
          <DocumentUploadVerifyCard
            onVerifySuccess={vi.fn()}
            onStartVerify={vi.fn()}
          />
        </MemoryRouter>,
      );

      expect(screen.getByText(/Authentication Required/i)).toBeInTheDocument();
      expect(
        screen.getByText(/Mode B document-upload verification/i),
      ).toBeInTheDocument();
      expect(
        screen.getByRole('link', { name: /Connect Session/i }),
      ).toBeInTheDocument();
    });

    it('allows document verification submission when authenticated as VERIFIER', async () => {
      setAuthenticatedVerifier('bob');

      const onStartVerify = vi.fn().mockResolvedValue(undefined);

      render(
        <MemoryRouter>
          <DocumentUploadVerifyCard
            onVerifySuccess={vi.fn()}
            onStartVerify={onStartVerify}
          />
        </MemoryRouter>,
      );

      expect(
        screen.getByText(/Mode B: Document Integrity & Upload Verification/i),
      ).toBeInTheDocument();

      const uuidInput = screen.getByLabelText(/Target Document UUID/i);
      const fileInput = screen.getByLabelText(
        /Upload document file for verification/i,
      );
      const submitBtn = screen.getByRole('button', {
        name: /Verify Document Copy/i,
      });

      // Valid UUID
      await userEvent.type(uuidInput, '550e8400-e29b-41d4-a716-446655440000');

      // Attach valid PDF file
      const testFile = new File(['test pdf content'], 'contract.pdf', {
        type: 'application/pdf',
      });
      fireEvent.change(fileInput, { target: { files: [testFile] } });

      expect(screen.getByText('contract.pdf')).toBeInTheDocument();

      await userEvent.click(submitBtn);

      expect(onStartVerify).toHaveBeenCalledWith(
        '550e8400-e29b-41d4-a716-446655440000',
        testFile,
      );
    });
  });

  describe('PredicateProofVerifyCard', () => {
    it('prompts user to connect session when unauthenticated', () => {
      render(
        <MemoryRouter>
          <PredicateProofVerifyCard onVerify={vi.fn()} />
        </MemoryRouter>,
      );

      expect(
        screen.getByText(/Verifier Authentication Required/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          /Zero-knowledge predicate verification uses CPU-intensive/i,
        ),
      ).toBeInTheDocument();
    });

    it('parses quick-pasted JSON presentation token and submits proof', async () => {
      setAuthenticatedVerifier('charlie');

      const onVerify = vi.fn().mockResolvedValue(undefined);

      render(
        <MemoryRouter>
          <PredicateProofVerifyCard onVerify={onVerify} />
        </MemoryRouter>,
      );

      const pasteInput = screen.getByLabelText(
        /Quick-Paste Presentation JSON/i,
      );

      const presentationJson = JSON.stringify({
        credentialId: '550e8400-e29b-41d4-a716-446655440000',
        attributeName: 'income',
        threshold: 75000,
        proofVersion: 'BULLETPROOF_V1',
        nonceHex: '0102030405060708090a0b0c0d0e0f10',
        proofBytesHex: 'aabbccddeeff00112233445566778899',
      });

      fireEvent.change(pasteInput, { target: { value: presentationJson } });

      expect(
        screen.getByText(/Successfully parsed presentation token/i),
      ).toBeInTheDocument();

      const submitBtn = screen.getByRole('button', {
        name: /Verify ZKP Proof/i,
      });
      await userEvent.click(submitBtn);

      expect(onVerify).toHaveBeenCalledWith(
        expect.objectContaining({
          credentialId: '550e8400-e29b-41d4-a716-446655440000',
          attributeName: 'income',
          threshold: 75000,
          proofVersion: 'BULLETPROOF_V1',
        }),
      );
    });
  });

  describe('VerificationResultCard', () => {
    it('renders valid capability verification result with checklist and reset button', async () => {
      const onReset = vi.fn();
      const mockResult = {
        kind: 'PUBLIC_CAPABILITY' as const,
        token: 'cap-token-1234567890123456',
        response: {
          verified: true,
          valid: true,
          status: 'VALID',
          mode: 'MODE_B_REGISTRY_CAPABILITY',
          protocolVersion: 'QR_V1',
          signatureValid: true,
          active: true,
          failureReason: null,
        },
        timestamp: '2026-10-15T09:00:00Z',
      };

      render(
        <VerificationResultCard
          result={mockResult}
          error={null}
          onReset={onReset}
        />,
      );

      expect(
        screen.getByText('Authoritative Verification Result'),
      ).toBeInTheDocument();
      expect(
        screen.getByText('Cryptographic Verification Passed'),
      ).toBeInTheDocument();
      expect(
        screen.getByText('Digital Signature (RSA-PSS)'),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/QR_V1 \(MODE_B_REGISTRY_CAPABILITY\)/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/Zero-Knowledge Privacy Enclave Protection/i),
      ).toBeInTheDocument();

      const resetBtn = screen.getByRole('button', {
        name: /Verify Another Reference/i,
      });
      await userEvent.click(resetBtn);
      expect(onReset).toHaveBeenCalled();
    });

    it('renders error notice for operational service unavailability without calling credential fake', () => {
      const onReset = vi.fn();
      const mockError = {
        type: 'UNAVAILABLE' as const,
        message: 'Network connection failed to verification authority',
        targetReference: 'tok-123',
      };

      render(
        <VerificationResultCard
          result={null}
          error={mockError}
          onReset={onReset}
        />,
      );

      expect(
        screen.getByText('Verification Service Unavailable'),
      ).toBeInTheDocument();
      expect(screen.getByText(/Important Trust Notice:/i)).toBeInTheDocument();
      expect(
        screen.getByText(
          /An operational failure, network timeout, or server unavailability does/i,
        ),
      ).toBeInTheDocument();
    });
  });

  describe('PublicVerificationPage Integration', () => {
    it('renders tabs and executes public capability verification when token is submitted', async () => {
      const verifySpy = vi
        .spyOn(verifierApi, 'publicVerifyCapability')
        .mockResolvedValueOnce({
          verified: true,
          valid: true,
          status: 'VALID',
          mode: 'MODE_B_REGISTRY_CAPABILITY',
          protocolVersion: 'QR_V1',
          signatureValid: true,
          active: true,
          failureReason: null,
        });

      render(
        <MemoryRouter initialEntries={['/verify']}>
          <PublicVerificationPage />
        </MemoryRouter>,
      );

      expect(
        screen.getByText('Public Cryptographic Verification'),
      ).toBeInTheDocument();

      // Switch to Manual Reference Tab
      const manualTab = screen.getByRole('button', {
        name: /Manual Reference Entry/i,
      });
      await userEvent.click(manualTab);

      const input = screen.getByLabelText(/Verification Reference or Payload/i);
      await userEvent.type(input, 'opaque-capability-token-1234567890');

      const submitBtn = screen.getByRole('button', {
        name: /Verify Reference/i,
      });
      await userEvent.click(submitBtn);

      await waitFor(() => {
        expect(
          screen.getByText('Cryptographic Verification Passed'),
        ).toBeInTheDocument();
      });
      expect(verifySpy).toHaveBeenCalledWith(
        'opaque-capability-token-1234567890',
      );
    });
  });
});
