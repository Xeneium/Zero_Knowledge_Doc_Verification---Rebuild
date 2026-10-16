import React, { useState } from 'react';
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
import { CopyToClipboard } from '../../../components/data/CopyToClipboard';
import { holderApi } from '../../../api/holderApi';
import { holderStore, useHolderState } from '../../../state/holderStore';
import type { HolderPredicateProofResponse } from '../../../api/types';
import {
  FileKey,
  ShieldCheck,
  RefreshCw,
  Lock,
  EyeOff,
  CheckCircle2,
} from 'lucide-react';

interface PredicateProofGeneratorCardProps {
  initialHolderCredentialId?: string;
}

const createRandomNonceHex = (): string => {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
};

export const PredicateProofGeneratorCard: React.FC<
  PredicateProofGeneratorCardProps
> = ({ initialHolderCredentialId }) => {
  const { credentials } = useHolderState();

  const [holderCredentialId, setHolderCredentialId] = useState(
    initialHolderCredentialId || '',
  );
  const [attributeName, setAttributeName] = useState('age');
  const [operator] = useState('>=');
  const [threshold, setThreshold] = useState<number>(18);
  const [nonceHex, setNonceHex] = useState(createRandomNonceHex);

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [proofResult, setProofResult] =
    useState<HolderPredicateProofResponse | null>(null);

  const activeCredentials = credentials.filter((c) => c.status === 'ACTIVE');
  const effectiveHolderCredentialId =
    holderCredentialId ||
    initialHolderCredentialId ||
    activeCredentials[0]?.id ||
    credentials[0]?.id ||
    '';

  const generateRandomNonce = () => {
    setNonceHex(createRandomNonceHex());
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const credId = effectiveHolderCredentialId.trim();
    const attr = attributeName.trim();
    const nonce = nonceHex.trim();

    if (!credId) {
      setError('Please select or specify a Holder Credential record.');
      return;
    }

    if (!attr) {
      setError('Please specify the attribute name to evaluate.');
      return;
    }

    if (threshold < 0 || isNaN(threshold)) {
      setError('Threshold must be a non-negative number.');
      return;
    }

    if (!nonce || !/^[0-9a-fA-F]{2,128}$/.test(nonce)) {
      setError(
        'Nonce must be a valid hex string between 2 and 128 characters.',
      );
      return;
    }

    setIsLoading(true);
    setError(null);
    setProofResult(null);

    try {
      const result = await holderApi.generatePredicateProof({
        holderCredentialId: credId,
        attributeName: attr,
        operator,
        threshold,
        nonceHex: nonce,
      });

      setProofResult(result);
      holderStore.addGeneratedProof(result);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Failed to generate zero-knowledge predicate proof',
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader>
        <div className="flex items-center gap-2">
          <FileKey className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
          <CardTitle>Zero-Knowledge Predicate Proof Generator</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Mathematically prove that your committed credential attribute
          satisfies a condition (e.g.{' '}
          <code className="rounded bg-slate-100 px-1 py-0.5 text-slate-800 dark:bg-slate-800 dark:text-slate-200">
            age &gt;= 18
          </code>
          ) without disclosing your true attribute value, birthdate, or private
          blinding factor.
        </p>

        {/* Minimal Disclosure Guarantee */}
        <div className="grid grid-cols-1 gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 sm:grid-cols-2 dark:border-slate-800 dark:bg-slate-900/50">
          <div className="flex items-start gap-2">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
            <div className="text-xs text-slate-700 dark:text-slate-300">
              <strong className="text-slate-900 dark:text-slate-100">
                Disclosed to Verifier:
              </strong>
              <p>
                Attribute name, threshold comparison, statement anchors, and
                cryptographic Bulletproof bytes.
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2">
            <EyeOff className="mt-0.5 h-4 w-4 shrink-0 text-indigo-600" />
            <div className="text-xs text-slate-700 dark:text-slate-300">
              <strong className="text-slate-900 dark:text-slate-100">
                Never Disclosed:
              </strong>
              <p>
                Exact attribute value (e.g. real age), raw document data, or
                private Pedersen opening secret.
              </p>
            </div>
          </div>
        </div>

        {error && (
          <Alert variant="danger" title="Proof Generation Error">
            {error}
          </Alert>
        )}

        <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
          {activeCredentials.length > 0 ? (
            <Select
              id="target-holder-credential"
              label="Target Holder Credential"
              helperText="Select an active credential from your wallet."
              required
              value={effectiveHolderCredentialId}
              onChange={(e) => setHolderCredentialId(e.target.value)}
              options={activeCredentials.map((c) => ({
                value: c.id,
                label: `Credential: ${c.credentialId.substring(0, 8)}... (Digest: ${c.credentialDigestHex.substring(0, 8)}...)`,
              }))}
              disabled={isLoading}
            />
          ) : (
            <Input
              id="target-holder-credential"
              label="Target Holder Credential"
              helperText="Select an active credential from your wallet."
              required
              value={effectiveHolderCredentialId}
              onChange={(e) => setHolderCredentialId(e.target.value)}
              placeholder="Enter Holder Credential UUID"
              disabled={isLoading}
              className="font-mono text-sm"
            />
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Input
              id="proof-attribute-name"
              label="Attribute Name"
              helperText="e.g. age, income, rating"
              required
              value={attributeName}
              onChange={(e) => setAttributeName(e.target.value)}
              placeholder="age"
              disabled={isLoading}
            />

            <Input
              id="proof-operator"
              label="Predicate Operator"
              helperText="Backend requires '>='"
              value=">= (GREATER_THAN_OR_EQUAL)"
              disabled
              className="bg-slate-100 font-mono text-xs dark:bg-slate-800"
            />

            <Input
              id="proof-threshold"
              label="Threshold Value"
              helperText="Non-negative number"
              required
              type="number"
              min="0"
              value={threshold}
              onChange={(e) => setThreshold(Number(e.target.value))}
              disabled={isLoading}
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-end gap-2">
              <div className="flex-1">
                <Input
                  id="proof-nonce"
                  label="Transcript Nonce (Challenge Hex)"
                  helperText="Cryptographically secure fresh challenge to defend against proof replay."
                  required
                  value={nonceHex}
                  onChange={(e) => setNonceHex(e.target.value)}
                  placeholder="16-byte hex nonce"
                  disabled={isLoading}
                  className="font-mono text-xs"
                />
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={generateRandomNonce}
                disabled={isLoading}
                title="Generate Fresh Nonce"
                className="mb-[2px] min-h-[44px] shrink-0"
              >
                <RefreshCw className="h-4 w-4" />
                <span className="hidden sm:ml-1.5 sm:inline">Fresh Nonce</span>
              </Button>
            </div>
          </div>

          <div className="flex justify-end pt-2">
            <Button
              type="submit"
              variant="primary"
              disabled={
                isLoading || !effectiveHolderCredentialId || !attributeName
              }
              className="min-h-[44px]"
            >
              {isLoading ? (
                <>
                  <Spinner size="sm" className="mr-2" />
                  Generating Bulletproof...
                </>
              ) : (
                <>
                  <Lock className="mr-1.5 h-4 w-4" />
                  Generate Zero-Knowledge Proof
                </>
              )}
            </Button>
          </div>
        </form>

        {/* Proof Artifact Result Display */}
        {proofResult && (
          <div className="mt-6 space-y-4 rounded-lg border border-emerald-200 bg-emerald-50/50 p-4 dark:border-emerald-950 dark:bg-emerald-950/20">
            <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
                <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                  Zero-Knowledge Proof Artifact Generated
                </h4>
              </div>
              <CopyToClipboard
                value={JSON.stringify(proofResult, null, 2)}
                label="Copy Full Presentation Pack (JSON)"
              />
            </div>

            <div className="rounded border border-emerald-300 bg-white p-2.5 text-xs text-emerald-900 dark:border-emerald-900 dark:bg-slate-900 dark:text-emerald-200">
              <strong>Proven Statement:</strong> Attribute{' '}
              <code className="font-semibold">{proofResult.attributeName}</code>{' '}
              {proofResult.operator} <strong>{proofResult.threshold}</strong>{' '}
              (Verified by Key Authority:{' '}
              <code>{proofResult.attestationKeyId}</code>)
            </div>

            <div className="grid grid-cols-1 gap-2.5 text-xs sm:grid-cols-2">
              <div className="rounded border border-slate-200 bg-white p-2 dark:border-slate-800 dark:bg-slate-900">
                <span className="text-[11px] font-semibold text-slate-500 uppercase">
                  Proof Engine &amp; Bit Size
                </span>
                <p className="font-mono text-slate-800 dark:text-slate-200">
                  {proofResult.proofVersion} ({proofResult.bitSize}-bit range)
                </p>
              </div>

              <div className="rounded border border-slate-200 bg-white p-2 dark:border-slate-800 dark:bg-slate-900">
                <span className="text-[11px] font-semibold text-slate-500 uppercase">
                  Transcript Nonce (Hex)
                </span>
                <p className="font-mono text-slate-800 dark:text-slate-200">
                  {proofResult.nonceHex}
                </p>
              </div>

              <div className="rounded border border-slate-200 bg-white p-2 sm:col-span-2 dark:border-slate-800 dark:bg-slate-900">
                <span className="text-[11px] font-semibold text-slate-500 uppercase">
                  Commitment Point (Hex)
                </span>
                <div className="mt-0.5 flex items-center justify-between">
                  <FormattedHash
                    value={proofResult.commitmentPointHex}
                    prefixLength={16}
                    suffixLength={16}
                  />
                </div>
              </div>

              <div className="rounded border border-slate-200 bg-white p-2 sm:col-span-2 dark:border-slate-800 dark:bg-slate-900">
                <span className="text-[11px] font-semibold text-slate-500 uppercase">
                  Bulletproof Cryptographic Bytes (Hex)
                </span>
                <div className="mt-0.5 flex items-center justify-between">
                  <FormattedHash
                    value={proofResult.proofBytesHex}
                    prefixLength={20}
                    suffixLength={20}
                  />
                </div>
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
};
