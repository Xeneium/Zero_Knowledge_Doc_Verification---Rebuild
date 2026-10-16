import React, { useState } from 'react';
import {
  Cpu,
  ShieldCheck,
  AlertTriangle,
  Code2,
  Terminal,
  Lock,
  CheckCircle2,
  XCircle,
} from 'lucide-react';
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
import { TextArea } from '../../../components/common/TextArea';
import { Alert } from '../../../components/feedback/Alert';
import { adminApi } from '../../../api/adminApi';
import { authStore } from '../../../state/authStore';
import type { CredentialPredicateVerificationRequest } from '../../../api/types';

export const PredicateInspectionCard: React.FC = () => {
  const isVerifier = authStore.hasRole('VERIFIER');

  const [proofVersion, setProofVersion] = useState('1.0');
  const [credentialId, setCredentialId] = useState('');
  const [attributeName, setAttributeName] = useState('age');
  const [threshold, setThreshold] = useState<number>(21);
  const [nonce, setNonce] = useState('');
  const [proofBytes, setProofBytes] = useState('');

  const [isVerifying, setIsVerifying] = useState(false);
  const [verifyResult, setVerifyResult] = useState<{
    verified: boolean;
    timestamp: string;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleTestVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setVerifyResult(null);

    const payload: CredentialPredicateVerificationRequest = {
      proofVersion: proofVersion.trim() || '1.0',
      credentialId: credentialId.trim(),
      attributeName: attributeName.trim(),
      threshold: Number(threshold),
      nonce: nonce.trim(),
      proofBytes: proofBytes.trim(),
    };

    setIsVerifying(true);
    try {
      const res = await adminApi.verifyCredentialPredicate(payload);
      setVerifyResult({
        verified: res.verified,
        timestamp: new Date().toISOString(),
      });
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Predicate proof verification failed';
      setError(msg);
    } finally {
      setIsVerifying(false);
    }
  };

  return (
    <Card className="shadow-xs">
      <CardHeader>
        <div className="flex items-center gap-2">
          <Cpu
            className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
            aria-hidden="true"
          />
          <CardTitle>Zero-Knowledge Predicate &amp; Bulletproof Operations</CardTitle>
        </div>
        <CardDescription>
          Protocol inspection, statement binding constraints, and cryptographic
          verification of privacy-preserving Bulletproof range proofs.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-6">
        {/* Architectural Principles Grid */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs dark:border-slate-800 dark:bg-slate-900/50">
            <div className="flex items-center gap-1.5 font-semibold text-slate-900 dark:text-slate-100">
              <Lock className="h-4 w-4 text-indigo-600" aria-hidden="true" />
              <span>Zero Knowledge Privacy</span>
            </div>
            <p className="mt-1 text-slate-600 dark:text-slate-400">
              Prover proves <code>attribute ≥ threshold</code> without revealing
              the underlying plaintext value or secret blinding factor to
              verifiers or administrators.
            </p>
          </div>

          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs dark:border-slate-800 dark:bg-slate-900/50">
            <div className="flex items-center gap-1.5 font-semibold text-slate-900 dark:text-slate-100">
              <ShieldCheck
                className="h-4 w-4 text-indigo-600"
                aria-hidden="true"
              />
              <span>Pedersen Commitment Binding</span>
            </div>
            <p className="mt-1 text-slate-600 dark:text-slate-400">
              Statements bind mathematically to the issuer-signed Pedersen
              commitment attestation point on curve secp256k1, preventing attribute
              substitution.
            </p>
          </div>

          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs dark:border-slate-800 dark:bg-slate-900/50">
            <div className="flex items-center gap-1.5 font-semibold text-slate-900 dark:text-slate-100">
              <Code2 className="h-4 w-4 text-indigo-600" aria-hidden="true" />
              <span>Transcript Nonce Freshness</span>
            </div>
            <p className="mt-1 text-slate-600 dark:text-slate-400">
              Fiat-Shamir challenge incorporates a 128-bit verifier nonce,
              preventing replay attacks across distinct verification sessions.
            </p>
          </div>
        </div>

        {/* Permission Boundary Notice */}
        {!isVerifier && (
          <Alert variant="warning" title="Endpoint Authorization Notice">
            <div className="flex items-start gap-2">
              <AlertTriangle
                className="mt-0.5 h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400"
                aria-hidden="true"
              />
              <span>
                Backend security policy strictly restricts{' '}
                <code>/api/v2/zkp/credential-predicates/verify</code> to users
                possessing <strong>ROLE_VERIFIER</strong>. If your current session
                only possesses <code>ROLE_ADMIN</code>, live test submissions will
                be rejected with <code>403 Forbidden</code> by the Spring Security
                filter chain.
              </span>
            </div>
          </Alert>
        )}

        {/* Live Test Console */}
        <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Terminal
                className="h-4 w-4 text-slate-600 dark:text-slate-400"
                aria-hidden="true"
              />
              <span className="text-xs font-semibold text-slate-900 dark:text-slate-100">
                Predicate Proof Verification Test Console
              </span>
            </div>
            <Badge variant="neutral">POST /api/v2/zkp/credential-predicates/verify</Badge>
          </div>

          <form onSubmit={(e) => void handleTestVerify(e)} className="space-y-4">
            {error && (
              <Alert variant="danger" title="Verification Failure">
                {error}
              </Alert>
            )}

            {verifyResult && (
              <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
                {verifyResult.verified ? (
                  <CheckCircle2
                    className="h-6 w-6 text-emerald-600 dark:text-emerald-400"
                    aria-hidden="true"
                  />
                ) : (
                  <XCircle
                    className="h-6 w-6 text-red-600 dark:text-red-400"
                    aria-hidden="true"
                  />
                )}
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-900 dark:text-slate-100">
                      Proof Mathematical Outcome:
                    </span>
                    <Badge variant={verifyResult.verified ? 'success' : 'danger'}>
                      {verifyResult.verified
                        ? 'PROOF VALID (STATEMENT HOLDS)'
                        : 'PROOF REJECTED (TAMPERED / INVALID)'}
                    </Badge>
                  </div>
                  <p className="mt-0.5 text-xs text-slate-500">
                    Timestamp: {new Date(verifyResult.timestamp).toLocaleString()}
                  </p>
                </div>
              </div>
            )}

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Input
                label="Credential UUID"
                value={credentialId}
                onChange={(e) => setCredentialId(e.target.value)}
                placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
                className="font-mono text-xs"
                required
              />

              <Input
                label="Attribute Name"
                value={attributeName}
                onChange={(e) => setAttributeName(e.target.value)}
                placeholder="e.g. age or income"
                required
              />

              <Input
                label="Threshold (Value to exceed)"
                type="number"
                value={threshold}
                onChange={(e) => setThreshold(Number(e.target.value))}
                required
              />
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input
                label="Nonce (Base64 Encoded)"
                value={nonce}
                onChange={(e) => setNonce(e.target.value)}
                placeholder="Base64 128-bit nonce"
                className="font-mono text-xs"
                required
              />

              <Input
                label="Proof Version"
                value={proofVersion}
                onChange={(e) => setProofVersion(e.target.value)}
                placeholder="1.0"
                required
              />
            </div>

            <TextArea
              label="Bulletproof Range Proof Bytes (Base64 Encoded)"
              value={proofBytes}
              onChange={(e) => setProofBytes(e.target.value)}
              rows={3}
              className="font-mono text-xs"
              placeholder="Base64 encoded inner product range proof bytes"
              required
            />

            <div className="flex justify-end">
              <Button
                type="submit"
                variant="primary"
                isLoading={isVerifying}
                aria-label="Verify predicate proof"
              >
                Verify Predicate Statement
              </Button>
            </div>
          </form>
        </div>
      </CardContent>
    </Card>
  );
};
