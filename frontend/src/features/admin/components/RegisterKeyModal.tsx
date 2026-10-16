import React, { useState } from 'react';
import { KeyRound, ShieldAlert } from 'lucide-react';
import { Dialog } from '../../../components/feedback/Dialog';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { TextArea } from '../../../components/common/TextArea';
import { Select } from '../../../components/common/Select';
import { Alert } from '../../../components/feedback/Alert';
import { adminApi } from '../../../api/adminApi';
import type {
  KeyPurpose,
  KeyOwnerType,
  KeyStatus,
  KeyRegistrationRequest,
} from '../../../api/types';

interface RegisterKeyModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const RegisterKeyModal: React.FC<RegisterKeyModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [keyId, setKeyId] = useState('');
  const [purpose, setPurpose] = useState<KeyPurpose>('CREDENTIAL_SIGNING');
  const [ownerId, setOwnerId] = useState('platform');
  const [ownerType, setOwnerType] = useState<KeyOwnerType>('PLATFORM');
  const [algorithm, setAlgorithm] = useState('RSASSA-PSS');
  const [status, setStatus] = useState<KeyStatus>('ACTIVE');
  const [publicKeyPem, setPublicKeyPem] = useState('');

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading) return;
    setError(null);

    const trimmedKeyId = keyId.trim();
    const trimmedPem = publicKeyPem.trim();

    if (!trimmedKeyId) {
      setError('Key ID must not be blank');
      return;
    }

    // Zero Private Key Policy: intercept and reject private key material immediately
    if (
      trimmedPem.toUpperCase().includes('PRIVATE KEY') ||
      trimmedPem.toUpperCase().includes('-----BEGIN RSA PRIVATE KEY-----')
    ) {
      setPublicKeyPem('');
      setError(
        'Security Violation: Private key material detected! ZKDoc enforces a strict Zero Private Key Policy. Never paste, transmit, or register private keys.',
      );
      return;
    }

    if (!trimmedPem.includes('-----BEGIN PUBLIC KEY-----')) {
      setError('Public Key must be a valid X.509 PEM formatted public key.');
      return;
    }

    const payload: KeyRegistrationRequest = {
      keyId: trimmedKeyId,
      purpose,
      ownerId: ownerId.trim() || 'platform',
      ownerType,
      algorithm: algorithm.trim() || 'RSASSA-PSS',
      status,
      publicKeyPem: trimmedPem,
    };

    setIsLoading(true);
    try {
      await adminApi.registerKey(payload);
      onSuccess();
      onClose();
      // Reset form
      setKeyId('');
      setPublicKeyPem('');
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : 'Key registration failed';
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
          <KeyRound
            className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
            aria-hidden="true"
          />
          <span>Register Cryptographic Public Key</span>
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
            Register Key Anchor
          </Button>
        </>
      }
    >
      <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
        <Alert variant="info" title="Zero Private Key Policy">
          <div className="flex items-start gap-2">
            <ShieldAlert
              className="mt-0.5 h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400"
              aria-hidden="true"
            />
            <span>
              The trust registry only accepts and verifies public keys. Private
              key material is never accepted, transmitted, or stored by the
              platform.
            </span>
          </div>
        </Alert>

        {error && (
          <Alert variant="danger" title="Registration Error">
            {error}
          </Alert>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label="Key Identifier (Key ID)"
            value={keyId}
            onChange={(e) => setKeyId(e.target.value)}
            placeholder="e.g. issuer-alpha-2026-pss"
            maxLength={128}
            required
          />

          <Select
            label="Cryptographic Purpose"
            value={purpose}
            onChange={(e) => setPurpose(e.target.value as KeyPurpose)}
            required
            options={[
              {
                value: 'CREDENTIAL_SIGNING',
                label: 'CREDENTIAL_SIGNING (Verifiable Credentials)',
              },
              {
                value: 'DOCUMENT_SIGNING',
                label: 'DOCUMENT_SIGNING (Physical Records)',
              },
              {
                value: 'COMMITMENT_ATTESTATION_SIGNING',
                label: 'COMMITMENT_ATTESTATION_SIGNING (Pedersen ZKP)',
              },
              {
                value: 'JWT_SIGNING',
                label: 'JWT_SIGNING (Bearer Authentication)',
              },
            ]}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input
            label="Owner ID"
            value={ownerId}
            onChange={(e) => setOwnerId(e.target.value)}
            placeholder="e.g. platform or issuer-id"
            maxLength={128}
          />

          <Select
            label="Owner Type"
            value={ownerType}
            onChange={(e) => setOwnerType(e.target.value as KeyOwnerType)}
            options={[
              { value: 'PLATFORM', label: 'PLATFORM' },
              { value: 'ISSUER', label: 'ISSUER' },
              { value: 'SERVICE', label: 'SERVICE' },
              { value: 'SECURITY_DOMAIN', label: 'SECURITY_DOMAIN' },
            ]}
          />

          <Select
            label="Initial Status"
            value={status}
            onChange={(e) => setStatus(e.target.value as KeyStatus)}
            options={[
              { value: 'ACTIVE', label: 'ACTIVE (Authorized to Sign)' },
              { value: 'RETIRING', label: 'RETIRING (Rotation Grace)' },
              { value: 'RETIRED', label: 'RETIRED (Historical Only)' },
            ]}
          />
        </div>

        <Input
          label="Signature Algorithm"
          value={algorithm}
          onChange={(e) => setAlgorithm(e.target.value)}
          placeholder="RSASSA-PSS"
          maxLength={64}
        />

        <TextArea
          label="Public Key (X.509 PEM)"
          value={publicKeyPem}
          onChange={(e) => setPublicKeyPem(e.target.value)}
          rows={6}
          className="font-mono text-xs"
          placeholder="-----BEGIN PUBLIC KEY-----&#10;MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...&#10;-----END PUBLIC KEY-----"
          helperText="Standard PEM format starting with -----BEGIN PUBLIC KEY-----"
          required
        />
      </form>
    </Dialog>
  );
};
