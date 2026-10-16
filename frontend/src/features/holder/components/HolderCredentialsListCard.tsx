import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Badge } from '../../../components/common/Badge';
import { Input } from '../../../components/common/Input';
import { Alert } from '../../../components/feedback/Alert';
import { Spinner } from '../../../components/feedback/Spinner';
import { EmptyState } from '../../../components/feedback/EmptyState';
import { FormattedHash } from '../../../components/data/FormattedHash';
import { holderApi } from '../../../api/holderApi';
import {
  holderStore,
  useHolderState,
  type HolderFilterStatus,
} from '../../../state/holderStore';
import type { HolderCredentialResponse } from '../../../api/types';
import { HolderCredentialDetailModal } from './HolderCredentialDetailModal';
import {
  HolderStatusActionModal,
  type HolderStatusAction,
} from './HolderStatusActionModal';
import {
  Wallet,
  RefreshCw,
  Search,
  Eye,
  FileKey,
  QrCode,
  PauseCircle,
  PlayCircle,
  XCircle,
} from 'lucide-react';

interface HolderCredentialsListCardProps {
  onNavigateToProvision?: () => void;
  onNavigateToProof?: (holderCredentialId: string) => void;
  onNavigateToCapability?: (credentialId: string) => void;
}

export const HolderCredentialsListCard: React.FC<
  HolderCredentialsListCardProps
> = ({ onNavigateToProvision, onNavigateToProof, onNavigateToCapability }) => {
  const { credentials, isLoading, error, filterStatus, searchQuery } =
    useHolderState();

  const [selectedForDetail, setSelectedForDetail] =
    useState<HolderCredentialResponse | null>(null);
  const [statusModalCredential, setStatusModalCredential] =
    useState<HolderCredentialResponse | null>(null);
  const [statusModalAction, setStatusModalAction] =
    useState<HolderStatusAction | null>(null);

  const fetchCredentials = useCallback(async () => {
    holderStore.setLoading(true);
    holderStore.setError(null);
    try {
      const data = await holderApi.listCredentials();
      holderStore.setCredentials(data);
    } catch (err) {
      holderStore.setError(
        err instanceof Error
          ? err.message
          : 'Failed to load credentials from wallet',
      );
    }
  }, []);

  useEffect(() => {
    void fetchCredentials();
  }, [fetchCredentials]);

  // Dynamic metrics computed strictly from real records
  const stats = useMemo(() => {
    const total = credentials.length;
    const active = credentials.filter((c) => c.status === 'ACTIVE').length;
    const suspended = credentials.filter(
      (c) => c.status === 'SUSPENDED',
    ).length;
    const revoked = credentials.filter((c) => c.status === 'REVOKED').length;
    return { total, active, suspended, revoked };
  }, [credentials]);

  // Filtered & searched records
  const filteredCredentials = useMemo(() => {
    return credentials.filter((cred) => {
      // 1. Status Filter
      if (filterStatus !== 'ALL' && cred.status !== filterStatus) {
        return false;
      }

      // 2. Search Query
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase().trim();
        const digest = (
          cred.credentialDigestHex ||
          cred.credentialDigest ||
          ''
        ).toLowerCase();
        const credId = cred.credentialId.toLowerCase();
        const id = cred.id.toLowerCase();
        return (
          credId.includes(query) || digest.includes(query) || id.includes(query)
        );
      }

      return true;
    });
  }, [credentials, filterStatus, searchQuery]);

  const handleStatusAction = (
    credential: HolderCredentialResponse,
    action: HolderStatusAction,
  ) => {
    setStatusModalCredential(credential);
    setStatusModalAction(action);
  };

  const getStatusBadge = (status: string) => {
    switch (status.toUpperCase()) {
      case 'ACTIVE':
        return <Badge variant="success">ACTIVE</Badge>;
      case 'SUSPENDED':
        return <Badge variant="warning">SUSPENDED</Badge>;
      case 'REVOKED':
        return <Badge variant="danger">REVOKED</Badge>;
      default:
        return <Badge variant="neutral">{status}</Badge>;
    }
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader>
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
          <div className="flex items-center gap-2">
            <Wallet className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
            <CardTitle>My Credential Wallet</CardTitle>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void fetchCredentials()}
            disabled={isLoading}
            className="min-h-[44px]"
          >
            <RefreshCw
              className={`mr-1.5 h-4 w-4 ${isLoading ? 'animate-spin' : ''}`}
            />
            Refresh Wallet
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Real Summary Metrics */}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-center dark:border-slate-800 dark:bg-slate-900/60">
            <span className="text-xs font-semibold text-slate-500 uppercase dark:text-slate-400">
              Total Wallet Items
            </span>
            <p className="mt-1 text-2xl font-bold text-slate-900 dark:text-slate-100">
              {stats.total}
            </p>
          </div>
          <div className="rounded-lg border border-emerald-200 bg-emerald-50/50 p-3 text-center dark:border-emerald-950 dark:bg-emerald-950/20">
            <span className="text-xs font-semibold text-emerald-700 uppercase dark:text-emerald-400">
              Active Credentials
            </span>
            <p className="mt-1 text-2xl font-bold text-emerald-800 dark:text-emerald-300">
              {stats.active}
            </p>
          </div>
          <div className="rounded-lg border border-amber-200 bg-amber-50/50 p-3 text-center dark:border-amber-950 dark:bg-amber-950/20">
            <span className="text-xs font-semibold text-amber-700 uppercase dark:text-amber-400">
              Suspended
            </span>
            <p className="mt-1 text-2xl font-bold text-amber-800 dark:text-amber-300">
              {stats.suspended}
            </p>
          </div>
          <div className="rounded-lg border border-rose-200 bg-rose-50/50 p-3 text-center dark:border-rose-950 dark:bg-rose-950/20">
            <span className="text-xs font-semibold text-rose-700 uppercase dark:text-rose-400">
              Revoked
            </span>
            <p className="mt-1 text-2xl font-bold text-rose-800 dark:text-rose-300">
              {stats.revoked}
            </p>
          </div>
        </div>

        {error && (
          <Alert variant="danger" title="Wallet Sync Error">
            {error}
          </Alert>
        )}

        {/* Filter and Search Controls */}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div
            className="flex flex-wrap items-center gap-1.5"
            role="group"
            aria-label="Credential Status Filter"
          >
            {(
              ['ALL', 'ACTIVE', 'SUSPENDED', 'REVOKED'] as HolderFilterStatus[]
            ).map((st) => (
              <Button
                key={st}
                variant={filterStatus === st ? 'primary' : 'outline'}
                size="sm"
                onClick={() => holderStore.setFilterStatus(st)}
                className="min-h-[44px]"
              >
                {st === 'ALL' ? 'All Records' : st}
              </Button>
            ))}
          </div>

          <div className="relative w-full sm:w-64">
            <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <Input
              value={searchQuery}
              onChange={(e) => holderStore.setSearchQuery(e.target.value)}
              placeholder="Search UUID or digest..."
              className="pl-9 text-xs"
              aria-label="Search credentials by UUID or digest"
            />
          </div>
        </div>

        {/* Credentials Table / List */}
        {isLoading && credentials.length === 0 ? (
          <div className="flex flex-col items-center justify-center p-12 text-slate-500">
            <Spinner size="lg" className="mb-3" />
            <p className="text-sm">
              Retrieving provisioned credentials from enclave...
            </p>
          </div>
        ) : filteredCredentials.length === 0 ? (
          <EmptyState
            title={
              credentials.length === 0
                ? 'No Credentials in Wallet'
                : 'No Matching Credentials'
            }
            description={
              credentials.length === 0
                ? 'Your holder wallet has no credentials linked yet. Provision an issued credential using its canonical UUID.'
                : 'No credentials in your wallet match the selected status filter or search query.'
            }
            action={
              credentials.length === 0 && onNavigateToProvision ? (
                <Button
                  variant="primary"
                  onClick={onNavigateToProvision}
                  className="min-h-[44px]"
                >
                  Provision First Credential
                </Button>
              ) : undefined
            }
          />
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3 font-semibold">Credential UUID</th>
                  <th className="px-4 py-3 font-semibold">
                    Status &amp; Version
                  </th>
                  <th className="px-4 py-3 font-semibold">SHA-256 Digest</th>
                  <th className="px-4 py-3 font-semibold">Provisioned</th>
                  <th className="px-4 py-3 text-right font-semibold">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                {filteredCredentials.map((cred) => {
                  const digest =
                    cred.credentialDigestHex || cred.credentialDigest || '';
                  return (
                    <tr
                      key={cred.id}
                      className="hover:bg-slate-50/80 dark:hover:bg-slate-800/40"
                    >
                      <td className="px-4 py-3 font-mono text-slate-900 dark:text-slate-100">
                        <FormattedHash
                          value={cred.credentialId}
                          prefixLength={8}
                          suffixLength={8}
                        />
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          {getStatusBadge(cred.status)}
                          <span className="text-[11px] text-slate-500">
                            v{cred.version}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3 font-mono">
                        <FormattedHash
                          value={digest}
                          prefixLength={8}
                          suffixLength={8}
                        />
                      </td>
                      <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                        {new Date(cred.provisionedAt).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1">
                          {/* Inspect Modal Trigger */}
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setSelectedForDetail(cred)}
                            title="Inspect Credential Details"
                            className="min-h-[36px] min-w-[36px] p-2"
                            aria-label={`Inspect credential ${cred.credentialId}`}
                          >
                            <Eye className="h-4 w-4" />
                          </Button>

                          {/* ZK Proof Generator Trigger */}
                          {cred.status === 'ACTIVE' && onNavigateToProof && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => onNavigateToProof(cred.id)}
                              title="Generate Zero-Knowledge Predicate Proof"
                              className="min-h-[36px] min-w-[36px] p-2 text-indigo-600 dark:text-indigo-400"
                              aria-label={`Generate ZK Proof for ${cred.credentialId}`}
                            >
                              <FileKey className="h-4 w-4" />
                            </Button>
                          )}

                          {/* Capability QR Trigger */}
                          {cred.status === 'ACTIVE' &&
                            onNavigateToCapability && (
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() =>
                                  onNavigateToCapability(cred.credentialId)
                                }
                                title="Mint Capability QR Code"
                                className="min-h-[36px] min-w-[36px] p-2 text-slate-700 dark:text-slate-300"
                                aria-label={`Mint Capability QR for ${cred.credentialId}`}
                              >
                                <QrCode className="h-4 w-4" />
                              </Button>
                            )}

                          {/* Lifecycle Status Action Triggers */}
                          {cred.status === 'ACTIVE' && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() =>
                                handleStatusAction(cred, 'suspend')
                              }
                              title="Suspend Credential"
                              className="min-h-[36px] min-w-[36px] p-2 text-amber-600 dark:text-amber-400"
                              aria-label={`Suspend credential ${cred.credentialId}`}
                            >
                              <PauseCircle className="h-4 w-4" />
                            </Button>
                          )}

                          {cred.status === 'SUSPENDED' && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() =>
                                handleStatusAction(cred, 'activate')
                              }
                              title="Reactivate Credential"
                              className="min-h-[36px] min-w-[36px] p-2 text-emerald-600 dark:text-emerald-400"
                              aria-label={`Reactivate credential ${cred.credentialId}`}
                            >
                              <PlayCircle className="h-4 w-4" />
                            </Button>
                          )}

                          {cred.status !== 'REVOKED' && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleStatusAction(cred, 'revoke')}
                              title="Permanently Revoke Credential"
                              className="min-h-[36px] min-w-[36px] p-2 text-rose-600 dark:text-rose-400"
                              aria-label={`Revoke credential ${cred.credentialId}`}
                            >
                              <XCircle className="h-4 w-4" />
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Modals */}
        <HolderCredentialDetailModal
          isOpen={!!selectedForDetail}
          onClose={() => setSelectedForDetail(null)}
          credential={selectedForDetail}
          onNavigateToProof={onNavigateToProof}
          onNavigateToCapability={onNavigateToCapability}
        />

        <HolderStatusActionModal
          isOpen={!!statusModalAction && !!statusModalCredential}
          onClose={() => {
            setStatusModalAction(null);
            setStatusModalCredential(null);
          }}
          credential={statusModalCredential}
          action={statusModalAction}
        />
      </CardContent>
    </Card>
  );
};
