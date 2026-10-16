import React, { useState, useEffect } from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { Select } from '../../../components/common/Select';
import { Alert } from '../../../components/feedback/Alert';
import { Spinner } from '../../../components/feedback/Spinner';
import { FormattedHash } from '../../../components/data/FormattedHash';
import { holderApi } from '../../../api/holderApi';
import { useHolderState } from '../../../state/holderStore';
import type { CapabilityResponse, VerificationMode } from '../../../api/types';
import { QrCode, Download, ShieldCheck, Clock } from 'lucide-react';

interface HolderCapabilityQrCardProps {
  initialCredentialId?: string;
}

export const HolderCapabilityQrCard: React.FC<HolderCapabilityQrCardProps> = ({
  initialCredentialId,
}) => {
  const { credentials } = useHolderState();

  const [credentialId, setCredentialId] = useState(initialCredentialId || '');
  const [mode, setMode] = useState<VerificationMode>(
    'MODE_A_PRESENTED_ARTIFACT',
  );
  const [ttlSeconds, setTtlSeconds] = useState('3600');

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mintedCapability, setMintedCapability] =
    useState<CapabilityResponse | null>(null);
  const [qrBlobUrl, setQrBlobUrl] = useState<string | null>(null);

  const effectiveCredentialId =
    credentialId || initialCredentialId || (credentials[0]?.credentialId ?? '');

  // Clean up Object URL on unmount or refresh
  useEffect(() => {
    return () => {
      if (qrBlobUrl) {
        URL.revokeObjectURL(qrBlobUrl);
      }
    };
  }, [qrBlobUrl]);

  const handleMintCapability = async (e: React.FormEvent) => {
    e.preventDefault();
    const id = effectiveCredentialId.trim();
    if (!id) {
      setError('Please provide a valid Credential UUID.');
      return;
    }

    setIsLoading(true);
    setError(null);
    setMintedCapability(null);
    if (qrBlobUrl) {
      URL.revokeObjectURL(qrBlobUrl);
      setQrBlobUrl(null);
    }

    try {
      const cap = await holderApi.createCapability({
        credentialId: id,
        mode,
        ttlSeconds: parseInt(ttlSeconds, 10),
      });
      setMintedCapability(cap);

      // Fetch the binary QR code image
      const blob = await holderApi.getCapabilityQrBlob(cap.capabilityToken);
      const url = URL.createObjectURL(blob);
      setQrBlobUrl(url);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Failed to mint verification capability',
      );
    } finally {
      setIsLoading(false);
    }
  };

  const handleDownloadQr = () => {
    if (!qrBlobUrl || !mintedCapability) return;
    const a = document.createElement('a');
    a.href = qrBlobUrl;
    a.download = `capability-qr-${mintedCapability.capabilityToken.substring(0, 8)}.png`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader>
        <div className="flex items-center gap-2">
          <QrCode className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
          <CardTitle>Mint Verification Capability &amp; QR Code</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Create a time-bounded verification capability token for offline or
          mobile verification. Verifiers scanning this QR code obtain an
          authoritative verification reference without exposing your private
          identity or unneeded attributes.
        </p>

        {/* Clear Conceptual Distinction Alert */}
        <Alert
          variant="info"
          title="Important Distinction: Reference vs. Result"
        >
          <div className="space-y-1 text-xs">
            <p>
              1. <strong>Wallet Credential:</strong> Your authenticated digital
              asset.
            </p>
            <p>
              2. <strong>Verification Capability QR:</strong> A temporary
              cryptographic pointer/reference that enables a verifier to
              validate status.
            </p>
            <p>
              3. <strong>Verification Result:</strong> The authoritative boolean
              answer computed by the backend verification engine upon
              evaluation.
            </p>
          </div>
        </Alert>

        {error && (
          <Alert variant="danger" title="Capability Minting Error">
            {error}
          </Alert>
        )}

        <form
          onSubmit={(e) => void handleMintCapability(e)}
          className="space-y-4"
        >
          {credentials.length > 0 ? (
            <Select
              id="capability-credential-id"
              label="Target Credential UUID"
              helperText="Select from your wallet or input a credential UUID."
              required
              value={effectiveCredentialId}
              onChange={(e) => setCredentialId(e.target.value)}
              options={credentials.map((c) => ({
                value: c.credentialId,
                label: `Credential: ${c.credentialId} (Status: ${c.status})`,
              }))}
              disabled={isLoading}
            />
          ) : (
            <Input
              id="capability-credential-id"
              label="Target Credential UUID"
              helperText="Select from your wallet or input a credential UUID."
              required
              value={effectiveCredentialId}
              onChange={(e) => setCredentialId(e.target.value)}
              placeholder="Enter Credential UUID"
              disabled={isLoading}
              className="font-mono text-sm"
            />
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Select
              id="capability-mode"
              label="Verification Mode"
              helperText="Mode A verifies signature; Mode B verifies registry."
              value={mode}
              onChange={(e) => setMode(e.target.value as VerificationMode)}
              options={[
                {
                  value: 'MODE_A_PRESENTED_ARTIFACT',
                  label: 'Mode A: Presented Artifact Signature',
                },
                {
                  value: 'MODE_B_REGISTRY_CAPABILITY',
                  label: 'Mode B: Registry Capability Status',
                },
              ]}
              disabled={isLoading}
            />

            <Select
              id="capability-ttl"
              label="Capability Time-To-Live (TTL)"
              helperText="Expiry window after which the token is invalid."
              value={ttlSeconds}
              onChange={(e) => setTtlSeconds(e.target.value)}
              options={[
                { value: '900', label: '15 Minutes (Short-lived)' },
                { value: '3600', label: '1 Hour (Standard)' },
                { value: '86400', label: '24 Hours (Full Day)' },
              ]}
              disabled={isLoading}
            />
          </div>

          <div className="flex justify-end pt-2">
            <Button
              type="submit"
              variant="primary"
              disabled={isLoading || !effectiveCredentialId.trim()}
              className="min-h-[44px]"
            >
              {isLoading ? (
                <>
                  <Spinner size="sm" className="mr-2" />
                  Minting Capability...
                </>
              ) : (
                <>
                  <QrCode className="mr-1.5 h-4 w-4" />
                  Mint Capability QR
                </>
              )}
            </Button>
          </div>
        </form>

        {/* Minted Result & QR Code Display */}
        {mintedCapability && (
          <div className="mt-6 flex flex-col items-center gap-6 rounded-lg border border-indigo-200 bg-indigo-50/40 p-6 sm:flex-row sm:items-start dark:border-indigo-950 dark:bg-indigo-950/20">
            {qrBlobUrl ? (
              <div className="flex flex-col items-center gap-2">
                <img
                  src={qrBlobUrl}
                  alt={`Verification Capability QR for token ${mintedCapability.capabilityToken}`}
                  className="h-44 w-44 rounded-lg border border-slate-300 bg-white p-2 shadow-xs dark:border-slate-700"
                />
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleDownloadQr}
                  className="min-h-[44px]"
                >
                  <Download className="mr-1.5 h-4 w-4" />
                  Download PNG
                </Button>
              </div>
            ) : (
              <div className="flex h-44 w-44 items-center justify-center rounded-lg border border-slate-300 bg-white dark:border-slate-700 dark:bg-slate-900">
                <Spinner size="md" />
              </div>
            )}

            <div className="flex-1 space-y-3 text-xs">
              <div className="flex items-center gap-1.5 text-emerald-700 dark:text-emerald-400">
                <ShieldCheck className="h-4 w-4" />
                <span className="font-semibold tracking-wider uppercase">
                  Active Capability Token
                </span>
              </div>

              <div>
                <span className="text-slate-500 uppercase">
                  Capability Token
                </span>
                <div className="mt-1 flex items-center justify-between rounded border border-slate-200 bg-white p-2 dark:border-slate-800 dark:bg-slate-900">
                  <FormattedHash
                    value={mintedCapability.capabilityToken}
                    prefixLength={12}
                    suffixLength={12}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <span className="text-slate-500 uppercase">
                    Protocol Version
                  </span>
                  <p className="font-mono text-slate-800 dark:text-slate-200">
                    {mintedCapability.protocolVersion}
                  </p>
                </div>
                <div>
                  <span className="text-slate-500 uppercase">
                    Verification Mode
                  </span>
                  <p className="font-mono text-slate-800 dark:text-slate-200">
                    {mintedCapability.mode}
                  </p>
                </div>
              </div>

              <div>
                <span className="text-slate-500 uppercase">Expires At</span>
                <p className="flex items-center gap-1 font-mono text-slate-800 dark:text-slate-200">
                  <Clock className="h-3.5 w-3.5 text-slate-400" />
                  {new Date(mintedCapability.expiresAt).toLocaleString()}
                </p>
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
};
