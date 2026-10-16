import React, { useState } from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { TextArea } from '../../../components/common/TextArea';
import { Alert } from '../../../components/feedback/Alert';
import { Badge } from '../../../components/common/Badge';
import { hexToBase64, isUuid } from '../../../lib/utils/qrPayload';
import { useAuth, authStore } from '../../../state/authStore';
import { Link } from 'react-router';
import { ShieldCheck, Cpu, Lock, FileCode, CheckCircle2 } from 'lucide-react';
import type { CredentialPredicateVerificationRequest } from '../../../api/types';

interface PredicateProofVerifyCardProps {
  onVerify: (payload: CredentialPredicateVerificationRequest) => Promise<void>;
  disabled?: boolean;
  isLoading?: boolean;
}

export const PredicateProofVerifyCard: React.FC<
  PredicateProofVerifyCardProps
> = ({ onVerify, disabled = false, isLoading = false }) => {
  const [jsonPaste, setJsonPaste] = useState('');
  const [credentialId, setCredentialId] = useState('');
  const [attributeName, setAttributeName] = useState('income');
  const [threshold, setThreshold] = useState('50000');
  const [proofVersion, setProofVersion] = useState('BULLETPROOF_V1');
  const [nonce, setNonce] = useState('');
  const [proofBytes, setProofBytes] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);
  const [pasteSuccess, setPasteSuccess] = useState<string | null>(null);

  const { isAuthenticated } = useAuth();
  const hasVerifierRole = authStore.hasAnyRole(['VERIFIER', 'ADMIN']);

  const handleJsonPaste = (text: string) => {
    setJsonPaste(text);
    setValidationError(null);
    setPasteSuccess(null);

    const trimmed = text.trim();
    if (!trimmed) return;

    try {
      const obj = JSON.parse(trimmed) as Record<string, unknown>;

      if (typeof obj.credentialId === 'string') {
        setCredentialId(obj.credentialId);
      }
      if (typeof obj.attributeName === 'string') {
        setAttributeName(obj.attributeName);
      }
      if (typeof obj.threshold === 'number') {
        setThreshold(String(obj.threshold));
      }
      if (typeof obj.proofVersion === 'string') {
        setProofVersion(obj.proofVersion);
      }

      // Convert Hex from F7 presentation payload to Base64
      if (typeof obj.nonceHex === 'string' && obj.nonceHex) {
        setNonce(hexToBase64(obj.nonceHex));
      } else if (typeof obj.nonce === 'string') {
        setNonce(obj.nonce);
      }

      if (typeof obj.proofBytesHex === 'string' && obj.proofBytesHex) {
        setProofBytes(hexToBase64(obj.proofBytesHex));
      } else if (typeof obj.proofBytes === 'string') {
        setProofBytes(obj.proofBytes);
      }

      setPasteSuccess(
        'Successfully parsed presentation token and converted cryptographic elements.',
      );
    } catch {
      // User might be typing, don't hard fail immediately unless submitting
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading || disabled) return;
    setValidationError(null);

    if (!credentialId.trim() || !isUuid(credentialId.trim())) {
      setValidationError('Please provide a valid Credential UUID');
      return;
    }

    if (!attributeName.trim()) {
      setValidationError('Attribute name cannot be blank');
      return;
    }

    const numericThreshold = parseInt(threshold, 10);
    if (isNaN(numericThreshold) || numericThreshold < 0) {
      setValidationError('Threshold must be a non-negative integer');
      return;
    }

    if (!nonce.trim()) {
      setValidationError('Nonce is required for anti-replay binding');
      return;
    }

    if (!proofBytes.trim()) {
      setValidationError('Proof bytes are required');
      return;
    }

    // Ensure nonce is valid Base64
    let cleanNonce = nonce.trim();
    if (/^[0-9a-fA-F]+$/.test(cleanNonce) && cleanNonce.length % 2 === 0) {
      try {
        cleanNonce = hexToBase64(cleanNonce);
      } catch {
        // Keep as is
      }
    }

    // Ensure proofBytes is valid Base64
    let cleanProof = proofBytes.trim();
    if (/^[0-9a-fA-F]+$/.test(cleanProof) && cleanProof.length % 2 === 0) {
      try {
        cleanProof = hexToBase64(cleanProof);
      } catch {
        // Keep as is
      }
    }

    const payload: CredentialPredicateVerificationRequest = {
      proofVersion: proofVersion.trim() || 'BULLETPROOF_V1',
      credentialId: credentialId.trim(),
      attributeName: attributeName.trim(),
      threshold: numericThreshold,
      nonce: cleanNonce,
      proofBytes: cleanProof,
    };

    try {
      await onVerify(payload);
    } catch (err) {
      const error = err as Error;
      setValidationError(
        error.message || 'Predicate proof verification failed',
      );
    }
  };

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-lg text-slate-900">
            <Cpu className="h-5 w-5 text-indigo-600" aria-hidden="true" />
            Zero-Knowledge Predicate Range Proof Verifier
          </CardTitle>
          <Badge variant="neutral">BULLETPROOF_V1</Badge>
        </div>
      </CardHeader>
      <CardContent>
        {!isAuthenticated || !hasVerifierRole ? (
          <div className="space-y-4">
            <Alert variant="warning" title="Verifier Authentication Required">
              Zero-knowledge predicate verification uses CPU-intensive
              Bulletproof algebraic constraint verification and requires an
              authenticated session with <code>ROLE_VERIFIER</code> or{' '}
              <code>ROLE_ADMIN</code>.
            </Alert>
            <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-4">
              <div className="flex items-center gap-3">
                <Lock className="h-5 w-5 text-slate-500" />
                <span className="text-sm text-slate-700">
                  Connect a session with Verifier privileges to evaluate proofs.
                </span>
              </div>
              <Link to="/login?returnUrl=/verify">
                <Button variant="primary" size="sm">
                  Connect Session
                </Button>
              </Link>
            </div>
          </div>
        ) : (
          <form
            onSubmit={(e) => {
              void handleSubmit(e);
            }}
            className="space-y-4"
          >
            <p className="text-sm text-slate-600">
              Mathematically evaluate a Bulletproof range proof against an
              authoritative credential commitment without learning the holder's
              actual private attribute value.
            </p>

            {validationError && (
              <Alert variant="danger" title="Validation Error">
                {validationError}
              </Alert>
            )}

            {pasteSuccess && (
              <Alert variant="success" title="Payload Parsed">
                {pasteSuccess}
              </Alert>
            )}

            {/* Quick-Paste JSON helper */}
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <label
                htmlFor="json-paste-input"
                className="mb-1 flex items-center gap-2 text-xs font-semibold text-slate-700"
              >
                <FileCode className="h-4 w-4 text-slate-500" />
                Quick-Paste Presentation JSON (from Holder Presentation Token)
              </label>
              <TextArea
                id="json-paste-input"
                value={jsonPaste}
                onChange={(e) => handleJsonPaste(e.target.value)}
                placeholder='Paste raw JSON presentation token: {"credentialId":"...","attributeName":"income","threshold":50000,"nonceHex":"...","proofBytesHex":"..."}'
                rows={2}
                disabled={disabled || isLoading}
                className="font-mono text-xs"
              />
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label
                  htmlFor="zkp-cred-id"
                  className="mb-1 block text-sm font-medium text-slate-700"
                >
                  Credential UUID
                </label>
                <Input
                  id="zkp-cred-id"
                  type="text"
                  value={credentialId}
                  onChange={(e) => setCredentialId(e.target.value)}
                  placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
                  disabled={disabled || isLoading}
                  className="font-mono text-xs"
                />
              </div>

              <div>
                <label
                  htmlFor="zkp-attribute"
                  className="mb-1 block text-sm font-medium text-slate-700"
                >
                  Attribute Name
                </label>
                <Input
                  id="zkp-attribute"
                  type="text"
                  value={attributeName}
                  onChange={(e) => setAttributeName(e.target.value)}
                  placeholder="e.g. income or age"
                  disabled={disabled || isLoading}
                />
              </div>

              <div>
                <label
                  htmlFor="zkp-threshold"
                  className="mb-1 block text-sm font-medium text-slate-700"
                >
                  Threshold Value (&ge;)
                </label>
                <Input
                  id="zkp-threshold"
                  type="number"
                  min="0"
                  value={threshold}
                  onChange={(e) => setThreshold(e.target.value)}
                  placeholder="e.g. 50000"
                  disabled={disabled || isLoading}
                />
              </div>

              <div>
                <label
                  htmlFor="zkp-version"
                  className="mb-1 block text-sm font-medium text-slate-700"
                >
                  Proof Version
                </label>
                <Input
                  id="zkp-version"
                  type="text"
                  value={proofVersion}
                  onChange={(e) => setProofVersion(e.target.value)}
                  disabled
                  className="bg-slate-100 font-mono text-xs"
                />
              </div>
            </div>

            <div>
              <label
                htmlFor="zkp-nonce"
                className="mb-1 block text-sm font-medium text-slate-700"
              >
                Verifier Challenge Nonce (Base64 or Hex)
              </label>
              <Input
                id="zkp-nonce"
                type="text"
                value={nonce}
                onChange={(e) => setNonce(e.target.value)}
                placeholder="e.g. 16-byte fresh challenge nonce"
                disabled={disabled || isLoading}
                className="font-mono text-xs"
              />
            </div>

            <div>
              <label
                htmlFor="zkp-proof-bytes"
                className="mb-1 block text-sm font-medium text-slate-700"
              >
                Bulletproof Proof Bytes (Base64 or Hex)
              </label>
              <TextArea
                id="zkp-proof-bytes"
                value={proofBytes}
                onChange={(e) => setProofBytes(e.target.value)}
                placeholder="Serialized Bulletproof range proof bytes"
                rows={3}
                disabled={disabled || isLoading}
                className="font-mono text-xs"
              />
            </div>

            <div className="flex items-center justify-between pt-2">
              <span className="flex items-center gap-1.5 text-xs text-slate-500">
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                Zero attribute disclosure: only the range statement is proven
              </span>
              <Button
                type="submit"
                variant="primary"
                disabled={
                  !credentialId ||
                  !attributeName ||
                  !nonce ||
                  !proofBytes ||
                  disabled ||
                  isLoading
                }
                className="flex items-center gap-2"
              >
                <ShieldCheck className="h-4 w-4" aria-hidden="true" />
                {isLoading ? 'Evaluating Proof...' : 'Verify ZKP Proof'}
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
};
