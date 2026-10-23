import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { LoginPage } from '../src/routes/LoginPage';
import { ForbiddenPage } from '../src/routes/ForbiddenPage';
import { RequireAuth, RequireRole } from '../src/components/auth';
import { authStore } from '../src/state/authStore';

describe('Authentication Components & Route Protection', () => {
  beforeEach(() => {
    authStore.clearSession();
    if (typeof window !== 'undefined' && window.sessionStorage) {
      window.sessionStorage.clear();
    }
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('LoginPage Component', () => {
    it('renders login page with zero-trust architecture notice and form controls', () => {
      render(
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.getByRole('heading', { name: /session authentication/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/zero-trust oauth2 resource server/i),
      ).toBeInTheDocument();
      expect(screen.getByLabelText(/bearer jwt token/i)).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /connect session/i }),
      ).toBeInTheDocument();
    });

    it('toggles password masking on token input', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
          </Routes>
        </MemoryRouter>,
      );

      const toggleBtn = screen.getByRole('button', {
        name: /show raw token value/i,
      });
      expect(toggleBtn).toBeInTheDocument();

      await user.click(toggleBtn);
      expect(
        screen.getByRole('button', { name: /mask token value/i }),
      ).toBeInTheDocument();
    });

    it('displays validation error for malformed token structure', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
          </Routes>
        </MemoryRouter>,
      );

      const textarea = screen.getByLabelText(/bearer jwt token/i);
      await user.type(textarea, 'not-a-valid-jwt');

      expect(
        screen.getByText(/token must contain three dot-separated segments/i),
      ).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /connect session/i }),
      ).toBeDisabled();
    });

    it('displays live decoded token claims preview when valid token is typed', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
          </Routes>
        </MemoryRouter>,
      );

      const header = btoa(JSON.stringify({ alg: 'RS256', kid: 'sec-key-1' }));
      const payload = btoa(
        JSON.stringify({
          sub: 'holder-agent-preview',
          roles: ['ROLE_HOLDER'],
          iss: 'https://auth.zkdoc.internal',
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      const validToken = `${header}.${payload}.sig`;

      const textarea = screen.getByLabelText(/bearer jwt token/i);
      await user.type(textarea, validToken);

      expect(screen.getByText(/decoded token claims/i)).toBeInTheDocument();
      expect(screen.getByText(/format valid/i)).toBeInTheDocument();
      expect(screen.getByText('holder-agent-preview')).toBeInTheDocument();
      expect(screen.getByText('HOLDER')).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /connect session/i }),
      ).not.toBeDisabled();
    });

    it('submits valid token, establishes session, and redirects to safe returnUrl', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter initialEntries={['/login?returnUrl=%2Fcredentials']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/credentials"
              element={<div>Protected Credentials Dashboard</div>}
            />
          </Routes>
        </MemoryRouter>,
      );

      const header = btoa(JSON.stringify({ alg: 'RS256' }));
      const payload = btoa(
        JSON.stringify({
          sub: 'authorized-holder',
          roles: ['HOLDER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      const validToken = `${header}.${payload}.sig`;

      const textarea = screen.getByLabelText(/bearer jwt token/i);
      await user.type(textarea, validToken);

      const connectBtn = screen.getByRole('button', {
        name: /connect session/i,
      });
      await user.click(connectBtn);

      await waitFor(() => {
        expect(
          screen.getByText(/protected credentials dashboard/i),
        ).toBeInTheDocument();
      });

      expect(authStore.getState().isAuthenticated).toBe(true);
      expect(authStore.getState().user?.subject).toBe('authorized-holder');
    });

    it('defends against open redirect when malicious returnUrl is supplied', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter
          initialEntries={['/login?returnUrl=https%3A%2F%2Fevil.attacker.com']}
        >
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<div>Safe Root Overview</div>} />
          </Routes>
        </MemoryRouter>,
      );

      const header = btoa(JSON.stringify({ alg: 'RS256' }));
      const payload = btoa(
        JSON.stringify({
          sub: 'agent-1',
          roles: ['HOLDER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      const validToken = `${header}.${payload}.sig`;

      const textarea = screen.getByLabelText(/bearer jwt token/i);
      await user.type(textarea, validToken);

      const connectBtn = screen.getByRole('button', {
        name: /connect session/i,
      });
      await user.click(connectBtn);

      // Must navigate to '/' safe fallback, not external domain
      await waitFor(() => {
        expect(screen.getByText(/safe root overview/i)).toBeInTheDocument();
      });
    });

    it('renders active session information when already authenticated', () => {
      const payload = btoa(
        JSON.stringify({
          sub: 'active-session-user',
          roles: ['ROLE_ISSUER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);

      render(
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.getByRole('heading', { name: /active session connected/i }),
      ).toBeInTheDocument();
      expect(screen.getByText('active-session-user')).toBeInTheDocument();
      expect(screen.getByText('ISSUER')).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /disconnect session/i }),
      ).toBeInTheDocument();
    });

    it('displays warning alert when entering login page with expired session state', () => {
      const expiredState = {
        status: 'expired' as const,
        token: null,
        user: null,
        isAuthenticated: false,
        error: 'Session has expired. Please connect a valid JWT to continue.',
      };
      vi.spyOn(authStore, 'getState').mockReturnValue(expiredState);

      render(
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText('Session Expired')).toBeInTheDocument();
      expect(
        screen.getByText(/session has expired\. please connect a valid jwt/i),
      ).toBeInTheDocument();
    });

    it('does not render unsupported registration or password reset links', () => {
      render(
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.queryByRole('link', { name: /register|sign up|create account/i }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('link', { name: /forgot password|reset password/i }),
      ).not.toBeInTheDocument();
    });
  });

  describe('Route Guards: RequireAuth & RequireRole', () => {
    it('RequireAuth blocks unauthenticated users and redirects to /login with returnUrl', () => {
      render(
        <MemoryRouter initialEntries={['/protected-workspace']}>
          <Routes>
            <Route element={<RequireAuth />}>
              <Route
                path="/protected-workspace"
                element={<div>Secret Protected Content</div>}
              />
            </Route>
            <Route path="/login" element={<div>Login Page Landing</div>} />
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.queryByText(/secret protected content/i),
      ).not.toBeInTheDocument();
      expect(screen.getByText(/login page landing/i)).toBeInTheDocument();
    });

    it('RequireAuth allows access when authenticated', () => {
      const payload = btoa(
        JSON.stringify({
          sub: 'logged-in-user',
          roles: ['HOLDER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);

      render(
        <MemoryRouter initialEntries={['/protected-workspace']}>
          <Routes>
            <Route element={<RequireAuth />}>
              <Route
                path="/protected-workspace"
                element={<div>Secret Protected Content</div>}
              />
            </Route>
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText(/secret protected content/i)).toBeInTheDocument();
    });

    it('RequireRole allows access when user has matching role', () => {
      const payload = btoa(
        JSON.stringify({
          sub: 'issuer-user',
          roles: ['ISSUER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);

      render(
        <MemoryRouter initialEntries={['/issuer-only']}>
          <Routes>
            <Route element={<RequireRole allowedRoles={['ISSUER']} />}>
              <Route
                path="/issuer-only"
                element={<div>Issuer Workspace Only</div>}
              />
            </Route>
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText(/issuer workspace only/i)).toBeInTheDocument();
    });

    it('RequireRole allows access when user has ADMIN superuser role', () => {
      const payload = btoa(
        JSON.stringify({
          sub: 'admin-superuser',
          roles: ['ADMIN'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);

      render(
        <MemoryRouter initialEntries={['/holder-only']}>
          <Routes>
            <Route element={<RequireRole allowedRoles={['HOLDER']} />}>
              <Route
                path="/holder-only"
                element={<div>Holder Workspace Only</div>}
              />
            </Route>
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText(/holder workspace only/i)).toBeInTheDocument();
    });

    it('RequireRole redirects to /forbidden when authenticated user lacks required role', () => {
      const payload = btoa(
        JSON.stringify({
          sub: 'plain-holder',
          roles: ['HOLDER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);

      render(
        <MemoryRouter initialEntries={['/admin-console']}>
          <Routes>
            <Route element={<RequireRole allowedRoles={['ADMIN']} />}>
              <Route
                path="/admin-console"
                element={<div>Admin Console Page</div>}
              />
            </Route>
            <Route
              path="/forbidden"
              element={<div>403 Access Denied Page</div>}
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.queryByText(/admin console page/i)).not.toBeInTheDocument();
      expect(screen.getByText(/403 access denied page/i)).toBeInTheDocument();
    });
  });

  describe('ForbiddenPage Component', () => {
    it('renders 403 status, explanation, and connect action when unauthenticated', () => {
      render(
        <MemoryRouter initialEntries={['/forbidden']}>
          <ForbiddenPage />
        </MemoryRouter>,
      );

      expect(
        screen.getByRole('heading', { name: /access restricted/i }),
      ).toBeInTheDocument();
      expect(screen.getByText(/403 forbidden/i)).toBeInTheDocument();
      expect(
        screen.getByRole('link', { name: /connect different session/i }),
      ).toHaveAttribute('href', '/login');
      expect(
        screen.getByRole('link', { name: /return to overview/i }),
      ).toHaveAttribute('href', '/');
    });

    it('renders connected user identity, assigned roles, and disconnect action when authenticated', async () => {
      const user = userEvent.setup();
      const payload = btoa(
        JSON.stringify({
          sub: 'restricted-holder',
          roles: ['HOLDER'],
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      );
      authStore.setToken(`header.${payload}.sig`);

      render(
        <MemoryRouter initialEntries={['/forbidden']}>
          <ForbiddenPage />
        </MemoryRouter>,
      );

      expect(screen.getByText('restricted-holder')).toBeInTheDocument();
      expect(screen.getByText('HOLDER')).toBeInTheDocument();

      const disconnectBtn = screen.getByRole('button', {
        name: /disconnect session/i,
      });
      expect(disconnectBtn).toBeInTheDocument();

      await user.click(disconnectBtn);
      expect(authStore.getState().isAuthenticated).toBe(false);
    });
  });
});
