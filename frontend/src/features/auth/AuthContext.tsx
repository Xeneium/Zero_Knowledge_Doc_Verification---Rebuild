import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { setAuthToken, setUnauthorizedListener } from '../../lib/api/client';

export interface UserSession {
  sub: string;
  roles: string[];
  exp?: number;
  rawToken: string;
}

export type PlatformRole = 'ISSUER' | 'HOLDER' | 'VERIFIER' | 'ADMIN';

export interface AuthContextValue {
  session: UserSession | null;
  isAuthenticated: boolean;
  sessionExpired: boolean;
  connectToken: (token: string) => void;
  connectPreset: (role: PlatformRole) => void;
  disconnect: () => void;
  dismissExpiryNotice: () => void;
  hasRole: (role: PlatformRole) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function base64UrlEncode(str: string): string {
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function createMockJwt(sub: string, role: PlatformRole, kid = 'test-key-1'): string {
  const header = base64UrlEncode(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid }));
  const now = Math.floor(Date.now() / 1000);
  const payload = base64UrlEncode(
    JSON.stringify({
      sub,
      iss: 'zkdoc-platform',
      aud: ['zkdoc-api'],
      roles: [role],
      iat: now,
      exp: now + 86400,
    }),
  );
  const signature = base64UrlEncode('demo-signature-for-development-eval');
  return `${header}.${payload}.${signature}`;
}

export const PRESET_ACCOUNTS: Record<PlatformRole, { sub: string; label: string; description: string }> = {
  ISSUER: {
    sub: 'acme-corp',
    label: 'Issuer (Acme Corp)',
    description: 'Issue credentials with attribute commitments, upload documents, manage credential lifecycle.',
  },
  HOLDER: {
    sub: 'holder-bob',
    label: 'Holder (Bob)',
    description: 'Inspect provisioned credentials, generate zero-knowledge predicate proofs without leaking witnesses.',
  },
  VERIFIER: {
    sub: 'verifier-corp',
    label: 'Verifier (Verifier Corp)',
    description: 'Verify cryptographic document signatures, check credential lifecycle, verify Bulletproof predicate proofs.',
  },
  ADMIN: {
    sub: 'admin-root',
    label: 'Administrator (Admin Root)',
    description: 'Inspect security audit events, verify cryptographic hash chain integrity, manage trust anchor keys.',
  },
};

export function parseJwt(token: string): UserSession {
  const trimmed = token.trim();
  const parts = trimmed.split('.');
  if (parts.length !== 3) {
    throw new Error('Invalid JWT format: Token must contain three parts separated by dots.');
  }

  let payloadJson: string;
  try {
    let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    while (base64.length % 4 !== 0) {
      base64 += '=';
    }
    payloadJson = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
  } catch {
    throw new Error('Failed to decode JWT base64url payload.');
  }

  let claims: Record<string, unknown>;
  try {
    claims = JSON.parse(payloadJson);
  } catch {
    throw new Error('JWT payload is not valid JSON.');
  }

  const sub = typeof claims.sub === 'string' && claims.sub.trim() ? claims.sub.trim() : 'anonymous';
  const roles: string[] = [];

  const addRole = (r: unknown) => {
    if (typeof r === 'string') {
      const clean = r.toUpperCase().replace(/^ROLE_/, '');
      if (['ISSUER', 'HOLDER', 'VERIFIER', 'ADMIN'].includes(clean) && !roles.includes(clean)) {
        roles.push(clean);
      }
    }
  };

  if (Array.isArray(claims.roles)) {
    claims.roles.forEach(addRole);
  } else if (typeof claims.roles === 'string') {
    addRole(claims.roles);
  }

  if (Array.isArray(claims.role)) {
    claims.role.forEach(addRole);
  } else if (typeof claims.role === 'string') {
    addRole(claims.role);
  }

  const exp = typeof claims.exp === 'number' ? claims.exp : undefined;
  if (exp && exp * 1000 < Date.now()) {
    throw new Error('The provided JWT token has already expired.');
  }

  return {
    sub,
    roles: roles.length > 0 ? roles : ['ANONYMOUS'],
    exp,
    rawToken: trimmed,
  };
}

const STORAGE_KEY = 'zkdoc.session.token';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<UserSession | null>(() => {
    try {
      const saved = sessionStorage.getItem(STORAGE_KEY);
      if (saved) {
        const parsed = parseJwt(saved);
        setAuthToken(saved);
        return parsed;
      }
    } catch {
      sessionStorage.removeItem(STORAGE_KEY);
    }
    return null;
  });

  const [sessionExpired, setSessionExpired] = useState(false);

  const disconnect = useCallback(() => {
    setSession(null);
    setAuthToken(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // storage unavailable
    }
  }, []);

  const connectToken = useCallback((token: string) => {
    const parsed = parseJwt(token);
    setSession(parsed);
    setAuthToken(parsed.rawToken);
    setSessionExpired(false);
    try {
      sessionStorage.setItem(STORAGE_KEY, parsed.rawToken);
    } catch {
      // storage unavailable
    }
  }, []);

  const connectPreset = useCallback(
    (role: PlatformRole) => {
      const config = PRESET_ACCOUNTS[role];
      const token = createMockJwt(config.sub, role);
      connectToken(token);
    },
    [connectToken],
  );

  const dismissExpiryNotice = useCallback(() => {
    setSessionExpired(false);
  }, []);

  const hasRole = useCallback(
    (role: PlatformRole): boolean => {
      if (!session) return false;
      return session.roles.includes(role) || session.roles.includes('ADMIN');
    },
    [session],
  );

  useEffect(() => {
    setUnauthorizedListener(() => {
      disconnect();
      setSessionExpired(true);
    });
    return () => {
      setUnauthorizedListener(null);
    };
  }, [disconnect]);

  return (
    <AuthContext.Provider
      value={{
        session,
        isAuthenticated: session !== null,
        sessionExpired,
        connectToken,
        connectPreset,
        disconnect,
        dismissExpiryNotice,
        hasRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
