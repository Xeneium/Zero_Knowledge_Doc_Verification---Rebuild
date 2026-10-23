import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import {
  AppLayout,
  Header,
  MobileNavDrawer,
  Breadcrumbs,
  PageHeader,
  SkipLink,
  Footer,
} from '../src/components/layout';
import { authStore } from '../src/state/authStore';

describe('Phase 4: Application Shell & Navigation Suite', () => {
  beforeEach(() => {
    authStore.clearSession();
    document.body.style.overflow = '';
  });

  describe('SkipLink Component', () => {
    it('renders an accessible skip link targeting #main-content', () => {
      render(<SkipLink />);
      const link = screen.getByRole('link', { name: /skip to main content/i });
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute('href', '#main-content');
      expect(link.className).toContain('sr-only');
      expect(link.className).toContain('focus:not-sr-only');
    });
  });

  describe('Footer Component', () => {
    it('renders landmark contentinfo with platform security and compliance information', () => {
      render(<Footer />);
      const footer = screen.getByRole('contentinfo');
      expect(footer).toBeInTheDocument();
      expect(screen.getByText('ZKDoc Platform')).toBeInTheDocument();
      expect(screen.getByText(/zero-knowledge cryptographic core/i)).toBeInTheDocument();
      expect(screen.getByText(/sha-256 and rsa-pss signatures/i)).toBeInTheDocument();
      expect(screen.getByText(/active key registry v1/i)).toBeInTheDocument();
      expect(screen.getByText('WCAG 2.2 AA')).toBeInTheDocument();
    });
  });

  describe('Breadcrumbs Component', () => {
    it('returns null when items array is empty', () => {
      const { container } = render(
        <MemoryRouter>
          <Breadcrumbs items={[]} />
        </MemoryRouter>,
      );
      expect(container.firstChild).toBeNull();
    });

    it('renders breadcrumbs with home link and sets aria-current="location" on terminal leaf', () => {
      render(
        <MemoryRouter>
          <Breadcrumbs
            items={[
              { label: 'Workspaces', href: '/credentials' },
              { label: 'Credential Detail' },
            ]}
          />
        </MemoryRouter>,
      );

      const nav = screen.getByRole('navigation', { name: /breadcrumb/i });
      expect(nav).toBeInTheDocument();

      const homeLink = screen.getByRole('link', { name: /home/i });
      expect(homeLink).toBeInTheDocument();
      expect(homeLink).toHaveAttribute('href', '/');

      const workspacesLink = screen.getByRole('link', { name: 'Workspaces' });
      expect(workspacesLink).toBeInTheDocument();
      expect(workspacesLink).toHaveAttribute('href', '/credentials');

      const current = screen.getByText('Credential Detail');
      expect(current).toHaveAttribute('aria-current', 'location');
    });

    it('omits home item when showHome is false', () => {
      render(
        <MemoryRouter>
          <Breadcrumbs
            showHome={false}
            items={[{ label: 'Audit Logs' }]}
          />
        </MemoryRouter>,
      );

      expect(screen.queryByRole('link', { name: /home/i })).not.toBeInTheDocument();
      expect(screen.getByText('Audit Logs')).toBeInTheDocument();
    });
  });

  describe('PageHeader Component', () => {
    it('renders page header title and description', () => {
      render(
        <PageHeader
          title="Cryptographic Key Registry"
          description="Manage authoritative public verification keys."
        />,
      );

      expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
        'Cryptographic Key Registry',
      );
      expect(
        screen.getByText(/manage authoritative public verification keys/i),
      ).toBeInTheDocument();
    });

    it('renders optional badge, breadcrumbs, and action slots', () => {
      render(
        <MemoryRouter>
          <PageHeader
            title="Issuer Console"
            badge={<span data-testid="status-badge">ONLINE</span>}
            breadcrumbs={
              <Breadcrumbs items={[{ label: 'Console' }]} showHome={false} />
            }
            actions={<button type="button">Issue Credential</button>}
          />
        </MemoryRouter>,
      );

      expect(screen.getByTestId('status-badge')).toHaveTextContent('ONLINE');
      expect(screen.getByText('Console')).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /issue credential/i }),
      ).toBeInTheDocument();
    });
  });

  describe('Header Component', () => {
    it('renders brand identity link pointing to home', () => {
      render(
        <MemoryRouter>
          <Header />
        </MemoryRouter>,
      );

      const brandLink = screen.getByRole('link', {
        name: /zero-knowledge document verification home/i,
      });
      expect(brandLink).toBeInTheDocument();
      expect(brandLink).toHaveAttribute('href', '/');
    });

    it('renders public navigation links and Connect Session when unauthenticated', () => {
      render(
        <MemoryRouter>
          <Header />
        </MemoryRouter>,
      );

      const nav = screen.getByRole('navigation', { name: /main navigation/i });
      expect(within(nav).getByRole('link', { name: /^overview$/i })).toBeInTheDocument();
      expect(within(nav).getByRole('link', { name: /^verify$/i })).toBeInTheDocument();
      expect(within(nav).getByRole('link', { name: /^zk predicates$/i })).toBeInTheDocument();

      // Protected routes absent
      expect(within(nav).queryByRole('link', { name: /^credentials$/i })).not.toBeInTheDocument();
      expect(within(nav).queryByRole('link', { name: /^issuer$/i })).not.toBeInTheDocument();
      expect(within(nav).queryByRole('link', { name: /^audit logs$/i })).not.toBeInTheDocument();
      expect(within(nav).queryByRole('link', { name: /^admin$/i })).not.toBeInTheDocument();

      // Connect session CTA
      expect(screen.getByRole('link', { name: /connect session/i })).toHaveAttribute(
        'href',
        '/login',
      );
    });

    it('renders authenticated session status banner and user badge with Disconnect action', async () => {
      const user = userEvent.setup();
      const payload = btoa(
        JSON.stringify({
          sub: 'holder-agent',
          roles: ['HOLDER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);

      render(
        <MemoryRouter>
          <Header />
        </MemoryRouter>,
      );

      // Authenticated session banner
      const banner = screen.getByRole('status');
      expect(banner).toBeInTheDocument();
      expect(banner).toHaveTextContent(/authenticated session: holder-agent/i);

      // User badge and disconnect button
      expect(screen.getAllByText('holder-agent').length).toBeGreaterThanOrEqual(2);
      const disconnectBtn = screen.getByRole('button', {
        name: /disconnect active session/i,
      });
      expect(disconnectBtn).toBeInTheDocument();

      // Role-specific link visible
      const nav = screen.getByRole('navigation', { name: /main navigation/i });
      expect(within(nav).getByRole('link', { name: /^credentials$/i })).toBeInTheDocument();
      expect(within(nav).queryByRole('link', { name: /^issuer$/i })).not.toBeInTheDocument();
      expect(within(nav).queryByRole('link', { name: /^admin$/i })).not.toBeInTheDocument();

      // Disconnect clears session
      await user.click(disconnectBtn);
      expect(authStore.getState().isAuthenticated).toBe(false);
    });

    it('renders role-gated navigation for ISSUER role', () => {
      const payload = btoa(
        JSON.stringify({
          sub: 'issuer-authority',
          roles: ['ISSUER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);

      render(
        <MemoryRouter>
          <Header />
        </MemoryRouter>,
      );

      const nav = screen.getByRole('navigation', { name: /main navigation/i });
      expect(within(nav).getByRole('link', { name: /^issuer$/i })).toBeInTheDocument();
      expect(within(nav).getByRole('link', { name: /^audit logs$/i })).toBeInTheDocument();
      expect(within(nav).queryByRole('link', { name: /^credentials$/i })).not.toBeInTheDocument();
      expect(within(nav).queryByRole('link', { name: /^admin$/i })).not.toBeInTheDocument();
    });

    it('renders all workspace navigation links for ADMIN role', () => {
      const payload = btoa(
        JSON.stringify({
          sub: 'admin-super',
          roles: ['ADMIN'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);

      render(
        <MemoryRouter>
          <Header />
        </MemoryRouter>,
      );

      const nav = screen.getByRole('navigation', { name: /main navigation/i });
      expect(within(nav).getByRole('link', { name: /^credentials$/i })).toBeInTheDocument();
      expect(within(nav).getByRole('link', { name: /^issuer$/i })).toBeInTheDocument();
      expect(within(nav).getByRole('link', { name: /^audit logs$/i })).toBeInTheDocument();
      expect(within(nav).getByRole('link', { name: /^admin$/i })).toBeInTheDocument();
    });

    it('opens mobile drawer when hamburger button is clicked', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter>
          <Header />
        </MemoryRouter>,
      );

      const hamburgerBtn = screen.getByRole('button', {
        name: /open navigation menu/i,
      });
      expect(hamburgerBtn).toHaveAttribute('aria-expanded', 'false');
      expect(hamburgerBtn).toHaveAttribute('aria-controls', 'mobile-nav-drawer');

      await user.click(hamburgerBtn);

      expect(hamburgerBtn).toHaveAttribute('aria-expanded', 'true');
      expect(
        screen.getByRole('dialog', { name: /navigation menu/i }),
      ).toBeInTheDocument();
    });
  });

  describe('MobileNavDrawer Component', () => {
    it('does not render when isOpen is false', () => {
      const { container } = render(
        <MemoryRouter>
          <MobileNavDrawer
            isOpen={false}
            onClose={vi.fn()}
            auth={authStore.getState()}
          />
        </MemoryRouter>,
      );
      expect(container.firstChild).toBeNull();
    });

    it('renders accessible dialog with public links when open and unauthenticated', () => {
      render(
        <MemoryRouter>
          <MobileNavDrawer
            isOpen={true}
            onClose={vi.fn()}
            auth={authStore.getState()}
          />
        </MemoryRouter>,
      );

      const dialog = screen.getByRole('dialog', { name: /navigation menu/i });
      expect(dialog).toBeInTheDocument();
      expect(dialog).toHaveAttribute('aria-modal', 'true');
      expect(dialog).toHaveAttribute('id', 'mobile-nav-drawer');

      expect(screen.getByText('Anonymous Verifier')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /platform overview/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /verify credential/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /zk predicates/i })).toBeInTheDocument();

      // Protected workspace section not displayed
      expect(screen.queryByText('Authenticated Workspaces')).not.toBeInTheDocument();
    });

    it('renders authenticated workspaces and user info for logged in user', () => {
      const payload = btoa(
        JSON.stringify({
          sub: 'holder-agent',
          roles: ['HOLDER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);
      const state = authStore.getState();

      render(
        <MemoryRouter>
          <MobileNavDrawer isOpen={true} onClose={vi.fn()} auth={state} />
        </MemoryRouter>,
      );

      expect(screen.getByText('holder-agent')).toBeInTheDocument();
      expect(screen.getByText(/roles: holder/i)).toBeInTheDocument();
      expect(screen.getByText('Authenticated Workspaces')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /holder credentials/i })).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /disconnect session/i }),
      ).toBeInTheDocument();
    });

    it('calls onClose when close button is clicked', async () => {
      const user = userEvent.setup();
      const handleClose = vi.fn();

      render(
        <MemoryRouter>
          <MobileNavDrawer
            isOpen={true}
            onClose={handleClose}
            auth={authStore.getState()}
          />
        </MemoryRouter>,
      );

      const closeBtn = screen.getByRole('button', {
        name: /close navigation menu/i,
      });
      await user.click(closeBtn);
      expect(handleClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when Escape key is pressed', async () => {
      const user = userEvent.setup();
      const handleClose = vi.fn();

      render(
        <MemoryRouter>
          <MobileNavDrawer
            isOpen={true}
            onClose={handleClose}
            auth={authStore.getState()}
          />
        </MemoryRouter>,
      );

      await user.keyboard('{Escape}');
      expect(handleClose).toHaveBeenCalledTimes(1);
    });

    it('locks body scroll when opened and unlocks upon unmount', () => {
      const { unmount } = render(
        <MemoryRouter>
          <MobileNavDrawer
            isOpen={true}
            onClose={vi.fn()}
            auth={authStore.getState()}
          />
        </MemoryRouter>,
      );

      expect(document.body.style.overflow).toBe('hidden');
      unmount();
      expect(document.body.style.overflow).toBe('');
    });
  });

  describe('AppLayout Component', () => {
    it('mounts full shell hierarchy with landmarks and outlet', () => {
      render(
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route element={<AppLayout />}>
              <Route index element={<div data-testid="test-content">Welcome Content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>,
      );

      // SkipLink
      expect(
        screen.getByRole('link', { name: /skip to main content/i }),
      ).toBeInTheDocument();

      // Header landmark
      expect(screen.getByRole('banner')).toBeInTheDocument();

      // Main content landmark
      const main = screen.getByRole('main');
      expect(main).toBeInTheDocument();
      expect(main).toHaveAttribute('id', 'main-content');
      expect(main).toHaveAttribute('tabIndex', '-1');
      expect(screen.getByTestId('test-content')).toHaveTextContent('Welcome Content');

      // Footer landmark
      expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    });
  });
});
