import React, { useState } from 'react';
import { RotateCw, ShieldCheck } from 'lucide-react';
import { Dialog } from '../../../components/feedback/Dialog';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { Alert } from '../../../components/feedback/Alert';
import { adminApi } from '../../../api/adminApi';
import type { KeyMetadataResponse } from '../../../api/types';

interface KeyRotateModalProps {
  keyMetadata: KeyMetadataResponse | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const KeyRotateModalContent: React.FC<{
  keyMetadata: KeyMetadataResponse;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}> = ({ keyMetadata, isOpen, onClose, onSuccess }) => {
  const [replacementKeyId, setReplacementKeyId] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading) return;
    setError(null);

    setIsLoading(true);
    try {
      await adminApi.rotateKey(keyMetadata.keyId, {
        replacementKeyId: replacementKeyId.trim() || undefined,
      });
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Key rotation failed';
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
          <RotateCw
            className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
            aria-hidden="true"
          />
          <span>Rotate Key Anchor: {keyMetadata.keyId}</span>
        </div>
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isLoading}>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={(e) => void handleSubmit(e)}
            isLoading={isLoading}
          >
            Execute Key Rotation
          </Button>
        </>
      }
    >
      <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
        <Alert variant="info" title="Graceful Rotation Lifecycle">
          <div className="flex items-start gap-2">
            <ShieldCheck
              className="mt-0.5 h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400"
              aria-hidden="true"
            />
            <span>
              Rotating a key transitions the current key to RETIRING or RETIRED status
              and binds it to a designated replacement key. Previously issued
              artifacts remain verifiable.
            </span>
          </div>
        </Alert>

        {error && (
          <Alert variant="danger" title="Rotation Failed">
            {error}
          </Alert>
        )}

        <Input
          label="Replacement Key Identifier"
          value={replacementKeyId}
          onChange={(e) => setReplacementKeyId(e.target.value)}
          placeholder="e.g. issuer-alpha-2027-v2"
          maxLength={128}
          helperText="Identifier of the newly registered active key that will take over signing"
          required
        />
      </form>
    </Dialog>
  );
};

export const KeyRotateModal: React.FC<KeyRotateModalProps> = ({
  keyMetadata,
  isOpen,
  onClose,
  onSuccess,
}) => {
  if (!isOpen || !keyMetadata) return null;

  return (
    <KeyRotateModalContent
      key={keyMetadata.keyId}
      keyMetadata={keyMetadata}
      isOpen={isOpen}
      onClose={onClose}
      onSuccess={onSuccess}
    />
  );
};
