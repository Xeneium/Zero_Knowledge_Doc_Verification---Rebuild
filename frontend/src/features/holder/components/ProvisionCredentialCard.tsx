import React, { useState } from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { Alert } from '../../../components/feedback/Alert';
import { Spinner } from '../../../components/feedback/Spinner';
import { holderApi } from '../../../api/holderApi';
import { holderStore } from '../../../state/holderStore';
import type { HolderCredentialResponse } from '../../../api/types';
import { PlusCircle, ShieldCheck } from 'lucide-react';

interface ProvisionCredentialCardProps {
  onSuccess?: (provisioned: HolderCredentialResponse) => void;
}

const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const ProvisionCredentialCard: React.FC<
  ProvisionCredentialCardProps
> = ({ onSuccess }) => {
  const [credentialId, setCredentialId] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [provisioned, setProvisioned] =
    useState<HolderCredentialResponse | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = credentialId.trim();

    if (!trimmed) {
      setError('Please provide a valid Credential UUID.');
      return;
    }

    if (!UUID_REGEX.test(trimmed)) {
      setError(
        'Invalid UUID format. Example: 123e4567-e89b-12d3-a456-426614174000',
      );
      return;
    }

    setIsLoading(true);
    setError(null);
    setProvisioned(null);

    try {
      const result = await holderApi.provisionCredential({
        credentialId: trimmed,
      });
      setProvisioned(result);
      holderStore.addCredential(result);
      setCredentialId('');
      onSuccess?.(result);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Failed to provision credential to holder wallet',
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader>
        <div className="flex items-center gap-2">
          <PlusCircle className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
          <CardTitle>Provision Issued Credential to Wallet</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Link an issued credential to your authenticated wallet. The backend
          securely binds your cryptographic holder identity and stores opening
          secrets in a protected enclave without exposing raw witness material
          to the browser.
        </p>

        {error && (
          <Alert variant="danger" title="Provisioning Failed">
            {error}
          </Alert>
        )}

        {provisioned && (
          <Alert variant="success" title="Credential Successfully Provisioned">
            <div className="space-y-1 text-xs">
              <p>
                <strong>Holder Record ID:</strong>{' '}
                <span className="font-mono">{provisioned.id}</span>
              </p>
              <p>
                <strong>Credential UUID:</strong>{' '}
                <span className="font-mono">{provisioned.credentialId}</span>
              </p>
              <p>
                <strong>Status:</strong> {provisioned.status} (Version{' '}
                {provisioned.version})
              </p>
            </div>
          </Alert>
        )}

        <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
          <Input
            id="provision-credential-id"
            name="credentialId"
            label="Credential Identifier (UUID)"
            helperText="Enter the canonical UUID provided by the issuing authority."
            required
            value={credentialId}
            onChange={(e) => {
              setCredentialId(e.target.value);
              if (error) setError(null);
            }}
            placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
            disabled={isLoading}
            className="font-mono text-sm"
          />

          <div className="flex items-center justify-between pt-2">
            <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
              <ShieldCheck className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
              <span>Zero-knowledge enclave binding enabled</span>
            </div>
            <Button
              type="submit"
              variant="primary"
              disabled={isLoading || !credentialId.trim()}
              className="min-h-[44px]"
            >
              {isLoading ? (
                <>
                  <Spinner size="sm" className="mr-2" />
                  Provisioning...
                </>
              ) : (
                'Provision to Wallet'
              )}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
};
