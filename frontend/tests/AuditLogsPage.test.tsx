import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { AuditLogsPage } from '../src/features/audit/pages/AuditLogsPage';
import { AuthProvider } from '../src/features/auth/AuthContext';

describe('AuditLogsPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders audit events table and verifies cryptographic hash chain integrity', async () => {
    const user = userEvent.setup();

    vi.mocked(fetch).mockImplementation(async (url: any) => {
      const urlStr = String(url);
      if (urlStr.includes('/api/admin/audit/verify-integrity')) {
        return new Response(
          JSON.stringify({
            valid: true,
            totalEventsChecked: 42,
            startSequence: 1,
            endSequence: 42,
            message: 'Audit hash chain unbroken.',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      if (urlStr.includes('/api/admin/audit/events')) {
        return new Response(
          JSON.stringify({
            items: [
              {
                id: 'evt-001',
                sequence: 1,
                timestamp: '2026-10-14T08:00:00Z',
                eventType: 'CREDENTIAL_ISSUED',
                category: 'CREDENTIAL_LIFECYCLE',
                actor: 'acme-corp',
                actorType: 'USER',
                targetType: 'CREDENTIAL',
                targetId: 'cred-123',
                action: 'ISSUE',
                outcome: 'SUCCESS',
              },
            ],
            total: 1,
            page: 0,
            size: 15,
            totalPages: 1,
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      return new Response(JSON.stringify({ status: 'ok' }), { status: 200 });
    });

    render(
      <MemoryRouter>
        <AuthProvider>
          <AuditLogsPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('heading', { level: 1, name: 'Audit logs' }),
    ).toBeInTheDocument();

    // Connect admin preset
    await user.click(screen.getByRole('button', { name: 'Connect as Administrator (admin-root)' }));

    // Audit event row should load
    expect(await screen.findByText('CREDENTIAL_ISSUED')).toBeInTheDocument();
    expect(screen.getByText('#1')).toBeInTheDocument();
    expect(screen.getByText('SUCCESS')).toBeInTheDocument();

    // Verify hash chain
    const verifyChainBtn = screen.getByRole('button', { name: 'Verify Chain Integrity' });
    await user.click(verifyChainBtn);

    expect(
      await screen.findByText('Cryptographic Hash Chain Integrity Confirmed: Unbroken'),
    ).toBeInTheDocument();
    expect(screen.getByText(/42 events/)).toBeInTheDocument();
  });
});
