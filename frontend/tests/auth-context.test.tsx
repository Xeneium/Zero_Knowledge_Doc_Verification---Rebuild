import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  AuthProvider,
  useAuth,
  parseJwt,
  createMockJwt,
} from '../src/features/auth/AuthContext';
import { getAuthToken, setAuthToken } from '../src/lib/api/client';

function TestConsumer() {
  const { session, isAuthenticated, connectToken, connectPreset, disconnect } = useAuth();
  return (
    <div>
      <div data-testid="auth-status">{isAuthenticated ? 'authenticated' : 'unauthenticated'}</div>
      <div data-testid="user-sub">{session?.sub || 'none'}</div>
      <div data-testid="user-roles">{session?.roles.join(',') || 'none'}</div>
      <button onClick={() => connectPreset('ISSUER')}>Connect Issuer</button>
      <button onClick={() => connectPreset('HOLDER')}>Connect Holder</button>
      <button onClick={() => connectPreset('VERIFIER')}>Connect Verifier</button>
      <button onClick={() => connectPreset('ADMIN')}>Connect Admin</button>
      <button onClick={disconnect}>Disconnect</button>
      <button onClick={() => connectToken('invalid.token')}>Connect Invalid</button>
    </div>
  );
}

describe('AuthContext and Session Management', () => {
  beforeEach(() => {
    setAuthToken(null);
    sessionStorage.clear();
  });

  it('correctly creates and parses mock JWT tokens', () => {
    const token = createMockJwt('alice-issuer', 'ISSUER');
    const parsed = parseJwt(token);

    expect(parsed.sub).toBe('alice-issuer');
    expect(parsed.roles).toEqual(['ISSUER']);
    expect(parsed.exp).toBeDefined();
  });

  it('rejects malformed tokens', () => {
    expect(() => parseJwt('not-a-jwt')).toThrow('Invalid JWT format');
    expect(() => parseJwt('header.invalid-base64.signature')).toThrow();
  });

  it('connects a preset role and attaches token to api client', async () => {
    const user = userEvent.setup();
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    expect(screen.getByTestId('auth-status')).toHaveTextContent('unauthenticated');
    expect(getAuthToken()).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Connect Issuer' }));

    expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated');
    expect(screen.getByTestId('user-sub')).toHaveTextContent('acme-corp');
    expect(screen.getByTestId('user-roles')).toHaveTextContent('ISSUER');
    expect(getAuthToken()).not.toBeNull();
  });

  it('disconnects and clears the token from memory and storage', async () => {
    const user = userEvent.setup();
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await user.click(screen.getByRole('button', { name: 'Connect Admin' }));
    expect(screen.getByTestId('user-roles')).toHaveTextContent('ADMIN');

    await user.click(screen.getByRole('button', { name: 'Disconnect' }));
    expect(screen.getByTestId('auth-status')).toHaveTextContent('unauthenticated');
    expect(screen.getByTestId('user-sub')).toHaveTextContent('none');
    expect(getAuthToken()).toBeNull();
  });
});
