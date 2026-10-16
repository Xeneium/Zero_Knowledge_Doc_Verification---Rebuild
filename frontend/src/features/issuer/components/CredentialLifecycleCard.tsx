import React, { useState, useEffect, useCallback } from 'react';
import {
  Activity,
  Search,
  PauseCircle,
  PlayCircle,
  XCircle,
  ShieldCheck,
  History,
  AlertTriangle,
} from 'lucide-react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { TextArea } from '../../../components/common/TextArea';
import { Badge } from '../../../components/common/Badge';
import { Alert } from '../../../components/feedback/Alert';
import { Dialog } from '../../../components/feedback/Dialog';
import { CopyToClipboard } from '../../../components/data/CopyToClipboard';
import { issuerApi } from '../../../api/issuerApi';
import { issuerStore, useIssuerActivities } from '../../../state/issuerStore';
import type {
  CredentialLifecycleResponse,
  CredentialLifecycleEvent,
  CredentialVerificationResult,
  CredentialStatus,
} from '../../../api/types';

export interface CredentialLifecycleCardProps {
  initialCredentialId?: string;
}

export const CredentialLifecycleCard: React.FC<
  CredentialLifecycleCardProps
> = ({ initialCredentialId = '' }) => {
  const [credentialId, setCredentialId] = useState(initialCredentialId);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lifecycle, setLifecycle] =
    useState<CredentialLifecycleResponse | null>(null);

  // History & Verification states
  const [historyEvents, setHistoryEvents] = useState<
    CredentialLifecycleEvent[] | null
  >(null);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [verificationResult, setVerificationResult] =
    useState<CredentialVerificationResult | null>(null);
  const [isVerifying, setIsVerifying] = useState(false);

  // Modal actions
  const [activeModalAction, setActiveModalAction] = useState<
    'suspend' | 'resume' | 'revoke' | null
  >(null);
  const [actionReason, setActionReason] = useState('');
  const [isMutating, setIsMutating] = useState(false);
  const [mutationSuccessMessage, setMutationSuccessMessage] = useState<
    string | null
  >(null);

  const activities = useIssuerActivities();

  const handleFetchStatus = useCallback(
    async (idToFetch?: string) => {
      const id = (idToFetch || credentialId).trim();
      if (!id) {
        setError('Please enter a valid Credential UUID');
        return;
      }

      setIsLoading(true);
      setError(null);
      setMutationSuccessMessage(null);
      setVerificationResult(null);

      try {
        const data = await issuerApi.getCredentialLifecycle(id);
        setLifecycle(data);
        issuerStore.updateStatus(id, data.status);
      } catch (err) {
        const msg =
          err instanceof Error
            ? err.message
            : 'Failed to fetch credential lifecycle';
        setError(msg);
        setLifecycle(null);
      } finally {
        setIsLoading(false);
      }
    },
    [credentialId],
  );

  useEffect(() => {
    let active = true;
    const id = initialCredentialId?.trim();
    if (id) {
      issuerApi
        .getCredentialLifecycle(id)
        .then((data) => {
          if (active) {
            setLifecycle(data);
            issuerStore.updateStatus(id, data.status);
          }
        })
        .catch((err: unknown) => {
          if (active) {
            setError(
              err instanceof Error
                ? err.message
                : 'Failed to fetch credential lifecycle',
            );
          }
        });
    }
    return () => {
      active = false;
    };
  }, [initialCredentialId]);

  const handleFetchHistory = async () => {
    if (!lifecycle || isLoadingHistory) return;
    setIsLoadingHistory(true);
    try {
      const history = await issuerApi.getCredentialLifecycleHistory(
        lifecycle.credentialId,
      );
      setHistoryEvents(history);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Failed to fetch credential transition history',
      );
    } finally {
      setIsLoadingHistory(false);
    }
  };

  const handleVerify = async () => {
    if (!lifecycle || isVerifying) return;
    setIsVerifying(true);
    try {
      const res = await issuerApi.verifyCredential(lifecycle.credentialId);
      setVerificationResult(res);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Authoritative verification failed',
      );
    } finally {
      setIsVerifying(false);
    }
  };

  const handleExecuteTransition = async () => {
    if (!lifecycle || !activeModalAction || isMutating) return;

    setIsMutating(true);
    setError(null);

    try {
      let updated: CredentialLifecycleResponse;
      if (activeModalAction === 'suspend') {
        updated = await issuerApi.suspendCredential(
          lifecycle.credentialId,
          actionReason,
        );
      } else if (activeModalAction === 'resume') {
        updated = await issuerApi.resumeCredential(
          lifecycle.credentialId,
          actionReason,
        );
      } else {
        updated = await issuerApi.revokeCredential(
          lifecycle.credentialId,
          actionReason,
        );
      }

      setLifecycle(updated);
      issuerStore.updateStatus(lifecycle.credentialId, updated.status);
      setMutationSuccessMessage(
        updated.message || 'Status updated successfully',
      );
      setActiveModalAction(null);
      setActionReason('');

      // Refresh history if already open
      if (historyEvents !== null) {
        void handleFetchHistory();
      }
    } catch (err) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Failed to execute lifecycle transition';
      setError(msg);
    } finally {
      setIsMutating(false);
    }
  };

  const getStatusBadge = (status: CredentialStatus) => {
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
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/50 dark:text-sky-400">
            <Activity className="h-5 w-5" />
          </div>
          <div>
            <CardTitle>Credential Lifecycle &amp; State Management</CardTitle>
            <CardDescription>
              Authoritative inspection, cryptographic verification, and
              lifecycle transitions (Suspend, Resume, Revoke).
            </CardDescription>
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-6">
        {/* Lookup Bar */}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <Input
              label="Credential ID (UUID)"
              value={credentialId}
              onChange={(e) => setCredentialId(e.target.value)}
              placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
              helperText="Enter the UUID of an issued credential owned by your issuer identity."
              required
            />
          </div>
          <Button
            type="button"
            variant="primary"
            onClick={() => {
              void handleFetchStatus();
            }}
            isLoading={isLoading}
            disabled={!credentialId.trim() || isLoading}
          >
            <Search className="mr-2 h-4 w-4" />
            Fetch Status
          </Button>
        </div>

        {/* Quick Fill from Session Activity */}
        {activities.filter((a) => a.type === 'credential').length > 0 && (
          <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-slate-500 dark:text-slate-400">
            <span>Recent in this session:</span>
            {activities
              .filter((a) => a.type === 'credential')
              .slice(0, 3)
              .map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => {
                    setCredentialId(item.id);
                    void handleFetchStatus(item.id);
                  }}
                  className="rounded-sm border border-slate-200 bg-slate-100 px-2 py-0.5 font-mono text-[11px] text-slate-700 hover:bg-slate-200 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
                >
                  {item.title || item.id.slice(0, 8)}...
                </button>
              ))}
          </div>
        )}

        {/* Errors & Notifications */}
        {error && (
          <Alert variant="danger" title="Operation Error">
            {error}
          </Alert>
        )}

        {mutationSuccessMessage && (
          <Alert variant="success" title="State Updated">
            {mutationSuccessMessage}
          </Alert>
        )}

        {/* Lifecycle Details Panel */}
        {lifecycle && (
          <div className="space-y-6 rounded-lg border border-slate-200 bg-slate-50/50 p-5 dark:border-slate-800 dark:bg-slate-900/50">
            {/* Header info */}
            <div className="flex flex-col gap-3 border-b border-slate-200 pb-4 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
              <div>
                <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                  Current Lifecycle Status
                </span>
                <div className="mt-1 flex items-center gap-3">
                  {getStatusBadge(lifecycle.status)}
                  <span className="text-xs text-slate-500 dark:text-slate-400">
                    Version: {lifecycle.version}
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    void handleVerify();
                  }}
                  isLoading={isVerifying}
                >
                  <ShieldCheck className="mr-1.5 h-4 w-4" />
                  Verify Signature
                </Button>

                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    void handleFetchHistory();
                  }}
                  isLoading={isLoadingHistory}
                >
                  <History className="mr-1.5 h-4 w-4" />
                  View History
                </Button>
              </div>
            </div>

            {/* Metadata Grid */}
            <div className="grid grid-cols-1 gap-4 text-xs sm:grid-cols-2">
              <div>
                <span className="font-semibold text-slate-500 dark:text-slate-400">
                  Credential ID:
                </span>
                <div className="mt-0.5 flex items-center gap-1.5">
                  <span className="font-mono text-slate-900 dark:text-slate-100">
                    {lifecycle.credentialId}
                  </span>
                  <CopyToClipboard
                    value={lifecycle.credentialId}
                    label="Copy Credential ID"
                  />
                </div>
              </div>

              <div>
                <span className="font-semibold text-slate-500 dark:text-slate-400">
                  Issuer Identity:
                </span>
                <p className="mt-0.5 font-mono text-slate-900 dark:text-slate-100">
                  {lifecycle.issuerId}
                </p>
              </div>

              <div>
                <span className="font-semibold text-slate-500 dark:text-slate-400">
                  Issued Timestamp:
                </span>
                <p className="mt-0.5 text-slate-800 dark:text-slate-200">
                  {lifecycle.issuedAt
                    ? new Date(lifecycle.issuedAt).toLocaleString()
                    : 'N/A'}
                </p>
              </div>

              <div>
                <span className="font-semibold text-slate-500 dark:text-slate-400">
                  Expiration:
                </span>
                <p className="mt-0.5 text-slate-800 dark:text-slate-200">
                  {lifecycle.expiresAt
                    ? new Date(lifecycle.expiresAt).toLocaleString()
                    : 'Permanent (No Expiration)'}
                </p>
              </div>

              {lifecycle.suspendedAt && (
                <div className="rounded-md bg-amber-50 p-2.5 text-amber-900 sm:col-span-2 dark:bg-amber-950/30 dark:text-amber-200">
                  <strong>Suspended At:</strong>{' '}
                  {new Date(lifecycle.suspendedAt).toLocaleString()}
                  {lifecycle.suspensionReason && (
                    <div className="mt-1">
                      <strong>Reason:</strong> {lifecycle.suspensionReason}
                    </div>
                  )}
                </div>
              )}

              {lifecycle.revokedAt && (
                <div className="rounded-md bg-red-50 p-2.5 text-red-900 sm:col-span-2 dark:bg-red-950/30 dark:text-red-200">
                  <strong>Revoked At:</strong>{' '}
                  {new Date(lifecycle.revokedAt).toLocaleString()}
                  {lifecycle.revocationReason && (
                    <div className="mt-1">
                      <strong>Reason:</strong> {lifecycle.revocationReason}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Authoritative Verification Result Alert */}
            {verificationResult && (
              <Alert
                variant={verificationResult.verified ? 'success' : 'danger'}
                title={
                  verificationResult.verified
                    ? 'Authoritative Verification Passed'
                    : 'Verification Failed'
                }
              >
                {verificationResult.verified
                  ? `Cryptographic signature verified against the Platform Key Authority. Current Status: ${verificationResult.status || lifecycle.status}`
                  : verificationResult.message || 'Signature check failed.'}
              </Alert>
            )}

            {/* Transition Action Buttons */}
            <div className="flex flex-wrap items-center justify-end gap-3 border-t border-slate-200 pt-4 dark:border-slate-800">
              {/* Suspend Button */}
              {lifecycle.status === 'ACTIVE' && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setActiveModalAction('suspend');
                    setActionReason('');
                  }}
                  className="text-amber-600 hover:border-amber-500 hover:text-amber-700 dark:text-amber-400"
                >
                  <PauseCircle className="mr-1.5 h-4 w-4" />
                  Suspend Credential
                </Button>
              )}

              {/* Resume Button */}
              {lifecycle.status === 'SUSPENDED' && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setActiveModalAction('resume');
                    setActionReason('');
                  }}
                  className="text-emerald-600 hover:border-emerald-500 hover:text-emerald-700 dark:text-emerald-400"
                >
                  <PlayCircle className="mr-1.5 h-4 w-4" />
                  Resume Credential
                </Button>
              )}

              {/* Revoke Button */}
              {(lifecycle.status === 'ACTIVE' ||
                lifecycle.status === 'SUSPENDED') && (
                <Button
                  type="button"
                  variant="destructive"
                  onClick={() => {
                    setActiveModalAction('revoke');
                    setActionReason('');
                  }}
                >
                  <XCircle className="mr-1.5 h-4 w-4" />
                  Revoke Credential
                </Button>
              )}
            </div>

            {/* Immutable Transition History Timeline */}
            {historyEvents && (
              <div className="space-y-3 border-t border-slate-200 pt-5 dark:border-slate-800">
                <h4 className="flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-slate-100">
                  <History className="h-4 w-4 text-slate-500" />
                  Lifecycle Transition History ({historyEvents.length})
                </h4>

                {historyEvents.length === 0 ? (
                  <p className="text-xs text-slate-500">
                    No state transitions recorded yet.
                  </p>
                ) : (
                  <div className="relative space-y-4 border-l-2 border-slate-200 pl-4 dark:border-slate-700">
                    {historyEvents.map((event, idx) => (
                      <div key={event.id || idx} className="relative">
                        <div className="absolute top-1 -left-[21px] h-2.5 w-2.5 rounded-full bg-indigo-600 dark:bg-indigo-400" />
                        <div className="text-xs">
                          <span className="font-semibold text-slate-900 dark:text-slate-100">
                            {event.eventType}
                          </span>{' '}
                          <span className="text-slate-500 dark:text-slate-400">
                            ({event.fromStatus} → {event.toStatus})
                          </span>
                          <p className="text-slate-500 dark:text-slate-400">
                            {new Date(event.timestamp).toLocaleString()} by{' '}
                            <span className="font-mono">{event.actor}</span> (
                            {event.actorRole})
                          </p>
                          {event.reason && (
                            <p className="mt-1 rounded-sm bg-slate-100 p-1.5 text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                              Reason: {event.reason}
                            </p>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* Transition Confirmation Dialog */}
        <Dialog
          isOpen={activeModalAction !== null}
          onClose={() => !isMutating && setActiveModalAction(null)}
          title={
            <div className="flex items-center gap-2">
              <AlertTriangle
                className={`h-5 w-5 ${
                  activeModalAction === 'revoke'
                    ? 'text-red-600'
                    : 'text-amber-600'
                }`}
              />
              <span className="capitalize">
                Confirm {activeModalAction} Credential
              </span>
            </div>
          }
          footer={
            <>
              <Button
                variant="outline"
                onClick={() => setActiveModalAction(null)}
                disabled={isMutating}
              >
                Cancel
              </Button>
              <Button
                variant={
                  activeModalAction === 'revoke' ? 'destructive' : 'primary'
                }
                onClick={() => {
                  void handleExecuteTransition();
                }}
                isLoading={isMutating}
              >
                Confirm {activeModalAction}
              </Button>
            </>
          }
        >
          <div className="space-y-4 text-sm text-slate-700 dark:text-slate-300">
            {activeModalAction === 'revoke' ? (
              <Alert variant="danger" title="Irreversible Action">
                Revoking this credential is permanent and cannot be undone. Any
                future verifications or zero-knowledge predicate evaluations
                will immediately report this credential as invalid.
              </Alert>
            ) : (
              <p>
                Are you sure you want to {activeModalAction} this credential?
                You can provide an optional audit reason below.
              </p>
            )}

            <TextArea
              label="Transition Audit Reason (Optional)"
              value={actionReason}
              onChange={(e) => setActionReason(e.target.value)}
              placeholder="e.g. Identity re-verification required, employee offboarding, etc."
              rows={3}
            />
          </div>
        </Dialog>
      </CardContent>
    </Card>
  );
};
