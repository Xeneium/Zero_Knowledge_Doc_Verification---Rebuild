import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../src/app/App';
import { authStore } from '../src/state/authStore';

describe('App Root and Foundation Routing', () => {
  beforeEach(() => {
    authStore.clearSession();
  });

  it('renders application overview on root path', () => {
    window.history.pushState({}, '', '/');
    render(<App />);

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: /zero-knowledge document verification/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/mathematically verify document integrity/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: /skip to main content/i }),
    ).toBeInTheDocument();
  });

  it('renders public navigation links in header when unauthenticated', () => {
    window.history.pushState({}, '', '/');
    render(<App />);

    const nav = screen.getByRole('navigation', { name: /main navigation/i });
    expect(
      within(nav).getByRole('link', { name: /^overview$/i }),
    ).toBeInTheDocument();
    expect(
      within(nav).getByRole('link', { name: /^verify$/i }),
    ).toBeInTheDocument();
    expect(
      within(nav).getByRole('link', { name: /^zk predicates$/i }),
    ).toBeInTheDocument();

    // Role-gated links must not be present when unauthenticated
    expect(
      within(nav).queryByRole('link', { name: /^credentials$/i }),
    ).not.toBeInTheDocument();
    expect(
      within(nav).queryByRole('link', { name: /^issuer$/i }),
    ).not.toBeInTheDocument();
    expect(
      within(nav).queryByRole('link', { name: /^audit logs$/i }),
    ).not.toBeInTheDocument();
    expect(
      within(nav).queryByRole('link', { name: /^admin$/i }),
    ).not.toBeInTheDocument();

    // Header displays "Connect Session" action
    expect(
      screen.getByRole('link', { name: /connect session/i }),
    ).toBeInTheDocument();
  });

  it('renders role-gated navigation links when authenticated as ADMIN', () => {
    const payload = btoa(
      JSON.stringify({
        sub: 'admin-user',
        roles: ['ADMIN'],
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    authStore.setToken(`eyJhbGciOiJSUzI1NiJ9.${payload}.sig`);

    window.history.pushState({}, '', '/');
    render(<App />);

    const nav = screen.getByRole('navigation', { name: /main navigation/i });
    expect(
      within(nav).getByRole('link', { name: /^credentials$/i }),
    ).toBeInTheDocument();
    expect(
      within(nav).getByRole('link', { name: /^issuer$/i }),
    ).toBeInTheDocument();
    expect(
      within(nav).getByRole('link', { name: /^audit logs$/i }),
    ).toBeInTheDocument();
    expect(
      within(nav).getByRole('link', { name: /^admin$/i }),
    ).toBeInTheDocument();
  });

  it('navigates to /verify route when clicking Verify link', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/');
    render(<App />);

    const nav = screen.getByRole('navigation', { name: /main navigation/i });
    const verifyLink = within(nav).getByRole('link', { name: /^verify$/i });
    await user.click(verifyLink);

    expect(
      screen.getByRole('heading', {
        name: /public cryptographic verification/i,
      }),
    ).toBeInTheDocument();
  });

  it('navigates to /predicate-verification route', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/');
    render(<App />);

    const nav = screen.getByRole('navigation', { name: /main navigation/i });
    const zkpLink = within(nav).getByRole('link', { name: /^zk predicates$/i });
    await user.click(zkpLink);

    expect(
      screen.getByRole('heading', {
        name: /public cryptographic verification/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /zkp predicate proof/i,
      }),
    ).toBeInTheDocument();
  });

  it('redirects unauthenticated access from /credentials to /login with returnUrl', () => {
    window.history.pushState({}, '', '/credentials');
    render(<App />);

    expect(
      screen.getByRole('heading', { name: /session authentication/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/authentication required to access/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/\/credentials/i)).toBeInTheDocument();
  });

  it('renders 403 Forbidden on /forbidden route', () => {
    window.history.pushState({}, '', '/forbidden');
    render(<App />);

    expect(
      screen.getByRole('heading', { name: /access restricted/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/403 forbidden/i)).toBeInTheDocument();
  });

  it('renders 404 Not Found on an unknown route', () => {
    window.history.pushState({}, '', '/non-existent-page-route');
    render(<App />);

    expect(
      screen.getByRole('heading', { name: /page not found/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: /return to overview/i }),
    ).toBeInTheDocument();
  });
});
