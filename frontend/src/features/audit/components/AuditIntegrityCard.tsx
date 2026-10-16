import React, { useState } from 'react';
import { ShieldCheck, ShieldAlert, CheckCircle2, XCircle } from 'lucide-react';
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
import { adminApi } from '../../../api/adminApi';
import {
  adminStore,
  useAdminStore,
} from '../../../state/adminStore';
import type { AuditIntegrityResult } from '../../../api/types';

export const AuditIntegrityCard: React.FC = () => {
  const { auditIntegrity, isVerifyingAudit } = useAdminStore();
  const [startSeq, setStartSeq] = useState<string>('1');
  const [endSeq, setEndSeq] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const start = startSeq ? parseInt(startSeq, 10) : undefined;
    const end = endSeq ? parseInt(endSeq, 10) : undefined;

    adminStore.setIsVerifyingAudit(true);
    try {
      const res: AuditIntegrityResult = await adminApi.verifyAuditIntegrity(
        start,
        end,
      );
      adminStore.setAuditIntegrity(res);
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Failed to verify audit chain integrity';
      setError(msg);
      adminStore.setAuditIntegrity(null);
    } finally {
      adminStore.setIsVerifyingAudit(false);
    }
  };

  return (
    <Card className="shadow-xs">
      <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <ShieldCheck
              className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
              aria-hidden="true"
            />
            <CardTitle>Audit Trail Hash Chain Integrity Verification</CardTitle>
          </div>
          <CardDescription>
            Validates continuous SHA-256 cryptographic linkage across all stored
            security audit events to detect tampering or record deletion.
          </CardDescription>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {error && (
          <Alert variant="danger" title="Verification Execution Failed">
            {error}
          </Alert>
        )}

        {auditIntegrity && (
          <div className="flex items-center gap-4 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
            {auditIntegrity.valid ? (
              <CheckCircle2
                className="h-7 w-7 shrink-0 text-emerald-600 dark:text-emerald-400"
                aria-hidden="true"
              />
            ) : (
              <XCircle
                className="h-7 w-7 shrink-0 text-red-600 dark:text-red-400"
                aria-hidden="true"
              />
            )}
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="font-semibold text-slate-900 dark:text-slate-100">
                  Cryptographic Chain Status:
                </span>
                <Badge variant={auditIntegrity.valid ? 'success' : 'danger'}>
                  {auditIntegrity.valid
                    ? 'UNBROKEN & VERIFIED'
                    : 'CHAIN INTEGRITY COMPROMISED'}
                </Badge>
              </div>
              <p className="text-xs text-slate-600 dark:text-slate-400">
                Audited <strong>{auditIntegrity.checkedCount}</strong> sequential
                events.
                {!auditIntegrity.valid && auditIntegrity.tamperedSequence && (
                  <span className="ml-1 text-red-600 dark:text-red-400">
                    Tampering detected at sequence #
                    {auditIntegrity.tamperedSequence} (
                    {auditIntegrity.failureReason})
                  </span>
                )}
              </p>
            </div>
          </div>
        )}

        <form onSubmit={(e) => void handleVerify(e)} className="flex flex-wrap items-end gap-3">
          <div className="w-32">
            <Input
              label="Start Seq #"
              type="number"
              min="1"
              value={startSeq}
              onChange={(e) => setStartSeq(e.target.value)}
              placeholder="1"
            />
          </div>

          <div className="w-32">
            <Input
              label="End Seq #"
              type="number"
              min="1"
              value={endSeq}
              onChange={(e) => setEndSeq(e.target.value)}
              placeholder="Latest"
            />
          </div>

          <Button
            type="submit"
            variant="primary"
            isLoading={isVerifyingAudit}
            aria-label="Verify audit hash chain"
          >
            <ShieldAlert className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
            Verify Hash Chain
          </Button>
        </form>
      </CardContent>
    </Card>
  );
};
