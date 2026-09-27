import { render, screen, within } from '@testing-library/react';
import { App } from '../src/app/App';
import { AppProviders } from '../src/app/providers';
import { createTestRouter } from '../src/app/router';

function renderPage() {
  render(
    <AppProviders>
      <App router={createTestRouter(['/administration'])} />
    </AppProviders>,
  );
}

describe('PlatformOperationsPage', () => {
  it('renders the audited security model without requesting protected data', async () => {
    renderPage();

    expect(
      await screen.findByRole('heading', {
        level: 1,
        name: 'Platform operations',
      }),
    ).toBeInTheDocument();
    expect(screen.getByText('Stateless authentication')).toBeInTheDocument();
    expect(screen.getByText('Backend-enforced roles')).toBeInTheDocument();
    expect(screen.getByText('Metadata-only auditing')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('documents unavailable capabilities and exposes no privileged controls', async () => {
    renderPage();

    await screen.findByRole('heading', {
      level: 1,
      name: 'Platform operations',
    });

    const table = screen.getByRole('table', {
      name: 'Phase 5 account and administration capability decisions',
    });
    expect(within(table).getByText('Users and roles')).toBeInTheDocument();
    expect(within(table).getAllByText('Unavailable')).toHaveLength(3);
    expect(screen.getByText('POST /api/admin/keys')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /register|revoke|deactivate/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument();
  });

  it('offers only the supported public workflow', async () => {
    renderPage();

    await screen.findByRole('heading', {
      level: 1,
      name: 'Platform operations',
    });

    expect(
      screen.getByRole('link', { name: 'Open public verification' }),
    ).toHaveAttribute('href', '/verify');
  });
});
