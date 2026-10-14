import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { CredentialsPage } from '../src/features/credentials/pages/CredentialsPage';
import { AuthProvider } from '../src/features/auth/AuthContext';

function TestWrapper() {
  return (
    <MemoryRouter>
      <AuthProvider>
        <CredentialsPage />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe('CredentialsPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders unauthenticated state and allows connecting preset profile', async () => {
    const user = userEvent.setup();
    render(<TestWrapper />);

    expect(
      screen.getByRole('heading', { level: 1, name: 'Credentials' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Credentials workflow is not connected yet'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Connect as Issuer (acme-corp)' }));
    expect(screen.getByText(/ISSUER · acme-corp/)).toBeInTheDocument();
  });

  it('submits credential issuance form and displays returned credential ID and digest', async () => {
    const user = userEvent.setup();

    vi.mocked(fetch).mockImplementation(async (url: any, init: any) => {
      const urlStr = String(url);
      if (urlStr.includes('/api/credentials') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            credentialId: '11111111-2222-3333-4444-555555555555',
            digest: 'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
          }),
          { status: 201, headers: { 'Content-Type': 'application/json' } },
        );
      }
      return new Response(JSON.stringify({ status: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<TestWrapper />);
    await user.click(screen.getByRole('button', { name: 'Connect as Issuer (acme-corp)' }));

    const submitBtn = screen.getByRole('button', { name: 'Issue & Sign Credential' });
    await user.click(submitBtn);

    expect(await screen.findByText('Credential Issued Successfully')).toBeInTheDocument();
    expect(screen.getByText('11111111-2222-3333-4444-555555555555')).toBeInTheDocument();
  });

  it('queries lifecycle status and transitions for a credential', async () => {
    const user = userEvent.setup();

    vi.mocked(fetch).mockImplementation(async (url: any) => {
      const urlStr = String(url);
      if (urlStr.includes('/lifecycle/history')) {
        return new Response(
          JSON.stringify([
            {
              id: 'evt-1',
              credentialId: 'test-cred-id',
              fromStatus: 'NONE',
              toStatus: 'ACTIVE',
              actor: 'acme-corp',
              role: 'ISSUER',
              timestamp: '2026-10-14T08:00:00Z',
            },
          ]),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      if (urlStr.includes('/lifecycle')) {
        return new Response(
          JSON.stringify({
            credentialId: 'test-cred-id',
            status: 'ACTIVE',
            version: 1,
            issuerId: 'acme-corp',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      return new Response(JSON.stringify({ status: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<TestWrapper />);
    await user.click(screen.getByRole('button', { name: 'Connect as Issuer (acme-corp)' }));

    // Switch to Lifecycle tab
    await user.click(screen.getByRole('tab', { name: 'Lifecycle Management' }));

    const input = screen.getByPlaceholderText('Enter Credential UUID');
    await user.type(input, 'test-cred-id');
    await user.click(screen.getByRole('button', { name: 'Inspect Status' }));

    expect(await screen.findByText('Version 1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Suspend Credential' })).toBeInTheDocument();
    expect(screen.getByText('NONE')).toBeInTheDocument();
  });
});
