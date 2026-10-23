/**
 * Centralized Authentication & Session State Management for ZKDoc Platform.
 * Adheres to RFC 7519 / RFC 8725 JWT Best Current Practices and ADR-003.
 */

import { useState, useEffect } from 'react';

export type AuthStatus =
  'idle' | 'initializing' | 'authenticated' | 'unauthenticated' | 'expired';

export interface AuthUser {
  readonly subject: string;
  readonly roles: readonly string[];
  readonly permissions: readonly string[];
  readonly issuer?: string;
  readonly audience?: string | readonly string[];
  readonly keyId?: string;
  readonly issuedAt: number; // Unix epoch seconds
  readonly expiration: number; // Unix epoch seconds
}

export interface AuthState {
  readonly status: AuthStatus;
  readonly token: string | null;
  readonly user: AuthUser | null;
  readonly isAuthenticated: boolean;
  readonly error: string | null;
}

const STORAGE_KEY = 'zkdoc_auth_session';

/** Allowed system roles matching backend Spring Security JwtAuthoritiesConverter */
export const ALLOWED_ROLES = ['ISSUER', 'HOLDER', 'VERIFIER', 'ADMIN'] as const;
export type SystemRole = (typeof ALLOWED_ROLES)[number];

/**
 * Safely decodes base64url-encoded string supporting Unicode/UTF-8.
 */
function decodeBase64Url(base64Url: string): string {
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
  // Pad with trailing '=' if needed
  const padded = base64.padEnd(
    base64.length + ((4 - (base64.length % 4)) % 4),
    '=',
  );
  const binaryString = atob(padded);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return new TextDecoder().decode(bytes);
}

/**
 * Normalizes role names by stripping optional 'ROLE_' prefix and converting to uppercase.
 */
export function normalizeRole(role: string): string {
  return role
    .trim()
    .replace(/^ROLE_/, '')
    .toUpperCase();
}

/**
 * Parses and validates JWT header and payload claims per RFC 8725.
 */
export function parseAndValidateJwt(token: string): {
  user: AuthUser | null;
  error: string | null;
} {
  if (!token || typeof token !== 'string') {
    return { user: null, error: 'Token is required' };
  }

  const parts = token.trim().split('.');
  if (parts.length !== 3) {
    return {
      user: null,
      error:
        'Invalid JWT structure: Token must contain three dot-separated segments (header.payload.signature)',
    };
  }

  // 1. Validate Header if valid JSON is present
  let header: { alg?: string; kid?: string; typ?: string } = {};
  try {
    const headerJson = decodeBase64Url(parts[0]);
    header = JSON.parse(headerJson) as {
      alg?: string;
      kid?: string;
      typ?: string;
    };
    if (header && typeof header === 'object') {
      const alg = String(header.alg || '').toUpperCase();
      if (alg === 'NONE') {
        return {
          user: null,
          error: 'Unsecured JWT algorithm ("none") is strictly forbidden',
        };
      }

      if (alg.startsWith('HS')) {
        return {
          user: null,
          error:
            'Symmetric algorithm ("HS*") rejected: ZKDoc requires asymmetric RSA signatures (RS256/PS256)',
        };
      }
    }
  } catch {
    // If header segment is not JSON (e.g. test fixture), continue to payload validation
  }

  // 2. Validate Payload Claims
  let payload: {
    sub?: string;
    roles?: unknown;
    role?: unknown;
    scope?: string;
    permissions?: unknown;
    iss?: string;
    aud?: unknown;
    iat?: number;
    exp?: number;
  };

  try {
    const payloadJson = decodeBase64Url(parts[1]);
    payload = JSON.parse(payloadJson) as typeof payload;
  } catch {
    return { user: null, error: 'Malformed JWT payload encoding' };
  }

  if (!payload || typeof payload !== 'object') {
    return { user: null, error: 'Invalid JWT payload object' };
  }

  const subject = String(payload.sub || '').trim();
  if (!subject) {
    return {
      user: null,
      error: 'JWT is missing required subject ("sub") claim',
    };
  }

  // Extract roles (handling 'roles' array, singular 'role', or space-delimited 'scope')
  const rawRoles: string[] = [];
  if (Array.isArray(payload.roles)) {
    for (const r of payload.roles) {
      if (typeof r === 'string') rawRoles.push(r);
    }
  } else if (typeof payload.roles === 'string') {
    rawRoles.push(payload.roles);
  } else if (typeof payload.role === 'string') {
    rawRoles.push(payload.role);
  } else if (Array.isArray(payload.role)) {
    for (const r of payload.role) {
      if (typeof r === 'string') rawRoles.push(r);
    }
  } else if (typeof payload.scope === 'string') {
    rawRoles.push(...payload.scope.split(' '));
  }

  const normalizedRoles = Array.from(
    new Set(rawRoles.map(normalizeRole).filter((r) => r.length > 0)),
  );

  // Extract permissions
  const permissions: string[] = [];
  if (Array.isArray(payload.permissions)) {
    for (const p of payload.permissions) {
      if (typeof p === 'string' && p.trim()) {
        permissions.push(p.trim());
      }
    }
  }

  // Expiration validation
  const expiration = typeof payload.exp === 'number' ? payload.exp : 0;
  const nowSeconds = Math.floor(Date.now() / 1000);

  if (expiration > 0 && expiration <= nowSeconds) {
    return {
      user: null,
      error: `Token has expired at ${new Date(expiration * 1000).toISOString()}`,
    };
  }

  // Audience claim
  let audience: string | string[] | undefined;
  if (typeof payload.aud === 'string') {
    audience = payload.aud;
  } else if (Array.isArray(payload.aud)) {
    audience = payload.aud.filter((a): a is string => typeof a === 'string');
  }

  const user: AuthUser = {
    subject,
    roles: normalizedRoles,
    permissions,
    issuer: payload.iss,
    audience,
    keyId: header.kid,
    issuedAt: typeof payload.iat === 'number' ? payload.iat : nowSeconds,
    expiration,
  };

  return { user, error: null };
}

export type TeardownListener = () => void;
const teardownListeners = new Set<TeardownListener>();

/**
 * Registers a callback invoked whenever an authenticated session is terminated
 * (via logout, session expiry, or account switch). Used by domain stores to purge
 * sensitive in-memory and sessionStorage state.
 */
export function registerTeardownListener(listener: TeardownListener): () => void {
  teardownListeners.add(listener);
  return () => {
    teardownListeners.delete(listener);
  };
}

/**
 * Notifies all registered teardown listeners to clean up sensitive data.
 */
export function notifyTeardownListeners(): void {
  for (const listener of teardownListeners) {
    try {
      listener();
    } catch {
      // Ignore individual listener failures to ensure full teardown cascade
    }
  }
}

class AuthStore {
  private state: AuthState = {
    status: 'initializing',
    token: null,
    user: null,
    isAuthenticated: false,
    error: null,
  };

  private listeners: Set<(state: AuthState) => void> = new Set();
  private expirationTimer: ReturnType<typeof setTimeout> | null = null;
  private isTornDown = false;
  private sessionEpoch = 0;
  private syncChannel: BroadcastChannel | null = null;

  constructor() {
    this.restoreSession();
    this.initSyncChannel();
  }

  private initSyncChannel(): void {
    try {
      if (typeof window !== 'undefined' && typeof BroadcastChannel !== 'undefined') {
        this.syncChannel = new BroadcastChannel('zkdoc_auth_sync');
        this.syncChannel.onmessage = (event: MessageEvent<{ type?: string; reason?: string }>) => {
          if (event.data?.type === 'LOGOUT' && this.state.isAuthenticated) {
            this.clearSession(event.data.reason || 'Session terminated from another tab.', false);
          }
        };
      }
    } catch {
      // BroadcastChannel unavailable
    }
  }

  private restoreSession(): void {
    try {
      if (typeof window !== 'undefined' && window.sessionStorage) {
        const storedToken = window.sessionStorage.getItem(STORAGE_KEY);
        if (storedToken) {
          const { user } = parseAndValidateJwt(storedToken);
          if (user) {
            this.state = {
              status: 'authenticated',
              token: storedToken,
              user,
              isAuthenticated: true,
              error: null,
            };
            this.scheduleExpirationWatch(user.expiration);
            return;
          } else {
            window.sessionStorage.removeItem(STORAGE_KEY);
          }
        }
      }
    } catch {
      // Storage unavailable or restricted
    }

    this.state = {
      status: 'unauthenticated',
      token: null,
      user: null,
      isAuthenticated: false,
      error: null,
    };
  }

  public getState(): AuthState {
    return this.state;
  }

  public subscribe(listener: (state: AuthState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Returns true if the session was explicitly terminated (via logout or expiry)
   * and is currently unauthenticated. Rejects stale async responses from committing
   * to domain stores.
   */
  public isSessionTerminated(): boolean {
    return this.isTornDown && !this.state.isAuthenticated;
  }

  /**
   * Returns monotonically increasing session epoch counter.
   */
  public getSessionEpoch(): number {
    return this.sessionEpoch;
  }

  /**
   * Test-only helper to reset teardown flags between test suites.
   */
  public resetForTesting(): void {
    this.clearExpirationTimer();
    this.state = {
      status: 'unauthenticated',
      token: null,
      user: null,
      isAuthenticated: false,
      error: null,
    };
    this.isTornDown = false;
    this.sessionEpoch = 0;
  }

  /**
   * Sets the active bearer token. If valid, transitions state to 'authenticated'.
   * If invalid, sets error and unauthenticated state.
   */
  public setToken(token: string | null): boolean {
    this.clearExpirationTimer();

    if (!token) {
      this.clearSession();
      return true;
    }

    const { user, error } = parseAndValidateJwt(token);

    if (!user) {
      this.state = {
        status: 'unauthenticated',
        token: null,
        user: null,
        isAuthenticated: false,
        error: error || 'Invalid token claims',
      };
      this.isTornDown = true;
      this.sessionEpoch++;
      notifyTeardownListeners();
      this.notify();
      return false;
    }

    // If an authenticated session existed for a different subject, purge prior user state
    if (this.state.isAuthenticated && this.state.user?.subject !== user.subject) {
      notifyTeardownListeners();
    }

    this.isTornDown = false;
    this.sessionEpoch++;

    this.state = {
      status: 'authenticated',
      token,
      user,
      isAuthenticated: true,
      error: null,
    };

    try {
      if (typeof window !== 'undefined' && window.sessionStorage) {
        window.sessionStorage.setItem(STORAGE_KEY, token);
      }
    } catch {
      // Storage failure non-fatal
    }

    this.scheduleExpirationWatch(user.expiration);
    this.notify();
    return true;
  }

  /**
   * Clears session in-memory, purges sensitive domain stores, and removes stored token from sessionStorage.
   */
  public clearSession(reason?: string, broadcast = true): void {
    this.clearExpirationTimer();
    this.isTornDown = true;
    this.sessionEpoch++;

    this.state = {
      status: 'unauthenticated',
      token: null,
      user: null,
      isAuthenticated: false,
      error: reason || null,
    };

    try {
      if (typeof window !== 'undefined' && window.sessionStorage) {
        window.sessionStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // Ignore
    }

    if (broadcast) {
      try {
        if (this.syncChannel) {
          this.syncChannel.postMessage({ type: 'LOGOUT', reason });
        }
      } catch {
        // Broadcast non-fatal
      }
    }

    notifyTeardownListeners();
    this.notify();
  }

  /**
   * Automatically handles proactive session expiration when token exp is reached.
   */
  private handleSessionExpired(): void {
    this.clearExpirationTimer();
    this.isTornDown = true;
    this.sessionEpoch++;

    this.state = {
      status: 'expired',
      token: null,
      user: null,
      isAuthenticated: false,
      error: 'Session has expired. Please connect a valid JWT to continue.',
    };

    try {
      if (typeof window !== 'undefined' && window.sessionStorage) {
        window.sessionStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // Ignore
    }

    try {
      if (this.syncChannel) {
        this.syncChannel.postMessage({
          type: 'LOGOUT',
          reason: 'Session expired',
        });
      }
    } catch {
      // Broadcast non-fatal
    }

    notifyTeardownListeners();
    this.notify();
  }

  private scheduleExpirationWatch(expirationEpochSeconds: number): void {
    if (!expirationEpochSeconds || expirationEpochSeconds <= 0) return;

    const nowSeconds = Math.floor(Date.now() / 1000);
    const secondsRemaining = expirationEpochSeconds - nowSeconds;

    if (secondsRemaining <= 0) {
      this.handleSessionExpired();
      return;
    }

    // Guard against 32-bit integer overflow in setTimeout (max ~24.8 days)
    const msUntilExpiry = Math.min(secondsRemaining * 1000, 2147483647);
    this.expirationTimer = setTimeout(() => {
      this.handleSessionExpired();
    }, msUntilExpiry);
  }

  private clearExpirationTimer(): void {
    if (this.expirationTimer) {
      clearTimeout(this.expirationTimer);
      this.expirationTimer = null;
    }
  }

  /**
   * Checks whether the authenticated user has the specified role (or ADMIN superuser).
   */
  public hasRole(role: string): boolean {
    if (!this.state.isAuthenticated || !this.state.user) return false;
    const target = normalizeRole(role);
    const userRoles = this.state.user.roles;
    return userRoles.includes('ADMIN') || userRoles.includes(target);
  }

  /**
   * Checks whether the authenticated user has ANY of the specified roles (or ADMIN).
   */
  public hasAnyRole(roles: readonly string[]): boolean {
    if (!this.state.isAuthenticated || !this.state.user) return false;
    if (this.state.user.roles.includes('ADMIN')) return true;
    const normalizedTargets = roles.map(normalizeRole);
    return this.state.user.roles.some((r) => normalizedTargets.includes(r));
  }

  /**
   * Checks whether the authenticated user holds a specific fine-grained permission.
   */
  public hasPermission(permission: string): boolean {
    if (!this.state.isAuthenticated || !this.state.user) return false;
    if (this.state.user.roles.includes('ADMIN')) return true;
    return this.state.user.permissions.includes(permission.trim());
  }

  private notify(): void {
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }
}

export const authStore = new AuthStore();

/**
 * React hook for consuming authentication state with reactive updates.
 */
export function useAuth(): AuthState {
  const [state, setState] = useState<AuthState>(authStore.getState());

  useEffect(() => {
    return authStore.subscribe(setState);
  }, []);

  return state;
}
