import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { DocumentVerificationPage } from '../src/features/verification/pages/DocumentVerificationPage';
import { PredicateVerificationPage } from '../src/features/verification/pages/PredicateVerificationPage';
import { AuthProvider } from '../src/features/auth/AuthContext';

describe('DocumentVerificationPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });
  it('verifies stored document copy and displays verification breakdown', async () => {
    const user = userEvent.setup();

    vi.mocked(fetch).mockImplementation(async (url: any) => {
      const urlStr = String(url);
      if (urlStr.includes('/api/documents/doc-123/verify')) {
        return new Response(
          JSON.stringify({
            verified: true,
            signatureValid: true,
            documentActive: true,
            storedDigestHex: 'abcdef1234567890',
            computedDigestHex: 'abcdef1234567890',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      return new Response(JSON.stringify({ status: 'ok' }), { status: 200 });
    });

    render(
      <MemoryRouter>
        <AuthProvider>
          <DocumentVerificationPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('heading', { level: 1, name: 'Verification' }),
    ).toBeInTheDocument();

    const input = screen.getByLabelText('Document UUID');
    await user.type(input, 'doc-123');
    await user.click(screen.getByRole('button', { name: 'Verify Stored Copy' }));

    expect(
      await screen.findByText('Document Authenticity Authoritatively Verified'),
    ).toBeInTheDocument();
    expect(screen.getByText('Valid Signature')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
  });
});

describe('PredicateVerificationPage', () => {
  it('parses pasted JSON and verifies zero-knowledge predicate proof', async () => {
    const user = userEvent.setup();

    vi.mocked(fetch).mockImplementation(async (url: any, init: any) => {
      const urlStr = String(url);
      if (urlStr.includes('/api/v2/zkp/credential-predicates/verify') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            verified: true,
            credentialId: 'cred-uuid-1',
            attributeName: 'gpa',
            threshold: 300,
            signatureValid: true,
            commitmentValid: true,
            proofValid: true,
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      return new Response(JSON.stringify({ status: 'ok' }), { status: 200 });
    });

    render(
      <MemoryRouter>
        <AuthProvider>
          <PredicateVerificationPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('heading', { level: 1, name: 'Predicate verification' }),
    ).toBeInTheDocument();

    const proofJson = JSON.stringify({
      proofVersion: 2,
      credentialId: 'cred-uuid-1',
      attributeName: 'gpa',
      threshold: 300,
      nonce: 'nonce-123',
      proofBytes: 'base64proofbytes==',
    });

    const textarea = screen.getByPlaceholderText(/Paste JSON containing/i);
    fireEvent.change(textarea, { target: { value: proofJson } });
    await user.click(screen.getByRole('button', { name: 'Load Fields' }));

    const verifyBtn = screen.getByRole('button', {
      name: 'Verify Zero-Knowledge Predicate Proof',
    });
    await user.click(verifyBtn);

    expect(
      await screen.findByText('Predicate Mathematically Proven and Satisfied!'),
    ).toBeInTheDocument();
    expect(screen.getByText('Mathematically Valid')).toBeInTheDocument();
    expect(screen.getByText('Bound to Credential')).toBeInTheDocument();
  });
});
