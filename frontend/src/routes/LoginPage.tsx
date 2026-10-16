import React, { useState, useEffect } from 'react';
import { useLocation, useNavigate, Link } from 'react-router';
import {
  Lock,
  ShieldCheck,
  KeyRound,
  Eye,
  EyeOff,
  ArrowRight,
  AlertCircle,
  UserCheck,
  LogOut,
  Info,
  Sparkles,
} from 'lucide-react';
import {
  authStore,
  type AuthState,
  parseAndValidateJwt,
} from '../state/authStore';
import { validateReturnUrl } from '../lib/utils/security';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Badge } from '../components/common/Badge';
import { Alert } from '../components/common/Alert';
import { FormField } from '../components/common/FormField';
import { TextArea } from '../components/common/TextArea';

export interface DemoAccount {
  role: string;
  username: string;
  subject: string;
  description: string;
  token: string;
}

export const LoginPage: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  const [auth, setAuth] = useState<AuthState>(authStore.getState());
  const [tokenInput, setTokenInput] = useState('');
  const [showToken, setShowToken] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [demoAccounts, setDemoAccounts] = useState<DemoAccount[]>([]);

  useEffect(() => {
    return authStore.subscribe(setAuth);
  }, []);

  useEffect(() => {
    let active = true;
    fetch('/api/public/demo-tokens')
      .then((res) => {
        if (!res.ok) throw new Error('Not available');
        return res.json() as Promise<{ accounts: DemoAccount[] }>;
      })
      .then((data) => {
        if (active && Array.isArray(data?.accounts) && data.accounts.length > 0) {
          setDemoAccounts(data.accounts);
        }
      })
      .catch(() => {
        // Silently ignore if demo endpoint is unavailable or seed disabled
      });

    return () => {
      active = false;
    };
  }, []);

  const searchParams = new URLSearchParams(location.search);
  const rawReturnUrl = searchParams.get('returnUrl');
  const safeReturnUrl = validateReturnUrl(rawReturnUrl, '/');

  // Real-time preview of parsed claims
  const { user: parsedClaims, error: claimsError } = tokenInput.trim()
    ? parseAndValidateJwt(tokenInput.trim())
    : { user: null, error: null };

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setSubmitError(null);

    const token = tokenInput.trim();
    if (!token) {
      setSubmitError('Please enter a valid Bearer JWT token.');
      return;
    }

    setIsSubmitting(true);

    try {
      const success = authStore.setToken(token);
      if (success) {
        void navigate(safeReturnUrl, { replace: true });
      } else {
        const currentError = authStore.getState().error;
        setSubmitError(
          currentError ||
            'Failed to establish session: Token rejected by security validation.',
        );
      }
    } catch (err) {
      const message =
        err instanceof Error
          ? err.message
          : 'Unexpected error establishing session.';
      setSubmitError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDisconnect = () => {
    authStore.clearSession();
    setTokenInput('');
    setSubmitError(null);
  };

  return (
    <div className="mx-auto max-w-2xl px-4 py-8 sm:px-6">
      <div className="mb-8 text-center">
        <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-indigo-100 text-indigo-700 dark:bg-indigo-950/80 dark:text-indigo-400">
          <Lock className="h-6 w-6" aria-hidden="true" />
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl dark:text-slate-100">
          Session Authentication
        </h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Connect a cryptographically signed Bearer JWT token to access
          authenticated workspaces.
        </p>
      </div>

      {/* Architecture & Zero-Trust Notice */}
      <div className="mb-6">
        <Alert variant="info" title="Zero-Trust OAuth2 Resource Server">
          ZKDoc operates as an RFC 7519 / RFC 8725 OAuth2 Resource Server.
          Access tokens must be RSA-signed (RS256/PS256) by the platform key
          authority targeting audience{' '}
          <code className="font-mono text-xs font-semibold">zkdoc-api</code>.
        </Alert>
      </div>

      {/* Currently Authenticated State Panel */}
      {auth.isAuthenticated && auth.user ? (
        <Card className="mb-6 border-indigo-200 bg-indigo-50/40 p-6 dark:border-indigo-900 dark:bg-indigo-950/20">
          <div className="flex items-start gap-3">
            <UserCheck
              className="mt-1 h-5 w-5 text-indigo-600 dark:text-indigo-400"
              aria-hidden="true"
            />
            <div className="flex-1 space-y-3">
              <div>
                <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">
                  Active Session Connected
                </h2>
                <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
                  You are currently authenticated as{' '}
                  <strong className="text-indigo-700 dark:text-indigo-300">
                    {auth.user.subject}
                  </strong>
                  .
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-1.5">
                <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
                  Assigned Roles:
                </span>
                {auth.user.roles.length > 0 ? (
                  auth.user.roles.map((role) => (
                    <Badge key={role} variant="info">
                      {role}
                    </Badge>
                  ))
                ) : (
                  <Badge variant="neutral">Standard User</Badge>
                )}
              </div>

              {auth.user.expiration > 0 && (
                <div className="text-xs text-slate-500 dark:text-slate-400">
                  Expires:{' '}
                  {new Date(auth.user.expiration * 1000).toLocaleString()}
                </div>
              )}

              <div className="flex flex-wrap items-center gap-3 pt-2">
                <Button
                  variant="primary"
                  onClick={() => {
                    void navigate(safeReturnUrl, { replace: true });
                  }}
                  className="flex items-center gap-1.5"
                >
                  <span>
                    Continue to{' '}
                    {safeReturnUrl === '/' ? 'Overview' : safeReturnUrl}
                  </span>
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </Button>

                <Button
                  variant="outline"
                  onClick={handleDisconnect}
                  className="flex items-center gap-1.5 text-red-600 hover:text-red-700 dark:text-red-400"
                >
                  <LogOut className="h-4 w-4" aria-hidden="true" />
                  <span>Disconnect Session</span>
                </Button>
              </div>
            </div>
          </div>
        </Card>
      ) : (
        /* Token Input Form */
        <Card className="p-6">
          <form onSubmit={handleSubmit} noValidate className="space-y-5">
            {submitError && (
              <Alert variant="danger" title="Authentication Failed">
                {submitError}
              </Alert>
            )}

            {safeReturnUrl !== '/' && (
              <div className="flex items-center gap-2 rounded-md bg-amber-50 p-3 text-xs text-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
                <Info className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span>
                  Authentication required to access{' '}
                  <strong className="font-mono">{safeReturnUrl}</strong>.
                </span>
              </div>
            )}

            {demoAccounts.length > 0 && (
              <div
                role="region"
                aria-label="Demo Quick Connect Accounts"
                className="rounded-lg border border-indigo-200 bg-indigo-50/60 p-4 dark:border-indigo-900/60 dark:bg-indigo-950/20"
              >
                <div className="mb-2.5 flex items-center justify-between">
                  <span className="flex items-center gap-1.5 text-xs font-semibold tracking-wider text-indigo-700 uppercase dark:text-indigo-300">
                    <Sparkles
                      className="h-4 w-4 text-indigo-600 dark:text-indigo-400"
                      aria-hidden="true"
                    />
                    Evaluation Demo Accounts (1-Click Fill)
                  </span>
                  <Badge variant="info">Seeded Mode</Badge>
                </div>
                <p className="mb-3 text-xs text-slate-600 dark:text-slate-400">
                  Select a pre-seeded synthetic account to automatically load
                  its signed cryptographic Bearer token:
                </p>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                  {demoAccounts.map((acc) => (
                    <button
                      key={acc.role}
                      type="button"
                      onClick={() => {
                        setTokenInput(acc.token);
                        if (submitError) setSubmitError(null);
                      }}
                      className="group flex flex-col items-start rounded-md border border-slate-200 bg-white p-2.5 text-left transition-all hover:border-indigo-400 hover:shadow-xs focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:border-slate-800 dark:bg-slate-900 dark:hover:border-indigo-600"
                    >
                      <div className="flex w-full items-center justify-between">
                        <span className="text-xs font-bold text-slate-900 group-hover:text-indigo-600 dark:text-slate-100 dark:group-hover:text-indigo-400">
                          {acc.username}
                        </span>
                        <Badge
                          variant={
                            acc.role === 'ADMIN'
                              ? 'danger'
                              : acc.role === 'ISSUER'
                                ? 'info'
                                : acc.role === 'HOLDER'
                                  ? 'success'
                                  : 'neutral'
                          }
                        >
                          {acc.role}
                        </Badge>
                      </div>
                      <span className="mt-1 line-clamp-2 text-[11px] text-slate-500 dark:text-slate-400">
                        {acc.description}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div>
              <div className="mb-1 flex items-center justify-end">
                <button
                  type="button"
                  onClick={() => setShowToken(!showToken)}
                  className="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-700 focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:text-slate-400 dark:hover:text-slate-200"
                  aria-label={
                    showToken ? 'Mask token value' : 'Show raw token value'
                  }
                >
                  {showToken ? (
                    <>
                      <EyeOff className="h-3.5 w-3.5" aria-hidden="true" />
                      <span>Mask Value</span>
                    </>
                  ) : (
                    <>
                      <Eye className="h-3.5 w-3.5" aria-hidden="true" />
                      <span>Reveal Value</span>
                    </>
                  )}
                </button>
              </div>

              <FormField
                id="jwt-token-input"
                label="Bearer JWT Token"
                helperText="Paste the 3-part signed compact JWT provided by your authority."
                error={
                  claimsError && tokenInput.trim() ? claimsError : undefined
                }
                required
              >
                {(fieldProps) => (
                  <TextArea
                    {...fieldProps}
                    rows={4}
                    value={tokenInput}
                    onChange={(e) => {
                      setTokenInput(e.target.value);
                      if (submitError) setSubmitError(null);
                    }}
                    placeholder="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Ii4uLiJ9..."
                    className={
                      showToken
                        ? 'font-mono text-xs'
                        : 'font-mono text-xs [-webkit-text-security:disc] [text-security:disc]'
                    }
                  />
                )}
              </FormField>
            </div>

            {/* Live Token Decoded Claims Preview */}
            {parsedClaims && (
              <div
                role="region"
                aria-label="Decoded JWT Claims Preview"
                className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/60"
              >
                <div className="mb-2 flex items-center justify-between">
                  <span className="flex items-center gap-1.5 text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                    <ShieldCheck
                      className="h-4 w-4 text-emerald-600 dark:text-emerald-400"
                      aria-hidden="true"
                    />
                    Decoded Token Claims
                  </span>
                  <Badge variant="success">Format Valid</Badge>
                </div>

                <dl className="grid grid-cols-1 gap-2 text-xs sm:grid-cols-2">
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">
                      Subject (sub):
                    </dt>
                    <dd className="font-semibold text-slate-800 dark:text-slate-200">
                      {parsedClaims.subject}
                    </dd>
                  </div>

                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">
                      Roles:
                    </dt>
                    <dd className="flex flex-wrap gap-1">
                      {parsedClaims.roles.map((r) => (
                        <Badge key={r} variant="info">
                          {r}
                        </Badge>
                      ))}
                    </dd>
                  </div>

                  {parsedClaims.issuer && (
                    <div>
                      <dt className="text-slate-500 dark:text-slate-400">
                        Issuer (iss):
                      </dt>
                      <dd className="truncate font-mono text-slate-700 dark:text-slate-300">
                        {parsedClaims.issuer}
                      </dd>
                    </div>
                  )}

                  {parsedClaims.expiration > 0 && (
                    <div>
                      <dt className="text-slate-500 dark:text-slate-400">
                        Expiration (exp):
                      </dt>
                      <dd className="text-slate-700 dark:text-slate-300">
                        {new Date(
                          parsedClaims.expiration * 1000,
                        ).toLocaleTimeString()}
                      </dd>
                    </div>
                  )}
                </dl>
              </div>
            )}

            <div className="flex items-center justify-end gap-3 pt-2">
              <Link to="/">
                <Button variant="ghost" type="button">
                  Cancel
                </Button>
              </Link>

              <Button
                type="submit"
                variant="primary"
                isLoading={isSubmitting}
                disabled={isSubmitting || !tokenInput.trim() || !!claimsError}
                className="flex items-center gap-2"
              >
                <KeyRound className="h-4 w-4" aria-hidden="true" />
                <span>Connect Session</span>
              </Button>
            </div>
          </form>
        </Card>
      )}

      {/* Footer Info */}
      <div className="mt-6 flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
        <div className="flex items-center gap-1.5">
          <AlertCircle
            className="h-3.5 w-3.5 text-slate-400"
            aria-hidden="true"
          />
          <span>Tokens are stored strictly in memory per ADR-003.</span>
        </div>
        <Link
          to="/"
          className="text-indigo-600 hover:underline dark:text-indigo-400"
        >
          Return to Overview
        </Link>
      </div>
    </div>
  );
};
