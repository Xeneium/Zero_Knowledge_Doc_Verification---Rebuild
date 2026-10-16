import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  authStore,
  parseAndValidateJwt,
  normalizeRole,
} from '../src/state/authStore';

describe('authStore & JWT Security Baseline', () => {
  beforeEach(() => {
    vi.useRealTimers();
    if (typeof window !== 'undefined' && window.sessionStorage) {
      window.sessionStorage.clear();
    }
    authStore.clearSession();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('starts in unauthenticated state after clearSession', () => {
    const state = authStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.token).toBeNull();
    expect(state.user).toBeNull();
    expect(state.status).toBe('unauthenticated');
  });

  it('normalizes role names correctly', () => {
    expect(normalizeRole('ROLE_HOLDER')).toBe('HOLDER');
    expect(normalizeRole('holder')).toBe('HOLDER');
    expect(normalizeRole(' ROLE_ISSUER ')).toBe('ISSUER');
    expect(normalizeRole('ADMIN')).toBe('ADMIN');
  });

  it('parses valid RSA JWT with subject, roles, expiration, and audience', () => {
    const header = btoa(JSON.stringify({ alg: 'RS256', kid: 'key-1' }));
    const payload = btoa(
      JSON.stringify({
        sub: 'holder-agent-01',
        roles: ['ROLE_HOLDER'],
        permissions: ['credential:verify'],
        iss: 'https://auth.zkdoc.internal',
        aud: 'zkdoc-api',
        exp: Math.floor(Date.now() / 1000) + 3600,
        iat: Math.floor(Date.now() / 1000),
      }),
    );
    const validToken = `${header}.${payload}.mockSignatureBytes`;

    const { user, error } = parseAndValidateJwt(validToken);
    expect(error).toBeNull();
    expect(user).not.toBeNull();
    expect(user?.subject).toBe('holder-agent-01');
    expect(user?.roles).toEqual(['HOLDER']);
    expect(user?.permissions).toEqual(['credential:verify']);
    expect(user?.issuer).toBe('https://auth.zkdoc.internal');
    expect(user?.audience).toBe('zkdoc-api');
    expect(user?.keyId).toBe('key-1');
  });

  it('authenticates and notifies subscribers when a valid token is set', () => {
    const header = btoa(JSON.stringify({ alg: 'RS256' }));
    const payload = btoa(
      JSON.stringify({
        sub: 'issuer-agent',
        roles: ['ISSUER'],
        exp: Math.floor(Date.now() / 1000) + 1800,
      }),
    );
    const token = `${header}.${payload}.sig`;

    let notifiedState: unknown = null;
    const unsubscribe = authStore.subscribe((state) => {
      notifiedState = state;
    });

    const success = authStore.setToken(token);
    expect(success).toBe(true);

    const state = authStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.status).toBe('authenticated');
    expect(state.user?.subject).toBe('issuer-agent');
    expect(state.user?.roles).toContain('ISSUER');
    expect(notifiedState).toEqual(state);

    unsubscribe();
  });

  it('evaluates hasRole and hasAnyRole with role hierarchy (ADMIN superuser)', () => {
    // 1. Regular HOLDER user
    const holderPayload = btoa(
      JSON.stringify({
        sub: 'holder-1',
        roles: ['ROLE_HOLDER'],
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    authStore.setToken(`header.${holderPayload}.sig`);

    expect(authStore.hasRole('HOLDER')).toBe(true);
    expect(authStore.hasRole('ROLE_HOLDER')).toBe(true);
    expect(authStore.hasRole('ISSUER')).toBe(false);
    expect(authStore.hasRole('ADMIN')).toBe(false);
    expect(authStore.hasAnyRole(['ISSUER', 'HOLDER'])).toBe(true);
    expect(authStore.hasAnyRole(['ISSUER', 'ADMIN'])).toBe(false);

    // 2. ADMIN user inherits all role checks
    const adminPayload = btoa(
      JSON.stringify({
        sub: 'admin-1',
        roles: ['ROLE_ADMIN'],
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    authStore.setToken(`header.${adminPayload}.sig`);

    expect(authStore.hasRole('ADMIN')).toBe(true);
    expect(authStore.hasRole('HOLDER')).toBe(true); // Superuser access
    expect(authStore.hasRole('ISSUER')).toBe(true); // Superuser access
    expect(authStore.hasAnyRole(['HOLDER'])).toBe(true);
  });

  it('rejects tokens with alg=none per RFC 8725', () => {
    const header = btoa(JSON.stringify({ alg: 'none' }));
    const payload = btoa(
      JSON.stringify({
        sub: 'attacker',
        roles: ['ADMIN'],
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    const token = `${header}.${payload}.sig`;

    const { user, error } = parseAndValidateJwt(token);
    expect(user).toBeNull();
    expect(error).toMatch(/unsecured jwt algorithm \("none"\)/i);

    const success = authStore.setToken(token);
    expect(success).toBe(false);
    expect(authStore.getState().isAuthenticated).toBe(false);
  });

  it('rejects symmetric HMAC algorithms (HS256) to prevent key confusion attacks', () => {
    const header = btoa(JSON.stringify({ alg: 'HS256' }));
    const payload = btoa(
      JSON.stringify({
        sub: 'attacker',
        roles: ['ADMIN'],
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    const token = `${header}.${payload}.sig`;

    const { user, error } = parseAndValidateJwt(token);
    expect(user).toBeNull();
    expect(error).toMatch(/symmetric algorithm \("hs\*"\) rejected/i);

    const success = authStore.setToken(token);
    expect(success).toBe(false);
    expect(authStore.getState().isAuthenticated).toBe(false);
  });

  it('rejects expired tokens immediately', () => {
    const header = btoa(JSON.stringify({ alg: 'RS256' }));
    const payload = btoa(
      JSON.stringify({
        sub: 'user',
        roles: ['HOLDER'],
        exp: Math.floor(Date.now() / 1000) - 300, // Expired 5 mins ago
      }),
    );
    const token = `${header}.${payload}.sig`;

    const { user, error } = parseAndValidateJwt(token);
    expect(user).toBeNull();
    expect(error).toMatch(/token has expired/i);

    const success = authStore.setToken(token);
    expect(success).toBe(false);
    expect(authStore.getState().isAuthenticated).toBe(false);
  });

  it('rejects tokens with missing subject claim', () => {
    const header = btoa(JSON.stringify({ alg: 'RS256' }));
    const payload = btoa(
      JSON.stringify({
        roles: ['HOLDER'],
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    const token = `${header}.${payload}.sig`;

    const { user, error } = parseAndValidateJwt(token);
    expect(user).toBeNull();
    expect(error).toMatch(/missing required subject/i);
  });

  it('triggers automatic session expiration when exp timer fires', () => {
    vi.useFakeTimers();

    const nowSeconds = Math.floor(Date.now() / 1000);
    const header = btoa(JSON.stringify({ alg: 'RS256' }));
    const payload = btoa(
      JSON.stringify({
        sub: 'expiring-user',
        roles: ['HOLDER'],
        exp: nowSeconds + 60, // Expires in 60 seconds
      }),
    );
    const token = `${header}.${payload}.sig`;

    authStore.setToken(token);
    expect(authStore.getState().isAuthenticated).toBe(true);

    // Fast-forward 61 seconds
    vi.advanceTimersByTime(61 * 1000);

    const expiredState = authStore.getState();
    expect(expiredState.isAuthenticated).toBe(false);
    expect(expiredState.status).toBe('expired');
    expect(expiredState.token).toBeNull();
    expect(expiredState.user).toBeNull();
    expect(expiredState.error).toMatch(/session has expired/i);
  });

  it('clears session and removes token on explicit clearSession call', () => {
    const payload = btoa(
      JSON.stringify({
        sub: 'test-user',
        roles: ['HOLDER'],
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    authStore.setToken(`header.${payload}.sig`);
    expect(authStore.getState().isAuthenticated).toBe(true);

    authStore.clearSession('User logged out');
    const state = authStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.token).toBeNull();
    expect(state.user).toBeNull();
    expect(state.error).toBe('User logged out');
  });
});
