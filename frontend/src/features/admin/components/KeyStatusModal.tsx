import React, { useState } from 'react';
import { RefreshCw, AlertTriangle } from 'lucide-react';
import { Dialog } from '../../../components/feedback/Dialog';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { Select } from '../../../components/common/Select';
import { Alert } from '../../../components/feedback/Alert';
import { Badge } from '../../../components/common/Badge';
import { adminApi } from '../../../api/adminApi';
import type { KeyMetadataResponse, KeyStatus } from '../../../api/types';

interface KeyStatusModalProps {
  keyMetadata: KeyMetadataResponse | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const KeyStatusModalContent: React.FC<{
  keyMetadata: KeyMetadataResponse;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}> = ({ keyMetadata, isOpen, onClose, onSuccess }) => {
  const [targetStatus, setTargetStatus] = useState<KeyStatus>(keyMetadata.status);
  const [reason, setReason] = useState('');
  const [replacementKeyId, setReplacementKeyId] = useState(
    keyMetadata.replacementKeyId || '',
  );
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const allowedTransitions: Record<KeyStatus, KeyStatus[]> = {
    ACTIVE: ['ACTIVE', 'RETIRING', 'RETIRED', 'REVOKED'],
    RETIRING: ['RETIRING', 'RETIRED', 'REVOKED'],
    RETIRED: ['RETIRED', 'REVOKED'],
    REVOKED: ['REVOKED'],
  };

  const validNextStates = allowedTransitions[keyMetadata.status] || [];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading) return;
    setError(null);

    if (
      targetStatus === keyMetadata.status &&
      !reason.trim() &&
      !replacementKeyId.trim()
    ) {
      onClose();
      return;
    }

    if (keyMetadata.status === 'REVOKED') {
      setError('Cannot modify status of a permanently REVOKED key.');
      return;
    }

    setIsLoading(true);
    try {
      await adminApi.changeKeyStatus(keyMetadata.keyId, {
        status: targetStatus,
        reason: reason.trim() || undefined,
        replacementKeyId: replacementKeyId.trim() || undefined,
      });
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Status update failed';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={
        <div className="flex items-center gap-2">
          <RefreshCw
            className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
            aria-hidden="true"
          />
          <span>Update Key Lifecycle State: {keyMetadata.keyId}</span>
        </div>
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isLoading}>
            Cancel
          </Button>
          <Button
            variant={targetStatus === 'REVOKED' ? 'destructive' : 'primary'}
            onClick={(e) => void handleSubmit(e)}
            isLoading={isLoading}
          >
            Update Lifecycle Status
          </Button>
        </>
      }
    >
      <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
        <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/50">
          <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
            Current Status:
          </span>
          <Badge
            variant={
              keyMetadata.status === 'ACTIVE'
                ? 'success'
                : keyMetadata.status === 'REVOKED'
                  ? 'danger'
                  : 'warning'
            }
          >
            {keyMetadata.status}
          </Badge>
        </div>

        {targetStatus === 'REVOKED' && (
          <Alert variant="danger" title="Permanent Compromise Action">
            <div className="flex items-start gap-2">
              <AlertTriangle
                className="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400"
                aria-hidden="true"
              />
              <span>
                REVOKED is a terminal cryptographic lifecycle state. Revoking a key
                permanently invalidates all future signature trust and cannot be undone.
              </span>
            </div>
          </Alert>
        )}

        {error && (
          <Alert variant="danger" title="Lifecycle Modification Error">
            {error}
          </Alert>
        )}

        <Select
          label="Target Status"
          value={targetStatus}
          onChange={(e) => setTargetStatus(e.target.value as KeyStatus)}
          disabled={keyMetadata.status === 'REVOKED'}
          required
          options={validNextStates.map((st) => ({
            value: st,
            label: st,
          }))}
        />

        <Input
          label="Transition Reason / Audit Code"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="e.g. Scheduled rotation / Suspected compromise"
          maxLength={512}
          helperText="Brief explanation of the state modification for immutable security logs"
        />

        <Input
          label="Replacement Key ID (Optional)"
          value={replacementKeyId}
          onChange={(e) => setReplacementKeyId(e.target.value)}
          placeholder="e.g. platform-2027-v2"
          maxLength={128}
          helperText="Key ID that supersedes this key for active operations"
        />
      </form>
    </Dialog>
  );
};

export const KeyStatusModal: React.FC<KeyStatusModalProps> = ({
  keyMetadata,
  isOpen,
  onClose,
  onSuccess,
}) => {
  if (!isOpen || !keyMetadata) return null;

  return (
    <KeyStatusModalContent
      key={keyMetadata.keyId}
      keyMetadata={keyMetadata}
      isOpen={isOpen}
      onClose={onClose}
      onSuccess={onSuccess}
    />
  );
};
