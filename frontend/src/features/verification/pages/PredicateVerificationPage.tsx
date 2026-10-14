import { useState } from 'react';
import {
  CheckCircle2,
  FileCode,
  Shield,
  ShieldAlert,
  ShieldCheck,
  XCircle,
} from 'lucide-react';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Alert } from '../../../components/ui/Alert';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Input, Textarea } from '../../../components/ui/Field';
import { useAuth } from '../../auth/AuthContext';
import {
  verifyPredicateProof,
  type PredicateVerificationResult,
} from '../api/authoritativeVerificationApi';

export function PredicateVerificationPage() {
  const { session, isAuthenticated, hasRole, connectPreset } = useAuth();

  const [jsonInput, setJsonInput] = useState('');
  const [proofVersion, setProofVersion] = useState<number>(2);
  const [credentialId, setCredentialId] = useState('');
  const [attributeName, setAttributeName] = useState('gpa');
  const [threshold, setThreshold] = useState<number>(300);
  const [nonce, setNonce] = useState('');
  const [proofBytes, setProofBytes] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<PredicateVerificationResult | null>(null);

  const handleLoadFromJson = () => {
    setError(null);
    try {
      if (!jsonInput.trim()) {
        setError('Paste proof JSON first.');
        return;
      }
      const parsed = JSON.parse(jsonInput.trim());
      if (typeof parsed.proofVersion === 'number') setProofVersion(parsed.proofVersion);
      if (typeof parsed.credentialId === 'string') setCredentialId(parsed.credentialId);
      if (typeof parsed.attributeName === 'string') setAttributeName(parsed.attributeName);
      if (typeof parsed.threshold === 'number') setThreshold(parsed.threshold);
      if (typeof parsed.nonce === 'string') setNonce(parsed.nonce);
      if (typeof parsed.proofBytes === 'string') setProofBytes(parsed.proofBytes);
    } catch {
      setError('Invalid JSON structure. Ensure you pasted a valid proof payload.');
    }
  };

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      if (!credentialId.trim()) throw new Error('Credential ID is required.');
      if (!attributeName.trim()) throw new Error('Attribute Name is required.');
      if (!nonce.trim()) throw new Error('Nonce is required.');
      if (!proofBytes.trim()) throw new Error('Proof Bytes are required.');

      const res = await verifyPredicateProof({
        proofVersion: Number(proofVersion),
        credentialId: credentialId.trim(),
        attributeName: attributeName.trim(),
        threshold: Number(threshold),
        nonce: nonce.trim(),
        proofBytes: proofBytes.trim(),
      });

      setResult(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Predicate verification failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Predicate verification"
        description="Verify a threshold claim without disclosing the underlying credential value."
        breadcrumbs={[{ label: 'Overview', to: '/' }, { label: 'Predicate verification' }]}
        actions={
          isAuthenticated ? (
            <Badge tone="success">
              {session?.roles[0]} · Authenticated
            </Badge>
          ) : (
            <Badge tone="warning">Verifier Role Recommended</Badge>
          )
        }
      />

      {!isAuthenticated || !hasRole('VERIFIER') ? (
        <Alert
          tone="info"
          title="Verifier session recommended"
          className="mb-6"
        >
          <p>
            Cryptographic zero-knowledge predicate proof verification is an authoritative verifier endpoint
            (<code>POST /api/v2/zkp/credential-predicates/verify</code>).
          </p>
          <div className="mt-3">
            <Button size="sm" variant="secondary" onClick={() => connectPreset('VERIFIER')}>
              Connect as Verifier (verifier-corp)
            </Button>
          </div>
        </Alert>
      ) : null}

      <div className="space-y-6">
        <Card className="p-6">
          <div className="flex items-center gap-3 border-b border-ink-100 pb-4">
            <span className="inline-flex size-10 items-center justify-center rounded-lg bg-accent-50 text-accent-700">
              <ShieldCheck className="size-5" />
            </span>
            <div>
              <h2 className="text-base font-semibold text-ink-900">Zero-Knowledge Bulletproof Predicate Verifier</h2>
              <p className="text-xs text-ink-600">
                Verifies range proof <code>Attribute &gt;= Threshold</code> against the Pedersen commitment and issuer RSA-PSS signature.
              </p>
            </div>
          </div>

          {error && (
            <Alert tone="danger" title="Verification Rejected" className="mt-4">
              {error}
            </Alert>
          )}

          {result && (
            <div
              className={`rounded-xl border p-5 mt-4 ${
                result.verified
                  ? 'border-success-200 bg-success-50 text-success-900'
                  : 'border-danger-200 bg-danger-50 text-danger-900'
              }`}
            >
              <div className="flex items-center gap-2 mb-3">
                {result.verified ? (
                  <CheckCircle2 className="size-5 text-success-700" />
                ) : (
                  <XCircle className="size-5 text-danger-700" />
                )}
                <h3 className="text-base font-semibold">
                  {result.verified
                    ? 'Predicate Mathematically Proven and Satisfied!'
                    : 'Predicate Proof Rejected / Invalid'}
                </h3>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs mb-3">
                <div className="bg-white/80 rounded p-2.5 border">
                  <span className="font-semibold block">Range Proof (Bulletproof):</span>
                  <Badge tone={result.proofValid !== false ? 'success' : 'danger'}>
                    {result.proofValid !== false ? 'Mathematically Valid' : 'Invalid Proof'}
                  </Badge>
                </div>
                <div className="bg-white/80 rounded p-2.5 border">
                  <span className="font-semibold block">Attribute Commitment:</span>
                  <Badge tone={result.commitmentValid !== false ? 'success' : 'danger'}>
                    {result.commitmentValid !== false ? 'Bound to Credential' : 'Unbound / Forged'}
                  </Badge>
                </div>
                <div className="bg-white/80 rounded p-2.5 border">
                  <span className="font-semibold block">Issuer RSA-PSS Signature:</span>
                  <Badge tone={result.signatureValid !== false ? 'success' : 'danger'}>
                    {result.signatureValid !== false ? 'Valid Signature' : 'Invalid Signature'}
                  </Badge>
                </div>
              </div>

              <div className="rounded-lg bg-white/70 p-3 text-xs text-ink-700 border">
                <strong>Zero-Knowledge Assurance:</strong> You have authoritatively verified that the holder's credential
                satisfies <code>{attributeName} &gt;= {threshold}</code>. At no point was the true attribute value or blinding factor disclosed.
              </div>
            </div>
          )}

          <div className="mt-5 space-y-4">
            <div className="rounded-lg border border-ink-200 bg-ink-50/50 p-4">
              <label className="text-xs font-semibold uppercase tracking-wider text-ink-600 block mb-1">
                Quick Paste Proof JSON
              </label>
              <div className="flex gap-2">
                <textarea
                  placeholder='Paste JSON containing {"proofVersion": 2, "credentialId": "...", "attributeName": "...", "threshold": 300, ...}'
                  value={jsonInput}
                  onChange={(e) => setJsonInput(e.target.value)}
                  className="min-h-16 flex-1 rounded-lg border border-ink-300 bg-white p-2 font-mono text-xs text-ink-900"
                />
                <Button size="sm" variant="secondary" onClick={handleLoadFromJson} className="self-end">
                  <FileCode className="size-4" />
                  Load Fields
                </Button>
              </div>
            </div>

            <form onSubmit={handleVerify} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input
                  label="Proof Version"
                  type="number"
                  value={proofVersion}
                  onChange={(e) => setProofVersion(Number(e.target.value))}
                  required
                />
                <Input
                  label="Credential ID"
                  placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
                  value={credentialId}
                  onChange={(e) => setCredentialId(e.target.value)}
                  required
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input
                  label="Attribute Name"
                  placeholder="e.g. gpa"
                  value={attributeName}
                  onChange={(e) => setAttributeName(e.target.value)}
                  required
                />
                <Input
                  label="Threshold (Claim: Attribute >= Threshold)"
                  type="number"
                  value={threshold}
                  onChange={(e) => setThreshold(Number(e.target.value))}
                  required
                />
              </div>

              <Input
                label="Proof Nonce"
                placeholder="Cryptographic replay-protection nonce"
                value={nonce}
                onChange={(e) => setNonce(e.target.value)}
                required
              />

              <Textarea
                label="Proof Bytes (Base64)"
                placeholder="Bulletproof proof bytes"
                value={proofBytes}
                onChange={(e) => setProofBytes(e.target.value)}
                className="font-mono text-xs"
                required
              />

              <div className="pt-2">
                <Button type="submit" variant="primary" loading={loading}>
                  <Shield className="size-4" />
                  Verify Zero-Knowledge Predicate Proof
                </Button>
              </div>
            </form>
          </div>
        </Card>
      </div>
    </>
  );
}
