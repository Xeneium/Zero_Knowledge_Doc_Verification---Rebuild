import React, { useState } from 'react';
import {
  Activity,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  RefreshCw,
  Database,
  Key,
  Award,
  ShieldCheck,
  RotateCcw,
} from 'lucide-react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardDescription,
} from '../../../components/common/Card';
import { Badge } from '../../../components/common/Badge';
import { Button } from '../../../components/common/Button';
import { Alert } from '../../../components/feedback/Alert';
import { ConfirmationModal } from '../../../components/feedback/ConfirmationModal';
import { adminApi } from '../../../api/adminApi';
import {
  adminStore,
  useAdminRecoveryReport,
  useAdminStore,
} from '../../../state/adminStore';

export const SystemOverviewCard: React.FC = () => {
  const report = useAdminRecoveryReport();
  const { isLoadingRecovery, isReconciling, recoveryError } = useAdminStore();
  const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);
  const [successNotice, setSuccessNotice] = useState<string | null>(null);

  const fetchStatus = async () => {
    adminStore.setLoadingRecovery(true);
    setSuccessNotice(null);
    try {
      const data = await adminApi.getRecoveryStatus();
      adminStore.setRecoveryReport(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch status';
      adminStore.setRecoveryError(msg);
    } finally {
      adminStore.setLoadingRecovery(false);
    }
  };

  const handleExecuteReconciliation = async () => {
    setIsConfirmModalOpen(false);
    adminStore.setIsReconciling(true);
    setSuccessNotice(null);
    try {
      const data = await adminApi.reconcileRecovery();
      adminStore.setRecoveryReport(data);
      setSuccessNotice('System-wide reconciliation completed successfully.');
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : 'Reconciliation execution failed';
      adminStore.setRecoveryError(msg);
    } finally {
      adminStore.setIsReconciling(false);
    }
  };

  const statusVariant = (status: string) => {
    switch (status) {
      case 'HEALTHY':
        return 'success' as const;
      case 'WARNING':
        return 'warning' as const;
      default:
        return 'danger' as const;
    }
  };

  return (
    <Card className="shadow-xs">
      <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Activity
              className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
              aria-hidden="true"
            />
            <CardTitle>Platform Integrity & Recovery Overview</CardTitle>
          </div>
          <CardDescription>
            Authoritative post-restoration health status across storage, schema,
            cryptographic keys, credentials, and audit hash chains.
          </CardDescription>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void fetchStatus()}
            isLoading={isLoadingRecovery}
            aria-label="Refresh recovery status"
          >
            <RefreshCw className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
            Refresh Status
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsConfirmModalOpen(true)}
            isLoading={isReconciling}
            aria-label="Run post-recovery reconciliation"
          >
            <RotateCcw className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
            Reconcile Platform
          </Button>
        </div>
      </CardHeader>

      <CardContent className="space-y-6">
        {recoveryError && (
          <Alert variant="danger" title="Status Operation Failed">
            {recoveryError}
          </Alert>
        )}

        {successNotice && (
          <Alert variant="success" title="Reconciliation Complete">
            {successNotice}
          </Alert>
        )}

        {report ? (
          <>
            {/* Status Summary Banner */}
            <div className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
              <div className="flex items-center gap-3">
                {report.status === 'HEALTHY' && (
                  <CheckCircle2
                    className="h-6 w-6 text-emerald-600 dark:text-emerald-400"
                    aria-hidden="true"
                  />
                )}
                {report.status === 'WARNING' && (
                  <AlertTriangle
                    className="h-6 w-6 text-amber-600 dark:text-amber-400"
                    aria-hidden="true"
                  />
                )}
                {report.status === 'CORRUPTED' && (
                  <XCircle
                    className="h-6 w-6 text-red-600 dark:text-red-400"
                    aria-hidden="true"
                  />
                )}
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-900 dark:text-slate-100">
                      Overall State:
                    </span>
                    <Badge variant={statusVariant(report.status)}>
                      {report.status}
                    </Badge>
                  </div>
                  <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                    Report Timestamp:{' '}
                    {new Date(report.timestamp).toLocaleString()}
                  </p>
                </div>
              </div>

              <div className="text-right text-xs text-slate-600 dark:text-slate-300">
                <span className="font-medium text-slate-900 dark:text-slate-100">
                  Summary:
                </span>{' '}
                {report.summary}
              </div>
            </div>

            {/* Sub-system Metrics Grid */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {/* Database Schema */}
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Schema Integrity
                  </span>
                  <Database
                    className="h-4 w-4 text-slate-400"
                    aria-hidden="true"
                  />
                </div>
                <div className="mt-2 flex items-baseline gap-2">
                  <Badge variant={report.schemaValid ? 'success' : 'danger'}>
                    {report.schemaValid ? 'VALID' : 'INVALID'}
                  </Badge>
                  <span className="text-xs text-slate-500">
                    v{report.schemaVersion}
                  </span>
                </div>
              </div>

              {/* Artifacts Storage */}
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Document Artifacts
                  </span>
                  <Activity
                    className="h-4 w-4 text-slate-400"
                    aria-hidden="true"
                  />
                </div>
                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-xl font-bold text-slate-900 dark:text-slate-100">
                    {report.validArtifacts} / {report.totalArtifactsChecked}
                  </span>
                  <span className="text-xs text-slate-500">Valid</span>
                </div>
                {(report.missingArtifacts.length > 0 ||
                  report.corruptedArtifacts.length > 0) && (
                  <p className="mt-1 text-xs text-red-600 dark:text-red-400">
                    {report.missingArtifacts.length} Missing,{' '}
                    {report.corruptedArtifacts.length} Corrupted
                  </p>
                )}
              </div>

              {/* Trust Keys */}
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Key Registry
                  </span>
                  <Key className="h-4 w-4 text-slate-400" aria-hidden="true" />
                </div>
                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-xl font-bold text-slate-900 dark:text-slate-100">
                    {report.activeKeys}
                  </span>
                  <span className="text-xs text-slate-500">
                    Active / {report.totalKeysChecked} Total
                  </span>
                </div>
                {report.invalidKeys.length > 0 && (
                  <p className="mt-1 text-xs text-red-600 dark:text-red-400">
                    {report.invalidKeys.length} Invalid Keys Detected
                  </p>
                )}
              </div>

              {/* Audit Hash Chain */}
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Audit Hash Chain
                  </span>
                  <ShieldCheck
                    className="h-4 w-4 text-slate-400"
                    aria-hidden="true"
                  />
                </div>
                <div className="mt-2 flex items-baseline gap-2">
                  <Badge variant={report.auditChainValid ? 'success' : 'danger'}>
                    {report.auditChainValid ? 'UNBROKEN' : 'CORRUPTED'}
                  </Badge>
                  <span className="text-xs text-slate-500">
                    {report.totalAuditEvents} Events
                  </span>
                </div>
                {report.auditFailureReason && (
                  <p className="mt-1 text-xs text-red-600 dark:text-red-400">
                    {report.auditFailureReason}
                  </p>
                )}
              </div>
            </div>

            {/* Credential Statistics Banner */}
            <div className="flex items-center justify-between rounded-lg border border-slate-200 p-4 dark:border-slate-800">
              <div className="flex items-center gap-3">
                <Award
                  className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
                  aria-hidden="true"
                />
                <div>
                  <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                    Verifiable Credentials Status
                  </h4>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    {report.validCredentials} valid credentials verified out of{' '}
                    {report.totalCredentialsChecked} checked records.
                  </p>
                </div>
              </div>
              <div>
                {report.invalidCredentials.length > 0 ? (
                  <Badge variant="danger">
                    {report.invalidCredentials.length} Invalid
                  </Badge>
                ) : (
                  <Badge variant="success">All Valid</Badge>
                )}
              </div>
            </div>
          </>
        ) : (
          <div className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
            Click &quot;Refresh Status&quot; to inspect current platform
            reconciliation metrics.
          </div>
        )}
      </CardContent>

      {/* Confirmation Modal for Reconciliation Execution */}
      <ConfirmationModal
        isOpen={isConfirmModalOpen}
        onClose={() => setIsConfirmModalOpen(false)}
        onConfirm={() => void handleExecuteReconciliation()}
        title="Execute Platform Reconciliation"
        message={
          <div className="space-y-3">
            <p>
              Are you sure you want to trigger a full system reconciliation?
            </p>
            <ul className="list-disc space-y-1 pl-5 text-xs text-slate-600 dark:text-slate-400">
              <li>
                Validates database schema version and Flyway migration state.
              </li>
              <li>
                Audits all stored physical document digests against SHA-256
                fingerprints.
              </li>
              <li>
                Checks cryptographic key fingerprints and public key registry
                integrity.
              </li>
              <li>
                Verifies all credential RSASSA-PSS digital signatures.
              </li>
              <li>
                Validates the entire continuous SHA-256 security audit hash
                chain.
              </li>
              <li>
                Records an authoritative <code>ADMIN_SECURITY_OPERATION</code>{' '}
                event in the immutable audit log.
              </li>
            </ul>
          </div>
        }
        confirmText="Execute Reconciliation"
        variant="warning"
        isLoading={isReconciling}
      />
    </Card>
  );
};
