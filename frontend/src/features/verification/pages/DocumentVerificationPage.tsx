import { useState } from 'react';
import {
  CheckCircle2,
  FileCheck2,
  FileSearch,
  KeyRound,
  ShieldAlert,
  ShieldCheck,
  Upload,
  XCircle,
} from 'lucide-react';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Alert } from '../../../components/ui/Alert';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Input } from '../../../components/ui/Field';
import { Tabs } from '../../../components/ui/Tabs';
import { useAuth } from '../../auth/AuthContext';
import {
  verifyStoredDocumentCopy,
  verifyUploadedDocumentCopy,
  verifyCredential,
  type DocumentVerificationResult,
  type CredentialVerificationResult,
} from '../api/authoritativeVerificationApi';

export function DocumentVerificationPage() {
  const { isAuthenticated, session } = useAuth();

  // Stored Doc State
  const [storedDocId, setStoredDocId] = useState('');
  const [storedDocLoading, setStoredDocLoading] = useState(false);
  const [storedDocResult, setStoredDocResult] = useState<DocumentVerificationResult | null>(null);
  const [storedDocError, setStoredDocError] = useState<string | null>(null);

  // Uploaded Doc State
  const [uploadDocId, setUploadDocId] = useState('');
  const [verifyFile, setVerifyFile] = useState<File | null>(null);
  const [uploadDocLoading, setUploadDocLoading] = useState(false);
  const [uploadDocResult, setUploadDocResult] = useState<DocumentVerificationResult | null>(null);
  const [uploadDocError, setUploadDocError] = useState<string | null>(null);

  // Credential Verify State
  const [credentialId, setCredentialId] = useState('');
  const [asOfTimestamp, setAsOfTimestamp] = useState('');
  const [credLoading, setCredLoading] = useState(false);
  const [credResult, setCredResult] = useState<CredentialVerificationResult | null>(null);
  const [credError, setCredError] = useState<string | null>(null);

  const handleVerifyStoredDoc = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!storedDocId.trim()) return;
    setStoredDocLoading(true);
    setStoredDocError(null);
    setStoredDocResult(null);

    try {
      const res = await verifyStoredDocumentCopy(storedDocId.trim());
      setStoredDocResult(res);
    } catch (err) {
      setStoredDocError(err instanceof Error ? err.message : 'Verification failed');
    } finally {
      setStoredDocLoading(false);
    }
  };

  const handleVerifyUploadedDoc = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uploadDocId.trim() || !verifyFile) return;
    setUploadDocLoading(true);
    setUploadDocError(null);
    setUploadDocResult(null);

    try {
      const res = await verifyUploadedDocumentCopy(uploadDocId.trim(), verifyFile);
      setUploadDocResult(res);
    } catch (err) {
      setUploadDocError(err instanceof Error ? err.message : 'Upload copy verification failed');
    } finally {
      setUploadDocLoading(false);
    }
  };

  const handleVerifyCredential = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!credentialId.trim()) return;
    setCredLoading(true);
    setCredError(null);
    setCredResult(null);

    try {
      const res = await verifyCredential(
        credentialId.trim(),
        asOfTimestamp.trim() || undefined,
      );
      setCredResult(res);
    } catch (err) {
      setCredError(err instanceof Error ? err.message : 'Credential verification failed');
    } finally {
      setCredLoading(false);
    }
  };

  const storedDocTab = (
    <Card className="p-6 space-y-5">
      <div className="flex items-center gap-3 border-b border-ink-100 pb-4">
        <span className="inline-flex size-10 items-center justify-center rounded-lg bg-accent-50 text-accent-700">
          <FileSearch className="size-5" />
        </span>
        <div>
          <h2 className="text-base font-semibold text-ink-900">Verify Stored Document Integrity</h2>
          <p className="text-xs text-ink-600">
            Recomputes the SHA-256 hash of the stored document and cryptographically validates the RSA-PSS signature against the public key registry.
          </p>
        </div>
      </div>

      {storedDocError && (
        <Alert tone="danger" title="Verification Error">
          {storedDocError}
        </Alert>
      )}

      {storedDocResult && (
        <div
          className={`rounded-xl border p-5 ${
            storedDocResult.verified
              ? 'border-success-200 bg-success-50 text-success-900'
              : 'border-danger-200 bg-danger-50 text-danger-900'
          }`}
        >
          <div className="flex items-center gap-2 mb-3">
            {storedDocResult.verified ? (
              <CheckCircle2 className="size-5 text-success-700" />
            ) : (
              <XCircle className="size-5 text-danger-700" />
            )}
            <h3 className="text-sm font-semibold">
              {storedDocResult.verified
                ? 'Document Authenticity Authoritatively Verified'
                : 'Document Verification Failed'}
            </h3>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div className="bg-white/80 rounded p-2.5 border">
              <span className="font-semibold block">RSA-PSS Signature:</span>
              <Badge tone={storedDocResult.signatureValid ? 'success' : 'danger'}>
                {storedDocResult.signatureValid ? 'Valid Signature' : 'Invalid Signature'}
              </Badge>
            </div>
            <div className="bg-white/80 rounded p-2.5 border">
              <span className="font-semibold block">Document Lifecycle Status:</span>
              <Badge tone={storedDocResult.documentActive ? 'success' : 'warning'}>
                {storedDocResult.documentActive ? 'Active' : 'Inactive / Revoked'}
              </Badge>
            </div>
          </div>

          {storedDocResult.storedDigestHex && (
            <div className="mt-3 text-[11px] font-mono break-all bg-white/70 p-2 rounded border">
              <span className="font-semibold">SHA-256 Digest:</span> {storedDocResult.storedDigestHex}
            </div>
          )}
        </div>
      )}

      <form onSubmit={handleVerifyStoredDoc} className="space-y-4">
        <Input
          label="Document UUID"
          placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
          value={storedDocId}
          onChange={(e) => setStoredDocId(e.target.value)}
          required
        />
        <Button type="submit" variant="primary" loading={storedDocLoading}>
          <ShieldCheck className="size-4" />
          Verify Stored Copy
        </Button>
      </form>
    </Card>
  );

  const uploadedDocTab = (
    <Card className="p-6 space-y-5">
      <div className="flex items-center gap-3 border-b border-ink-100 pb-4">
        <span className="inline-flex size-10 items-center justify-center rounded-lg bg-accent-50 text-accent-700">
          <Upload className="size-5" />
        </span>
        <div>
          <h2 className="text-base font-semibold text-ink-900">Verify Uploaded Copy Match</h2>
          <p className="text-xs text-ink-600">
            Upload a candidate file to authoritatively verify whether its cryptographic hash matches the registered signed document.
          </p>
        </div>
      </div>

      {uploadDocError && (
        <Alert tone="danger" title="Verification Error">
          {uploadDocError}
        </Alert>
      )}

      {uploadDocResult && (
        <div
          className={`rounded-xl border p-5 ${
            uploadDocResult.verified
              ? 'border-success-200 bg-success-50 text-success-900'
              : 'border-danger-200 bg-danger-50 text-danger-900'
          }`}
        >
          <div className="flex items-center gap-2 mb-2">
            {uploadDocResult.verified ? (
              <CheckCircle2 className="size-5 text-success-700" />
            ) : (
              <XCircle className="size-5 text-danger-700" />
            )}
            <h3 className="text-sm font-semibold">
              {uploadDocResult.verified
                ? 'Uploaded File Byte-Exact Hash Match & Authentic'
                : 'File Hash Mismatch or Unauthentic Document'}
            </h3>
          </div>
          <p className="text-xs">
            Signature: {uploadDocResult.signatureValid ? 'VALID' : 'INVALID'} · Status:{' '}
            {uploadDocResult.documentActive ? 'ACTIVE' : 'INACTIVE'}
          </p>
        </div>
      )}

      <form onSubmit={handleVerifyUploadedDoc} className="space-y-4">
        <Input
          label="Document UUID"
          placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
          value={uploadDocId}
          onChange={(e) => setUploadDocId(e.target.value)}
          required
        />
        <div>
          <label className="text-sm font-semibold text-ink-800 mb-1.5 block">File to Compare</label>
          <input
            type="file"
            onChange={(e) => setVerifyFile(e.target.files?.[0] || null)}
            className="block w-full text-sm text-ink-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-accent-50 file:text-accent-700 hover:file:bg-accent-100"
            required
          />
        </div>
        <Button type="submit" variant="primary" loading={uploadDocLoading} disabled={!verifyFile}>
          <FileCheck2 className="size-4" />
          Compare & Verify Document
        </Button>
      </form>
    </Card>
  );

  const credentialTab = (
    <Card className="p-6 space-y-5">
      <div className="flex items-center gap-3 border-b border-ink-100 pb-4">
        <span className="inline-flex size-10 items-center justify-center rounded-lg bg-accent-50 text-accent-700">
          <FileCheck2 className="size-5" />
        </span>
        <div>
          <h2 className="text-base font-semibold text-ink-900">Verify Credential Authenticity</h2>
          <p className="text-xs text-ink-600">
            Authoritatively verifies issuer signature and checks operational status (ACTIVE vs SUSPENDED/REVOKED), optionally as of a historical date.
          </p>
        </div>
      </div>

      {credError && (
        <Alert tone="danger" title="Credential Verification Error">
          {credError}
        </Alert>
      )}

      {credResult && (
        <div
          className={`rounded-xl border p-5 ${
            credResult.verified
              ? 'border-success-200 bg-success-50 text-success-900'
              : 'border-danger-200 bg-danger-50 text-danger-900'
          }`}
        >
          <div className="flex items-center gap-2 mb-3">
            {credResult.verified ? (
              <CheckCircle2 className="size-5 text-success-700" />
            ) : (
              <XCircle className="size-5 text-danger-700" />
            )}
            <h3 className="text-sm font-semibold">
              {credResult.verified
                ? 'Credential Valid, Authentic, and Operational'
                : 'Credential Invalid, Inactive, or Unverifiable'}
            </h3>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div className="bg-white/80 rounded p-2.5 border">
              <span className="font-semibold block">Issuer Signature:</span>
              <Badge tone={credResult.signatureValid ? 'success' : 'danger'}>
                {credResult.signatureValid ? 'Signature Valid' : 'Signature Invalid'}
              </Badge>
            </div>
            <div className="bg-white/80 rounded p-2.5 border">
              <span className="font-semibold block">Operational Status:</span>
              <Badge tone={credResult.operationalStatusValid ? 'success' : 'warning'}>
                {credResult.operationalStatusValid ? 'Valid at Timestamp' : 'Invalid Status'}
              </Badge>
            </div>
          </div>
        </div>
      )}

      <form onSubmit={handleVerifyCredential} className="space-y-4">
        <Input
          label="Credential UUID"
          placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
          value={credentialId}
          onChange={(e) => setCredentialId(e.target.value)}
          required
        />
        <Input
          label="Historical As-Of Timestamp (Optional ISO-8601)"
          placeholder="e.g. 2026-10-14T00:00:00Z"
          value={asOfTimestamp}
          onChange={(e) => setAsOfTimestamp(e.target.value)}
          hint="Leave blank to verify against current real-time status"
        />
        <Button type="submit" variant="primary" loading={credLoading}>
          <ShieldCheck className="size-4" />
          Verify Credential
        </Button>
      </form>
    </Card>
  );

  return (
    <>
      <PageHeader
        title="Verification"
        description="Verify document integrity, cryptographic digital signatures, and historical credential validity."
        breadcrumbs={[{ label: 'Overview', to: '/' }, { label: 'Verification' }]}
        actions={
          isAuthenticated ? (
            <Badge tone="success">
              {session?.roles[0]} · Authenticated
            </Badge>
          ) : (
            <Badge tone="neutral">Public or Token Context</Badge>
          )
        }
      />

      <div className="mb-6">
        <Tabs
          items={[
            { id: 'doc-server', label: 'Verify Stored Document', content: storedDocTab },
            { id: 'doc-upload', label: 'Verify Uploaded Document Copy', content: uploadedDocTab },
            { id: 'credential', label: 'Verify Credential Authenticity', content: credentialTab },
          ]}
        />
      </div>
    </>
  );
}
