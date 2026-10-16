import React, { useState } from 'react';
import {
  Award,
  Search,
  ShieldCheck,
  PauseCircle,
  PlayCircle,
  XCircle,
  History,
  Clock,
  User,
} from 'lucide-react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '../../../components/common/Card';
import { Badge } from '../../../components/common/Badge';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { Alert } from '../../../components/feedback/Alert';
import { ConfirmationModal } from '../../../components/feedback/ConfirmationModal';
import { adminApi } from '../../../api/adminApi';
import {
  adminStore,
  useAdminCredentialOversight,
} from '../../../state/adminStore';

export const CredentialOversightCard: React.FC = () => {
  const oversight = useAdminCredentialOversight();
  const [searchId, setSearchId] = useState('');
  const [reasonInput, setReasonInput] = useState('');

  // Confirmation modal state
  const [confirmAction, setConfirmAction] = useState<
    'suspend' | 'resume' | 'revoke' | null
  >(null);
  const [isSubmittingAction, setIsSubmittingAction] = useState(false);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const handleLookup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (oversight.isLoading) return;
    const id = searchId.trim();
    if (!id) return;

    setActionSuccess(null);
    adminStore.setCredentialOversight({
      credentialId: id,
      isLoading: true,
      error: null,
      verification: null,
    });

    try {
      const [lifecycle, history, verification] = await Promise.all([
        adminApi.getCredentialLifecycle(id),
        adminApi.getCredentialLifecycleHistory(id).catch(() => []),
        adminApi.verifyCredential(id).catch(() => null),
      ]);

      adminStore.setCredentialOversight({
        lifecycle,
        history,
        verification,
        isLoading: false,
      });
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : 'Failed to look up credential';
      adminStore.setCredentialOversight({
        isLoading: false,
        error: msg,
        lifecycle: null,
        history: [],
        verification: null,
      });
    }
  };

  const handleExecuteLifecycleAction = async () => {
    if (isSubmittingAction || !confirmAction || !oversight.credentialId) return;

    setIsSubmittingAction(true);
    setActionSuccess(null);
    try {
      const id = oversight.credentialId;
      const reason = reasonInput.trim() || undefined;

      let res;
      if (confirmAction === 'suspend') {
        res = await adminApi.suspendCredential(id, reason);
      } else if (confirmAction === 'resume') {
        res = await adminApi.resumeCredential(id, reason);
      } else {
        res = await adminApi.revokeCredential(id, reason);
      }

      // Refresh lifecycle history and verification
      const [history, verification] = await Promise.all([
        adminApi.getCredentialLifecycleHistory(id).catch(() => []),
        adminApi.verifyCredential(id).catch(() => null),
      ]);

      adminStore.setCredentialOversight({
        lifecycle: res,
        history,
        verification,
      });

      setActionSuccess(
        `Credential successfully ${confirmAction === 'suspend' ? 'suspended' : confirmAction === 'resume' ? 'resumed' : 'revoked'}.`,
      );
      setConfirmAction(null);
      setReasonInput('');
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : 'Lifecycle mutation failed';
      adminStore.setCredentialOversight({ error: msg });
    } finally {
      setIsSubmittingAction(false);
    }
  };

  const statusBadge = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return <Badge variant="success">ACTIVE</Badge>;
      case 'SUSPENDED':
        return <Badge variant="warning">SUSPENDED</Badge>;
      case 'REVOKED':
        return <Badge variant="danger">REVOKED</Badge>;
      case 'EXPIRED':
        return <Badge variant="neutral">EXPIRED</Badge>;
      default:
        return <Badge variant="neutral">{status}</Badge>;
    }
  };

  return (
    <Card className="shadow-xs">
      <CardHeader>
        <div className="flex items-center gap-2">
          <Award
            className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
            aria-hidden="true"
          />
          <CardTitle>Credential Oversight & Lifecycle Administration</CardTitle>
        </div>
        <CardDescription>
          Inspect authoritative credential state, verify digital signatures,
          review lifecycle history, and perform administrative status overrides.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-6">
        {/* Lookup Bar */}
        <form onSubmit={(e) => void handleLookup(e)} className="flex gap-2">
          <div className="relative flex-1">
            <Search
              className="absolute top-2.5 left-3 h-4 w-4 text-slate-400"
              aria-hidden="true"
            />
            <Input
              value={searchId}
              onChange={(e) => setSearchId(e.target.value)}
              placeholder="Enter Credential UUID (e.g. 550e8400-e29b-41d4-a716-446655440000)"
              className="pl-9 font-mono text-xs"
              required
            />
          </div>
          <Button
            type="submit"
            variant="primary"
            isLoading={oversight.isLoading}
            aria-label="Inspect credential"
          >
            Lookup Credential
          </Button>
        </form>

        {oversight.error && (
          <Alert variant="danger" title="Oversight Error">
            {oversight.error}
          </Alert>
        )}

        {actionSuccess && (
          <Alert variant="success" title="State Updated">
            {actionSuccess}
          </Alert>
        )}

        {/* Selected Credential Lifecycle View */}
        {oversight.lifecycle && (
          <div className="space-y-6">
            {/* Status & Action Bar */}
            <div className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
              <div className="space-y-1">
                <div className="flex items-center gap-3">
                  <span className="font-semibold text-slate-900 dark:text-slate-100">
                    Status:
                  </span>
                  {statusBadge(oversight.lifecycle.status)}
                  <span className="text-xs text-slate-500">
                    Version: {oversight.lifecycle.version}
                  </span>
                </div>
                <div className="font-mono text-xs text-slate-600 dark:text-slate-400">
                  ID: {oversight.lifecycle.credentialId}
                </div>
                <div className="text-xs text-slate-500">
                  Issuer: {oversight.lifecycle.issuerId}
                </div>
              </div>

              {/* Administrative Lifecycle Controls */}
              <div className="flex items-center gap-2">
                {oversight.lifecycle.status === 'ACTIVE' && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setConfirmAction('suspend')}
                    className="text-amber-700 hover:bg-amber-50 dark:text-amber-400"
                  >
                    <PauseCircle
                      className="mr-1.5 h-3.5 w-3.5"
                      aria-hidden="true"
                    />
                    Suspend
                  </Button>
                )}

                {oversight.lifecycle.status === 'SUSPENDED' && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setConfirmAction('resume')}
                    className="text-emerald-700 hover:bg-emerald-50 dark:text-emerald-400"
                  >
                    <PlayCircle
                      className="mr-1.5 h-3.5 w-3.5"
                      aria-hidden="true"
                    />
                    Resume to Active
                  </Button>
                )}

                {oversight.lifecycle.status !== 'REVOKED' && (
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={() => setConfirmAction('revoke')}
                  >
                    <XCircle
                      className="mr-1.5 h-3.5 w-3.5"
                      aria-hidden="true"
                    />
                    Revoke
                  </Button>
                )}
              </div>
            </div>

            {/* Cryptographic Signature Verification Box */}
            {oversight.verification && (
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <ShieldCheck
                      className="h-4 w-4 text-indigo-600 dark:text-indigo-400"
                      aria-hidden="true"
                    />
                    <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                      Cryptographic Signature Verification
                    </span>
                  </div>
                  <Badge
                    variant={
                      oversight.verification.verified ? 'success' : 'danger'
                    }
                  >
                    {oversight.verification.verified
                      ? 'SIGNATURE VALID'
                      : 'SIGNATURE INVALID'}
                  </Badge>
                </div>
                {oversight.verification.message && (
                  <p className="mt-2 text-xs text-slate-600 dark:text-slate-400">
                    {oversight.verification.message}
                  </p>
                )}
              </div>
            )}

            {/* Lifecycle Audit History Timeline */}
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-xs font-semibold tracking-wider text-slate-800 uppercase dark:text-slate-200">
                <History className="h-4 w-4" aria-hidden="true" />
                <span>Lifecycle Event Timeline</span>
              </div>

              {oversight.history.length === 0 ? (
                <div className="rounded-md border border-slate-200 p-4 text-center text-xs text-slate-500 dark:border-slate-800">
                  No historical lifecycle modification events recorded.
                </div>
              ) : (
                <div className="space-y-2">
                  {oversight.history.map((ev, idx) => (
                    <div
                      key={ev.id || idx}
                      className="flex items-start gap-3 rounded-lg border border-slate-200 p-3 text-xs dark:border-slate-800"
                    >
                      <div className="mt-0.5">
                        <Clock
                          className="h-4 w-4 text-slate-400"
                          aria-hidden="true"
                        />
                      </div>
                      <div className="flex-1 space-y-1">
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-slate-900 dark:text-slate-100">
                            {ev.eventType}
                          </span>
                          <span className="text-[11px] text-slate-500">
                            {new Date(ev.timestamp).toLocaleString()}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 text-slate-600 dark:text-slate-400">
                          <User className="h-3 w-3" aria-hidden="true" />
                          <span>
                            Actor: <strong>{ev.actor}</strong> ({ev.actorRole})
                          </span>
                          <span>•</span>
                          <span>
                            State:{' '}
                            <span className="font-mono">{ev.fromStatus}</span> →{' '}
                            <span className="font-mono">{ev.toStatus}</span>
                          </span>
                        </div>
                        {ev.reason && (
                          <div className="text-[11px] text-slate-500 italic">
                            Reason: &quot;{ev.reason}&quot;
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </CardContent>

      {/* Confirmation Modal for Suspend / Resume / Revoke */}
      <ConfirmationModal
        isOpen={confirmAction !== null}
        onClose={() => {
          setConfirmAction(null);
          setReasonInput('');
        }}
        onConfirm={() => void handleExecuteLifecycleAction()}
        title={`Confirm Credential ${confirmAction ? confirmAction.toUpperCase() : ''}`}
        message={
          <div className="space-y-3">
            <p>
              Are you sure you want to execute administrative {confirmAction} on
              credential:
            </p>
            <div className="font-mono text-xs text-slate-700 select-all dark:text-slate-300">
              {oversight.credentialId}
            </div>

            {confirmAction === 'revoke' && (
              <Alert variant="danger" title="Permanent Invalidation">
                Revocation is irreversible. The credential cannot be resumed or
                re-activated after revocation.
              </Alert>
            )}

            <Input
              label="Audit Reason Code (Optional)"
              value={reasonInput}
              onChange={(e) => setReasonInput(e.target.value)}
              placeholder="e.g. Administrative policy enforcement"
              maxLength={256}
              helperText="Explanation recorded into the immutable audit trail"
            />
          </div>
        }
        confirmText={`Execute ${confirmAction || ''}`}
        variant={confirmAction === 'revoke' ? 'danger' : 'warning'}
        isLoading={isSubmittingAction}
      />
    </Card>
  );
};
