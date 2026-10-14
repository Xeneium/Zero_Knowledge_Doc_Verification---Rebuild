import { useState } from 'react';
import { CheckCircle2, KeyRound, LogOut, Shield, UserCheck } from 'lucide-react';
import { Dialog } from '../../components/ui/Dialog';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Alert } from '../../components/ui/Alert';
import { Textarea } from '../../components/ui/Field';
import { PRESET_ACCOUNTS, useAuth, type PlatformRole } from './AuthContext';

interface SessionModalProps {
  open: boolean;
  onClose: () => void;
}

export function SessionModal({ open, onClose }: SessionModalProps) {
  const { session, isAuthenticated, connectToken, connectPreset, disconnect } = useAuth();
  const [tokenInput, setTokenInput] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleConnectToken = () => {
    setError(null);
    try {
      if (!tokenInput.trim()) {
        setError('Please enter a valid Bearer JWT token.');
        return;
      }
      connectToken(tokenInput);
      setTokenInput('');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid JWT token');
    }
  };

  const handleSelectPreset = (role: PlatformRole) => {
    setError(null);
    connectPreset(role);
    onClose();
  };

  const handleDisconnect = () => {
    disconnect();
    onClose();
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={isAuthenticated ? 'Active platform session' : 'Connect security session'}
      description={
        isAuthenticated
          ? 'Manage your current authenticated cryptographic identity.'
          : 'Choose a role profile for evaluation or provide an externally signed Bearer JWT.'
      }
      footer={
        isAuthenticated ? (
          <Button variant="danger" size="sm" onClick={handleDisconnect}>
            <LogOut className="size-4" aria-hidden="true" />
            Disconnect session
          </Button>
        ) : (
          <Button variant="secondary" size="sm" onClick={onClose}>
            Cancel
          </Button>
        )
      }
    >
      <div className="space-y-5">
        {error ? (
          <Alert tone="danger" title="Authentication error">
            {error}
          </Alert>
        ) : null}

        {isAuthenticated && session ? (
          <div className="space-y-4">
            <div className="rounded-lg border border-success-200 bg-success-50 p-4">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="size-5 text-success-700" aria-hidden="true" />
                <span className="font-semibold text-success-900">Session Connected</span>
              </div>
              <div className="mt-3 space-y-1.5 text-sm text-ink-700">
                <p>
                  <span className="font-semibold text-ink-900">Subject (sub):</span>{' '}
                  <span className="font-mono text-ink-800">{session.sub}</span>
                </p>
                <div className="flex items-center gap-2">
                  <span className="font-semibold text-ink-900">Assigned Roles:</span>
                  <div className="flex flex-wrap gap-1">
                    {session.roles.map((r) => (
                      <Badge key={r} tone="accent">
                        {r}
                      </Badge>
                    ))}
                  </div>
                </div>
                {session.exp ? (
                  <p>
                    <span className="font-semibold text-ink-900">Expires:</span>{' '}
                    <span>{new Date(session.exp * 1000).toLocaleString()}</span>
                  </p>
                ) : null}
              </div>
            </div>

            <div className="rounded-lg border border-ink-200 bg-ink-50 p-3 text-xs text-ink-600">
              <span className="font-semibold text-ink-800">Security Invariant:</span> Bearer tokens are maintained
              strictly in memory for the active browser session. Raw private keys and proving secrets are never stored.
            </div>
          </div>
        ) : (
          <div className="space-y-5">
            <div>
              <p className="text-xs font-semibold tracking-wider text-ink-500 uppercase mb-2">
                Quick Role Presets
              </p>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {(Object.keys(PRESET_ACCOUNTS) as PlatformRole[]).map((role) => {
                  const preset = PRESET_ACCOUNTS[role];
                  return (
                    <button
                      key={role}
                      type="button"
                      onClick={() => handleSelectPreset(role)}
                      className="flex flex-col items-start rounded-lg border border-ink-200 bg-white p-3 text-left transition hover:border-accent-500 hover:bg-accent-50 focus:outline-none focus:ring-2 focus:ring-accent-500"
                    >
                      <div className="flex w-full items-center justify-between">
                        <span className="text-sm font-semibold text-ink-900">{preset.label}</span>
                        <Badge tone="accent">{role}</Badge>
                      </div>
                      <p className="mt-1 line-clamp-2 text-xs text-ink-600">{preset.description}</p>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="relative">
              <div className="absolute inset-0 flex items-center" aria-hidden="true">
                <div className="w-full border-t border-ink-200" />
              </div>
              <div className="relative flex justify-center text-xs uppercase">
                <span className="bg-white px-2 text-ink-500">Or Paste External JWT</span>
              </div>
            </div>

            <div className="space-y-2">
              <Textarea
                label="Bearer JWT Token"
                hint="Paste an RSA-signed JSON Web Token with 'sub' and 'roles' claims."
                placeholder="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Li4ufQ..."
                value={tokenInput}
                onChange={(e) => setTokenInput(e.target.value)}
                className="font-mono text-xs"
              />
              <Button variant="primary" size="sm" onClick={handleConnectToken} className="w-full">
                <KeyRound className="size-4" aria-hidden="true" />
                Connect with Bearer Token
              </Button>
            </div>
          </div>
        )}
      </div>
    </Dialog>
  );
}
