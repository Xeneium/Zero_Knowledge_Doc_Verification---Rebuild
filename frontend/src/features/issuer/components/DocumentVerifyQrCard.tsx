import React, { useState, useEffect } from 'react';
import {
  FileCheck2,
  ShieldCheck,
  QrCode,
  Sparkles,
  Upload,
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
import { Select } from '../../../components/common/Select';
import { Badge } from '../../../components/common/Badge';
import { Alert } from '../../../components/feedback/Alert';
import { CopyToClipboard } from '../../../components/data/CopyToClipboard';
import { issuerApi } from '../../../api/issuerApi';
import { useIssuerActivities } from '../../../state/issuerStore';
import type {
  DocumentVerificationResult,
  CapabilityResponse,
  VerificationMode,
} from '../../../api/types';

export interface DocumentVerifyQrCardProps {
  initialDocumentId?: string;
}

const VERIFICATION_MODES = [
  {
    value: 'MODE_A_PRESENTED_ARTIFACT',
    label: 'Mode A: Verifier Presents Artifact (Digest Matching)',
  },
  {
    value: 'MODE_B_REGISTRY_CAPABILITY',
    label: 'Mode B: Registry Capability (Direct Trusted Resolution)',
  },
];

export const DocumentVerifyQrCard: React.FC<DocumentVerifyQrCardProps> = ({
  initialDocumentId = '',
}) => {
  const [documentId, setDocumentId] = useState(initialDocumentId);
  const [error, setError] = useState<string | null>(null);

  // Mode A Server Verification
  const [isVerifyingStored, setIsVerifyingStored] = useState(false);
  const [storedVerifyResult, setStoredVerifyResult] =
    useState<DocumentVerificationResult | null>(null);

  // Mode B Upload Verification
  const [verifyFile, setVerifyFile] = useState<File | null>(null);
  const [isVerifyingUploaded, setIsVerifyingUploaded] = useState(false);
  const [uploadVerifyResult, setUploadVerifyResult] =
    useState<DocumentVerificationResult | null>(null);

  // QR Code
  const [isLoadingQr, setIsLoadingQr] = useState(false);
  const [qrBlobUrl, setQrBlobUrl] = useState<string | null>(null);

  // Capabilities
  const [isMintingCapability, setIsMintingCapability] = useState(false);
  const [capabilityMode, setCapabilityMode] = useState<VerificationMode>(
    'MODE_A_PRESENTED_ARTIFACT',
  );
  const [capabilityTtl, setCapabilityTtl] = useState('3600');
  const [mintedCapability, setMintedCapability] =
    useState<CapabilityResponse | null>(null);
  const [capabilityQrUrl, setCapabilityQrUrl] = useState<string | null>(null);

  const activities = useIssuerActivities();

  // Object URL unmount cleanup to eliminate memory leaks
  useEffect(() => {
    return () => {
      if (qrBlobUrl) URL.revokeObjectURL(qrBlobUrl);
    };
  }, [qrBlobUrl]);

  useEffect(() => {
    return () => {
      if (capabilityQrUrl) URL.revokeObjectURL(capabilityQrUrl);
    };
  }, [capabilityQrUrl]);

  const handleVerifyStored = async () => {
    if (isVerifyingStored) return;
    const id = documentId.trim();
    if (!id) {
      setError('Please enter a Document UUID');
      return;
    }
    setIsVerifyingStored(true);
    setError(null);
    setStoredVerifyResult(null);

    try {
      const res = await issuerApi.verifyStoredDocument(id);
      setStoredVerifyResult(res);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Stored copy verification failed',
      );
    } finally {
      setIsVerifyingStored(false);
    }
  };

  const handleVerifyUploaded = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isVerifyingUploaded) return;
    const id = documentId.trim();
    if (!id) {
      setError('Please enter a Document UUID');
      return;
    }
    if (!verifyFile) {
      setError('Please select a local document copy to verify');
      return;
    }

    setIsVerifyingUploaded(true);
    setError(null);
    setUploadVerifyResult(null);

    try {
      const res = await issuerApi.verifyUploadedDocument(id, verifyFile);
      setUploadVerifyResult(res);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Uploaded copy verification failed',
      );
    } finally {
      setIsVerifyingUploaded(false);
    }
  };

  const handleFetchQr = async () => {
    if (isLoadingQr) return;
    const id = documentId.trim();
    if (!id) {
      setError('Please enter a Document UUID');
      return;
    }

    setIsLoadingQr(true);
    setError(null);

    try {
      const blob = await issuerApi.getDocumentQrBlob(id);
      if (qrBlobUrl) {
        URL.revokeObjectURL(qrBlobUrl);
      }
      const url = URL.createObjectURL(blob);
      setQrBlobUrl(url);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to retrieve document QR',
      );
    } finally {
      setIsLoadingQr(false);
    }
  };

  const handleMintCapability = async () => {
    if (isMintingCapability) return;
    const id = documentId.trim();
    if (!id) {
      setError('Please enter a Document UUID');
      return;
    }

    setIsMintingCapability(true);
    setError(null);
    setMintedCapability(null);
    if (capabilityQrUrl) {
      URL.revokeObjectURL(capabilityQrUrl);
      setCapabilityQrUrl(null);
    }

    try {
      const cap = await issuerApi.createCapability({
        documentId: id,
        mode: capabilityMode,
        ttlSeconds: parseInt(capabilityTtl, 10) || 3600,
      });
      setMintedCapability(cap);

      // Fetch capability QR code
      const qrBlob = await issuerApi.getCapabilityQrBlob(cap.capabilityToken);
      const url = URL.createObjectURL(qrBlob);
      setCapabilityQrUrl(url);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Failed to mint verification capability',
      );
    } finally {
      setIsMintingCapability(false);
    }
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400">
            <FileCheck2 className="h-5 w-5" />
          </div>
          <div>
            <CardTitle>Document Verification &amp; QR Tool</CardTitle>
            <CardDescription>
              Verify document integrity against RSA-PSS signatures and generate
              verifiable QR capabilities.
            </CardDescription>
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-6">
        {/* Document ID Input */}
        <div className="space-y-2">
          <Input
            label="Document ID (UUID)"
            value={documentId}
            onChange={(e) => setDocumentId(e.target.value)}
            placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
            helperText="The unique identifier of an uploaded document."
            required
          />

          {/* Quick select from session activities */}
          {activities.filter((a) => a.type === 'document').length > 0 && (
            <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-slate-500 dark:text-slate-400">
              <span>Recent in session:</span>
              {activities
                .filter((a) => a.type === 'document')
                .slice(0, 3)
                .map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setDocumentId(item.id)}
                    className="rounded-sm border border-slate-200 bg-slate-100 px-2 py-0.5 font-mono text-[11px] text-slate-700 hover:bg-slate-200 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
                  >
                    {item.title || item.id.slice(0, 8)}...
                  </button>
                ))}
            </div>
          )}
        </div>

        {error && (
          <Alert variant="danger" title="Error">
            {error}
          </Alert>
        )}

        {/* Section 1: Mode A & QR Generation */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {/* Mode A Box */}
          <div className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
            <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              Mode A: Verify Server Stored Copy
            </h4>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Calculates SHA-256 hash from server's persisted bytes and verifies
              with RSA-PSS key.
            </p>
            <div className="mt-4">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  void handleVerifyStored();
                }}
                isLoading={isVerifyingStored}
                disabled={!documentId.trim() || isVerifyingStored}
              >
                <ShieldCheck className="mr-1.5 h-4 w-4" />
                Run Mode A Verify
              </Button>
            </div>

            {storedVerifyResult && (
              <div className="mt-3">
                <Badge
                  variant={storedVerifyResult.verified ? 'success' : 'danger'}
                >
                  {storedVerifyResult.verified
                    ? 'VERIFIED (Digest & Signature Match)'
                    : 'VERIFICATION FAILED'}
                </Badge>
              </div>
            )}
          </div>

          {/* Direct Public QR Box */}
          <div className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
            <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              Public Verification QR
            </h4>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Fetches the direct public verification token QR code image from
              the backend.
            </p>
            <div className="mt-4">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  void handleFetchQr();
                }}
                isLoading={isLoadingQr}
                disabled={!documentId.trim() || isLoadingQr}
              >
                <QrCode className="mr-1.5 h-4 w-4" />
                Fetch QR Code
              </Button>
            </div>

            {qrBlobUrl && (
              <div className="mt-3 flex items-center gap-3">
                <img
                  src={qrBlobUrl}
                  alt="Document QR Code"
                  className="h-20 w-20 rounded border border-slate-200 p-1 dark:border-slate-700"
                />
                <a
                  href={qrBlobUrl}
                  download={`zkdoc-${documentId.slice(0, 8)}.png`}
                  className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                >
                  Download PNG
                </a>
              </div>
            )}
          </div>
        </div>

        {/* Section 2: Mode B Upload Verification */}
        <div className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            Mode B: Verify Local Copy Without Trusting Server Copy
          </h4>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Uploads a local copy from your machine to compare against the
            authoritative digest on record.
          </p>

          <form
            onSubmit={(e) => {
              void handleVerifyUploaded(e);
            }}
            className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center"
          >
            <input
              type="file"
              onChange={(e) => setVerifyFile(e.target.files?.[0] || null)}
              className="text-xs text-slate-600 file:mr-2 file:rounded file:border-0 file:bg-indigo-50 file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-indigo-700 hover:file:bg-indigo-100 dark:text-slate-300 dark:file:bg-slate-800 dark:file:text-indigo-400"
            />
            <Button
              type="submit"
              variant="outline"
              size="sm"
              disabled={
                !documentId.trim() || !verifyFile || isVerifyingUploaded
              }
              isLoading={isVerifyingUploaded}
            >
              <Upload className="mr-1.5 h-4 w-4" />
              Run Mode B Verify
            </Button>
          </form>

          {uploadVerifyResult && (
            <div className="mt-3">
              <Badge
                variant={uploadVerifyResult.verified ? 'success' : 'danger'}
              >
                {uploadVerifyResult.verified
                  ? 'VERIFIED (Uploaded file matched stored digest & signature)'
                  : 'VERIFICATION FAILED (File contents differed)'}
              </Badge>
            </div>
          )}
        </div>

        {/* Section 3: Mint Verification Capability */}
        <div className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
            <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              Mint Scoped Verification Capability
            </h4>
          </div>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Mints a bearer verification token with configurable TTL for
            controlled presentation.
          </p>

          <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Select
              label="Verification Capability Mode"
              options={VERIFICATION_MODES}
              value={capabilityMode}
              onChange={(e) =>
                setCapabilityMode(e.target.value as VerificationMode)
              }
            />

            <Input
              label="TTL in Seconds"
              type="number"
              value={capabilityTtl}
              onChange={(e) => setCapabilityTtl(e.target.value)}
              placeholder="3600"
              helperText="E.g. 3600 = 1 hour, 86400 = 1 day (max 30 days)"
            />
          </div>

          <div className="mt-4 flex justify-end">
            <Button
              type="button"
              variant="primary"
              size="sm"
              onClick={() => {
                void handleMintCapability();
              }}
              isLoading={isMintingCapability}
              disabled={!documentId.trim() || isMintingCapability}
            >
              Mint Capability
            </Button>
          </div>

          {mintedCapability && (
            <div className="mt-4 space-y-3 rounded-md border border-indigo-100 bg-indigo-50/40 p-4 text-xs dark:border-indigo-900/50 dark:bg-indigo-950/20">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-700 dark:text-slate-300">
                  Capability Token:
                </span>
                <div className="flex items-center gap-1.5 font-mono text-slate-900 dark:text-slate-100">
                  <span>
                    {mintedCapability.capabilityToken.slice(0, 16)}...
                  </span>
                  <CopyToClipboard
                    value={mintedCapability.capabilityToken}
                    label="Copy Token"
                  />
                </div>
              </div>

              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-700 dark:text-slate-300">
                  Expires At:
                </span>
                <span className="text-slate-900 dark:text-slate-100">
                  {new Date(mintedCapability.expiresAt).toLocaleString()}
                </span>
              </div>

              {capabilityQrUrl && (
                <div className="mt-2 flex items-center gap-3 border-t border-indigo-100 pt-2 dark:border-indigo-900/40">
                  <img
                    src={capabilityQrUrl}
                    alt="Capability QR"
                    className="h-20 w-20 rounded border border-indigo-200 p-1 dark:border-indigo-800"
                  />
                  <div>
                    <p className="font-semibold text-slate-900 dark:text-slate-100">
                      Capability QR Ready
                    </p>
                    <a
                      href={capabilityQrUrl}
                      download={`capability-${mintedCapability.capabilityToken.slice(0, 8)}.png`}
                      className="mt-1 inline-block text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                    >
                      Download Capability PNG
                    </a>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
};
