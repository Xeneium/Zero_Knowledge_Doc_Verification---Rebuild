import React, { useState } from 'react';
import { ConfirmationModal } from '../../../components/feedback/ConfirmationModal';
import { Alert } from '../../../components/feedback/Alert';
import { holderApi } from '../../../api/holderApi';
import { holderStore } from '../../../state/holderStore';
import type { HolderCredentialResponse } from '../../../api/types';

export type HolderStatusAction = 'suspend' | 'activate' | 'revoke';

interface HolderStatusActionModalProps {
  isOpen: boolean;
  onClose: () => void;
  credential: HolderCredentialResponse | null;
  action: HolderStatusAction | null;
  onSuccess?: (updated: HolderCredentialResponse) => void;
}

export const HolderStatusActionModal: React.FC<
  HolderStatusActionModalProps
> = ({ isOpen, onClose, credential, action, onSuccess }) => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!credential || !action) return null;

  const actionConfig = {
    suspend: {
      title: 'Suspend Credential',
      description:
        'Temporarily deactivate this credential. Verifiers will receive a suspended verification status until you reactivate it.',
      confirmLabel: 'Confirm Suspension',
      variant: 'warning' as const,
    },
    activate: {
      title: 'Reactivate Credential',
      description:
        'Re-enable this credential for active zero-knowledge proof generation and verifier capability checks.',
      confirmLabel: 'Confirm Activation',
      variant: 'primary' as const,
    },
    revoke: {
      title: 'Permanently Revoke Credential',
      description:
        'Permanently invalidate this credential. Once revoked, this status is terminal and cannot be reversed.',
      confirmLabel: 'Confirm Revocation',
      variant: 'danger' as const,
    },
  }[action];

  const handleConfirm = async () => {
    setIsLoading(true);
    setError(null);

    try {
      let updated: HolderCredentialResponse;
      if (action === 'suspend') {
        updated = await holderApi.suspendCredential(credential.id);
      } else if (action === 'activate') {
        updated = await holderApi.activateCredential(credential.id);
      } else {
        updated = await holderApi.revokeCredential(credential.id);
      }

      holderStore.updateCredentialStatus(
        updated.id,
        updated.status,
        updated.version,
      );
      onSuccess?.(updated);
      onClose();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : `Failed to ${action} credential`,
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <ConfirmationModal
      isOpen={isOpen}
      onClose={() => {
        if (!isLoading) {
          setError(null);
          onClose();
        }
      }}
      onConfirm={() => void handleConfirm()}
      title={actionConfig.title}
      message={
        <div className="space-y-3">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            {actionConfig.description}
          </p>

          {error && (
            <Alert variant="danger" title="Action Failed">
              {error}
            </Alert>
          )}

          <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300">
            <p>
              <strong>Holder Record ID:</strong>{' '}
              <span className="font-mono">{credential.id}</span>
            </p>
            <p>
              <strong>Credential UUID:</strong>{' '}
              <span className="font-mono">{credential.credentialId}</span>
            </p>
            <p>
              <strong>Current State:</strong> {credential.status} (Version{' '}
              {credential.version})
            </p>
            <p>
              <strong>Target State:</strong>{' '}
              <span className="font-semibold uppercase">
                {action === 'activate' ? 'ACTIVE' : action.toUpperCase() + 'ED'}
              </span>
            </p>
          </div>

          {action === 'revoke' && (
            <Alert variant="warning" title="Irreversible Action">
              Revocation is cryptographically permanent. You will not be able to
              reactivate this credential or generate proofs for it.
            </Alert>
          )}
        </div>
      }
      confirmText={actionConfig.confirmLabel}
      variant={actionConfig.variant}
      isLoading={isLoading}
    />
  );
};
