import { useState, useEffect } from 'react';
import {
  FileCheck,
  FilePlus2,
  FolderLock,
  History,
  KeyRound,
  Plus,
  QrCode,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  Upload,
  Copy,
  Check,
} from 'lucide-react';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Alert } from '../../../components/ui/Alert';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Input, Select } from '../../../components/ui/Field';
import { Tabs } from '../../../components/ui/Tabs';
import { Dialog } from '../../../components/ui/Dialog';
import { Spinner } from '../../../components/ui/Spinner';
import { useAuth } from '../../auth/AuthContext';
import {
  issueCredential,
  uploadDocument,
  fetchDocumentQrBlob,
  getCredentialLifecycle,
  getCredentialHistory,
  suspendCredential,
  resumeCredential,
  revokeCredential,
  listHolderCredentials,
  provisionHolderCredential,
  generateHolderPredicateProof,
  type CredentialAttributeDto,
  type CredentialLifecycleResponse,
  type CredentialLifecycleEvent,
  type HolderCredentialDto,
  type HolderPredicateProofResponse,
} from '../api/credentialsApi';

export function CredentialsPage() {
  const { session, isAuthenticated, hasRole, connectPreset } = useAuth();

  // Issue Credential state
  const [issuerId, setIssuerId] = useState(session?.sub || 'acme-corp');
  const [subjectId, setSubjectId] = useState('');
  const [attributes, setAttributes] = useState<CredentialAttributeDto[]>([
    { name: 'gpa', value: 380, type: 'INTEGER' },
    { name: 'clearanceLevel', value: 3, type: 'INTEGER' },
  ]);
  const [issueLoading, setIssueLoading] = useState(false);
  const [issueError, setIssueError] = useState<string | null>(null);
  const [issuedResult, setIssuedResult] = useState<{ credentialId: string; digest: string } | null>(null);

  // Document Upload state
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadResult, setUploadResult] = useState<{ id: string; digest: string } | null>(null);
  const [qrBlobUrl, setQrBlobUrl] = useState<string | null>(null);

  // Lifecycle state
  const [lifecycleIdInput, setLifecycleIdInput] = useState('');
  const [lifecycleStatus, setLifecycleStatus] = useState<CredentialLifecycleResponse | null>(null);
  const [lifecycleHistory, setLifecycleHistory] = useState<CredentialLifecycleEvent[]>([]);
  const [lifecycleLoading, setLifecycleLoading] = useState(false);
  const [lifecycleActionLoading, setLifecycleActionLoading] = useState(false);
  const [lifecycleError, setLifecycleError] = useState<string | null>(null);

  // Holder state
  const [provisionIdInput, setProvisionIdInput] = useState('');
  const [holderCredentials, setHolderCredentials] = useState<HolderCredentialDto[]>([]);
  const [holderLoading, setHolderLoading] = useState(false);
  const [holderError, setHolderError] = useState<string | null>(null);

  // Proof Generation Modal state
  const [proofModalOpen, setProofModalOpen] = useState(false);
  const [selectedHolderCred, setSelectedHolderCred] = useState<HolderCredentialDto | null>(null);
  const [proofAttributeName, setProofAttributeName] = useState('gpa');
  const [proofThreshold, setProofThreshold] = useState<number>(300);
  const [proofLoading, setProofLoading] = useState(false);
  const [proofError, setProofError] = useState<string | null>(null);
  const [generatedProof, setGeneratedProof] = useState<HolderPredicateProofResponse | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (session?.sub) {
      setIssuerId(session.sub);
    }
  }, [session]);

  useEffect(() => {
    if (isAuthenticated && hasRole('HOLDER')) {
      loadHolderCredentials();
    }
  }, [isAuthenticated]);

  const loadHolderCredentials = async () => {
    setHolderLoading(true);
    setHolderError(null);
    try {
      const items = await listHolderCredentials();
      setHolderCredentials(items);
    } catch (err) {
      setHolderError(err instanceof Error ? err.message : 'Failed to load holder credentials');
    } finally {
      setHolderLoading(false);
    }
  };

  const handleAddAttribute = () => {
    setAttributes([...attributes, { name: '', value: 0, type: 'INTEGER' }]);
  };

  const handleRemoveAttribute = (idx: number) => {
    setAttributes(attributes.filter((_, i) => i !== idx));
  };

  const handleAttributeChange = (idx: number, field: keyof CredentialAttributeDto, val: any) => {
    const updated = [...attributes];
    updated[idx] = { ...updated[idx], [field]: val };
    setAttributes(updated);
  };

  const handleIssueCredential = async (e: React.FormEvent) => {
    e.preventDefault();
    setIssueLoading(true);
    setIssueError(null);
    setIssuedResult(null);

    try {
      if (!issuerId.trim()) throw new Error('Issuer ID is required.');
      const validAttrs = attributes.filter((a) => a.name.trim() !== '');
      if (validAttrs.length === 0) throw new Error('At least one named attribute is required.');

      const res = await issueCredential({
        issuerId: issuerId.trim(),
        subjectId: subjectId.trim() || undefined,
        attributes: validAttrs.map((a) => ({
          name: a.name.trim(),
          value: Number(a.value),
          type: 'INTEGER',
        })),
      });

      setIssuedResult(res);
      setLifecycleIdInput(res.credentialId);
    } catch (err) {
      setIssueError(err instanceof Error ? err.message : 'Issuance failed');
    } finally {
      setIssueLoading(false);
    }
  };

  const handleUploadDocument = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uploadFile) return;

    setUploadLoading(true);
    setUploadError(null);
    setUploadResult(null);
    if (qrBlobUrl) {
      URL.revokeObjectURL(qrBlobUrl);
      setQrBlobUrl(null);
    }

    try {
      const res = await uploadDocument(uploadFile);
      setUploadResult(res);

      try {
        const qrBlob = await fetchDocumentQrBlob(res.id);
        const url = URL.createObjectURL(qrBlob);
        setQrBlobUrl(url);
      } catch {
        // QR fetch is non-blocking
      }
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : 'Document upload failed');
    } finally {
      setUploadLoading(false);
    }
  };

  const handleQueryLifecycle = async () => {
    if (!lifecycleIdInput.trim()) return;
    setLifecycleLoading(true);
    setLifecycleError(null);
    try {
      const [status, history] = await Promise.all([
        getCredentialLifecycle(lifecycleIdInput.trim()),
        getCredentialHistory(lifecycleIdInput.trim()),
      ]);
      setLifecycleStatus(status);
      setLifecycleHistory(history);
    } catch (err) {
      setLifecycleError(err instanceof Error ? err.message : 'Failed to query credential lifecycle');
    } finally {
      setLifecycleLoading(false);
    }
  };

  const handleLifecycleAction = async (action: 'suspend' | 'resume' | 'revoke') => {
    if (!lifecycleIdInput.trim()) return;
    setLifecycleActionLoading(true);
    setLifecycleError(null);
    try {
      const reason = `Administrative ${action} via frontend`;
      let res: CredentialLifecycleResponse;
      if (action === 'suspend') res = await suspendCredential(lifecycleIdInput.trim(), reason);
      else if (action === 'resume') res = await resumeCredential(lifecycleIdInput.trim(), reason);
      else res = await revokeCredential(lifecycleIdInput.trim(), reason);

      setLifecycleStatus(res);
      const history = await getCredentialHistory(lifecycleIdInput.trim());
      setLifecycleHistory(history);
    } catch (err) {
      setLifecycleError(err instanceof Error ? err.message : `Failed to ${action} credential`);
    } finally {
      setLifecycleActionLoading(false);
    }
  };

  const handleProvision = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!provisionIdInput.trim()) return;
    setHolderLoading(true);
    setHolderError(null);
    try {
      await provisionHolderCredential(provisionIdInput.trim());
      setProvisionIdInput('');
      await loadHolderCredentials();
    } catch (err) {
      setHolderError(err instanceof Error ? err.message : 'Provisioning failed');
    } finally {
      setHolderLoading(false);
    }
  };

  const handleOpenProofModal = (cred: HolderCredentialDto) => {
    setSelectedHolderCred(cred);
    setProofAttributeName(cred.attributes?.[0]?.name || 'gpa');
    setProofThreshold(300);
    setGeneratedProof(null);
    setProofError(null);
    setProofModalOpen(true);
  };

  const handleGenerateProof = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedHolderCred) return;
    setProofLoading(true);
    setProofError(null);
    try {
      const res = await generateHolderPredicateProof({
        holderCredentialId: selectedHolderCred.id,
        attributeName: proofAttributeName.trim(),
        threshold: Number(proofThreshold),
      });
      setGeneratedProof(res);
    } catch (err) {
      setProofError(err instanceof Error ? err.message : 'Proof generation failed');
    } finally {
      setProofLoading(false);
    }
  };

  const handleCopyProofJson = () => {
    if (!generatedProof) return;
    navigator.clipboard.writeText(JSON.stringify(generatedProof, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const issueTab = (
    <div className="space-y-6">
      <Card className="p-6">
        <div className="flex items-center gap-3 border-b border-ink-100 pb-4">
          <span className="inline-flex size-10 items-center justify-center rounded-lg bg-accent-50 text-accent-700">
            <FilePlus2 className="size-5" />
          </span>
          <div>
            <h2 className="text-base font-semibold text-ink-900">Issue Authenticated Credential</h2>
            <p className="text-xs text-ink-600">
              Issues a canonical RSA-PSS signed credential with discrete attribute commitments (Pedersen commitments).
            </p>
          </div>
        </div>

        {issueError && (
          <Alert tone="danger" title="Issuance Failed" className="mt-4">
            {issueError}
          </Alert>
        )}

        {issuedResult && (
          <Alert tone="success" title="Credential Issued Successfully" className="mt-4">
            <div className="space-y-2 text-xs">
              <p>
                <span className="font-semibold">Credential ID:</span>{' '}
                <span className="font-mono">{issuedResult.credentialId}</span>
              </p>
              <p>
                <span className="font-semibold">Canonical SHA-256 Digest:</span>{' '}
                <span className="font-mono break-all">{issuedResult.digest}</span>
              </p>
            </div>
          </Alert>
        )}

        <form onSubmit={handleIssueCredential} className="mt-5 space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input
              label="Issuer ID"
              hint="Authority identifier matching authenticated principal"
              value={issuerId}
              onChange={(e) => setIssuerId(e.target.value)}
              required
            />
            <Input
              label="Subject / Holder ID (Optional UUID)"
              hint="Target holder subject identifier"
              placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
              value={subjectId}
              onChange={(e) => setSubjectId(e.target.value)}
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-sm font-semibold text-ink-800">
                Credential Attributes (Pedersen Commitments)
              </label>
              <Button type="button" size="sm" variant="secondary" onClick={handleAddAttribute}>
                <Plus className="size-3.5" />
                Add Attribute
              </Button>
            </div>
            <div className="space-y-2">
              {attributes.map((attr, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  <Input
                    label=""
                    placeholder="Attribute Name (e.g. gpa)"
                    value={attr.name}
                    onChange={(e) => handleAttributeChange(idx, 'name', e.target.value)}
                    className="flex-1"
                    required
                  />
                  <Input
                    label=""
                    type="number"
                    placeholder="Integer Value"
                    value={attr.value}
                    onChange={(e) => handleAttributeChange(idx, 'value', e.target.value)}
                    className="w-32"
                    required
                  />
                  {attributes.length > 1 && (
                    <button
                      type="button"
                      onClick={() => handleRemoveAttribute(idx)}
                      className="p-2 text-ink-400 hover:text-danger-600 transition"
                      aria-label="Remove attribute"
                    >
                      <Trash2 className="size-4" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          <div className="pt-2">
            <Button type="submit" variant="primary" loading={issueLoading} disabled={!isAuthenticated}>
              <ShieldCheck className="size-4" />
              Issue & Sign Credential
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );

  const uploadTab = (
    <div className="space-y-6">
      <Card className="p-6">
        <div className="flex items-center gap-3 border-b border-ink-100 pb-4">
          <span className="inline-flex size-10 items-center justify-center rounded-lg bg-accent-50 text-accent-700">
            <Upload className="size-5" />
          </span>
          <div>
            <h2 className="text-base font-semibold text-ink-900">Upload Document & Mint QR Code</h2>
            <p className="text-xs text-ink-600">
              Uploads an authentic document, stores its SHA-256 hash, and generates a zero-disclosure public verification QR capability.
            </p>
          </div>
        </div>

        {uploadError && (
          <Alert tone="danger" title="Upload Failed" className="mt-4">
            {uploadError}
          </Alert>
        )}

        {uploadResult && (
          <Alert tone="success" title="Document Uploaded Successfully" className="mt-4">
            <div className="space-y-3 text-xs">
              <p>
                <span className="font-semibold">Document ID:</span>{' '}
                <span className="font-mono">{uploadResult.id}</span>
              </p>
              <p>
                <span className="font-semibold">SHA-256 Digest:</span>{' '}
                <span className="font-mono break-all">{uploadResult.digest}</span>
              </p>
              {qrBlobUrl && (
                <div className="mt-3 flex flex-col items-center sm:flex-row gap-4 p-3 bg-white rounded-lg border border-ink-200">
                  <img src={qrBlobUrl} alt="Public verification QR capability" className="size-32 rounded border" />
                  <div className="space-y-2">
                    <p className="font-semibold text-ink-900">Verification Capability QR</p>
                    <p className="text-ink-600">
                      Contains an opaque 256-bit token. Does not leak file contents, signatures, or identities.
                    </p>
                    <a
                      href={qrBlobUrl}
                      download={`document-${uploadResult.id}-qr.png`}
                      className="inline-flex items-center gap-1.5 text-xs font-semibold text-accent-700 hover:underline"
                    >
                      <QrCode className="size-4" />
                      Download QR PNG
                    </a>
                  </div>
                </div>
              )}
            </div>
          </Alert>
        )}

        <form onSubmit={handleUploadDocument} className="mt-5 space-y-4">
          <div>
            <label className="text-sm font-semibold text-ink-800 mb-1.5 block">Select File</label>
            <input
              type="file"
              onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
              className="block w-full text-sm text-ink-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-accent-50 file:text-accent-700 hover:file:bg-accent-100"
              required
            />
          </div>

          <div className="pt-2">
            <Button type="submit" variant="primary" loading={uploadLoading} disabled={!uploadFile || !isAuthenticated}>
              <Upload className="size-4" />
              Upload & Generate Capability
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );

  const lifecycleTab = (
    <div className="space-y-6">
      <Card className="p-6">
        <h2 className="text-base font-semibold text-ink-900 mb-3">Credential Lifecycle Controls</h2>
        <div className="flex gap-2 mb-4">
          <Input
            label=""
            placeholder="Enter Credential UUID"
            value={lifecycleIdInput}
            onChange={(e) => setLifecycleIdInput(e.target.value)}
            className="flex-1"
          />
          <Button variant="secondary" onClick={handleQueryLifecycle} loading={lifecycleLoading}>
            Inspect Status
          </Button>
        </div>

        {lifecycleError && (
          <Alert tone="danger" title="Lifecycle Query Error" className="mb-4">
            {lifecycleError}
          </Alert>
        )}

        {lifecycleStatus && (
          <div className="space-y-4 border rounded-lg p-4 bg-ink-50/50">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-xs text-ink-500 font-mono">ID: {lifecycleStatus.credentialId}</span>
                <h3 className="text-sm font-semibold text-ink-900">Version {lifecycleStatus.version}</h3>
              </div>
              <Badge
                tone={
                  lifecycleStatus.status === 'ACTIVE'
                    ? 'success'
                    : lifecycleStatus.status === 'SUSPENDED'
                    ? 'warning'
                    : 'danger'
                }
              >
                {lifecycleStatus.status}
              </Badge>
            </div>

            <div className="flex flex-wrap gap-2 pt-2 border-t">
              <Button
                size="sm"
                variant="secondary"
                loading={lifecycleActionLoading}
                disabled={lifecycleStatus.status === 'SUSPENDED' || lifecycleStatus.status === 'REVOKED'}
                onClick={() => handleLifecycleAction('suspend')}
              >
                Suspend Credential
              </Button>
              <Button
                size="sm"
                variant="secondary"
                loading={lifecycleActionLoading}
                disabled={lifecycleStatus.status === 'ACTIVE' || lifecycleStatus.status === 'REVOKED'}
                onClick={() => handleLifecycleAction('resume')}
              >
                Resume Credential
              </Button>
              <Button
                size="sm"
                variant="danger"
                loading={lifecycleActionLoading}
                disabled={lifecycleStatus.status === 'REVOKED'}
                onClick={() => handleLifecycleAction('revoke')}
              >
                Revoke Permanently
              </Button>
            </div>

            {lifecycleHistory.length > 0 && (
              <div className="mt-4 pt-3 border-t">
                <p className="text-xs font-semibold uppercase text-ink-500 mb-2">Transition History</p>
                <div className="space-y-1.5">
                  {lifecycleHistory.map((evt, i) => (
                    <div key={i} className="text-xs text-ink-700 flex justify-between p-2 bg-white rounded border">
                      <span>
                        <strong>{evt.fromStatus}</strong> → <strong>{evt.toStatus}</strong> by{' '}
                        <span className="font-mono">{evt.actor}</span> ({evt.role})
                      </span>
                      <span className="text-ink-500">{new Date(evt.timestamp).toLocaleString()}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </Card>
    </div>
  );

  const holderTab = (
    <div className="space-y-6">
      <Card className="p-6">
        <h2 className="text-base font-semibold text-ink-900 mb-2">Holder Wallet & Credential Storage</h2>
        <p className="text-xs text-ink-600 mb-4">
          Holders hold credentials and attribute blinding factors locally. Zero-Knowledge Predicate Proofs allow proving claims (e.g. <code>GPA &gt;= 3.0</code>) without revealing raw numbers.
        </p>

        <form onSubmit={handleProvision} className="flex gap-2 mb-6">
          <Input
            label=""
            placeholder="Provision Credential UUID into Holder Wallet"
            value={provisionIdInput}
            onChange={(e) => setProvisionIdInput(e.target.value)}
            className="flex-1"
            required
          />
          <Button type="submit" variant="primary" loading={holderLoading} disabled={!isAuthenticated}>
            Provision Credential
          </Button>
        </form>

        {holderError && (
          <Alert tone="danger" title="Holder Error" className="mb-4">
            {holderError}
          </Alert>
        )}

        {holderCredentials.length === 0 ? (
          <div className="text-center py-8 text-sm text-ink-500 border rounded-lg bg-ink-50/50">
            No credentials provisioned for current holder. Provision a credential ID above.
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4">
            {holderCredentials.map((c) => (
              <div key={c.id} className="border rounded-xl p-4 bg-white shadow-xs flex flex-col justify-between gap-3">
                <div className="flex items-start justify-between">
                  <div>
                    <span className="text-xs font-mono text-ink-500">ID: {c.id}</span>
                    <p className="text-sm font-semibold text-ink-900">Credential: {c.credentialId}</p>
                    <p className="text-xs text-ink-600">Issuer: {c.issuerId}</p>
                  </div>
                  <Badge tone={c.status === 'ACTIVE' ? 'success' : 'danger'}>{c.status}</Badge>
                </div>

                <div className="flex justify-end gap-2 border-t pt-3">
                  <Button size="sm" variant="primary" onClick={() => handleOpenProofModal(c)}>
                    <Shield className="size-3.5" />
                    Generate ZKP Predicate Proof
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );

  return (
    <>
      <PageHeader
        title="Credentials"
        description="Issue signed credentials with attribute commitments, manage lifecycle transitions, and generate zero-knowledge predicate proofs."
        breadcrumbs={[{ label: 'Overview', to: '/' }, { label: 'Credentials' }]}
        actions={
          isAuthenticated ? (
            <Badge tone="success">
              {session?.roles[0]} · {session?.sub}
            </Badge>
          ) : (
            <Badge tone="warning">No active session</Badge>
          )
        }
      />

      {!isAuthenticated ? (
        <Alert
          tone="warning"
          title="Credentials workflow is not connected yet"
          className="mb-6"
        >
          <p>
            An authenticated cryptographic session is required to issue credentials, upload documents, or manage holder
            records. You can connect a quick role profile below to test each workflow immediately.
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button size="sm" variant="secondary" onClick={() => connectPreset('ISSUER')}>
              Connect as Issuer (acme-corp)
            </Button>
            <Button size="sm" variant="secondary" onClick={() => connectPreset('HOLDER')}>
              Connect as Holder (holder-bob)
            </Button>
            <Button size="sm" variant="secondary" onClick={() => connectPreset('ADMIN')}>
              Connect as Administrator
            </Button>
          </div>
        </Alert>
      ) : null}

      <div className="mb-6">
        <Tabs
          items={[
            { id: 'issue', label: 'Issue Credential', content: issueTab },
            { id: 'upload', label: 'Upload Document & QR', content: uploadTab },
            { id: 'lifecycle', label: 'Lifecycle Management', content: lifecycleTab },
            { id: 'holder', label: 'Holder Wallet & ZKP Prover', content: holderTab },
          ]}
        />
      </div>

      {/* Proof Generator Dialog */}
      <Dialog
        open={proofModalOpen}
        onClose={() => setProofModalOpen(false)}
        title="Generate Zero-Knowledge Predicate Proof"
        description="Prove that an attribute satisfies a threshold condition without exposing the attribute value or witness."
        footer={
          <Button variant="secondary" size="sm" onClick={() => setProofModalOpen(false)}>
            Close
          </Button>
        }
      >
        <div className="space-y-4">
          {proofError && (
            <Alert tone="danger" title="Proof Generation Error">
              {proofError}
            </Alert>
          )}

          {generatedProof ? (
            <div className="space-y-3">
              <Alert tone="success" title="Zero-Knowledge Proof Generated">
                <p className="text-xs">
                  Bulletproof range proof and commitment binding generated. The raw attribute and blinding factor remain private to you.
                </p>
              </Alert>

              <div className="rounded-lg bg-ink-900 p-3 font-mono text-xs text-ink-100 overflow-x-auto space-y-1">
                <p><strong>Proof Version:</strong> {generatedProof.proofVersion}</p>
                <p><strong>Credential ID:</strong> {generatedProof.credentialId}</p>
                <p><strong>Claim:</strong> {generatedProof.attributeName} &gt;= {generatedProof.threshold}</p>
                <p><strong>Nonce:</strong> {generatedProof.nonce}</p>
                <p className="break-all"><strong>Proof Bytes:</strong> {generatedProof.proofBytes.slice(0, 80)}...</p>
              </div>

              <div className="flex gap-2">
                <Button size="sm" variant="secondary" onClick={handleCopyProofJson} className="w-full">
                  {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
                  {copied ? 'Copied to Clipboard' : 'Copy Proof JSON'}
                </Button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleGenerateProof} className="space-y-4">
              <Input
                label="Attribute Name"
                hint="Name of numerical attribute committed in credential"
                value={proofAttributeName}
                onChange={(e) => setProofAttributeName(e.target.value)}
                required
              />
              <Input
                label="Threshold"
                hint="Claim to verify (Attribute >= Threshold)"
                type="number"
                value={proofThreshold}
                onChange={(e) => setProofThreshold(Number(e.target.value))}
                required
              />

              <div className="rounded-lg bg-info-50 border border-info-100 p-3 text-xs text-info-800">
                <strong>ZKP Privacy Guarantee:</strong> The generated cryptographic proof proves that the committed attribute is greater than or equal to the threshold using Bulletproofs. The verifier cannot extract the true attribute value.
              </div>

              <Button type="submit" variant="primary" loading={proofLoading} className="w-full">
                <Shield className="size-4" />
                Generate Bulletproof Proof
              </Button>
            </form>
          )}
        </div>
      </Dialog>
    </>
  );
}
