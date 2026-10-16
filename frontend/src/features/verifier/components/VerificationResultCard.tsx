import React from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Badge } from '../../../components/common/Badge';
import { FormattedHash } from '../../../components/data/FormattedHash';
import type {
  UnifiedVerificationResult,
  VerifierErrorState,
} from '../../../state/verifierStore';
import {
  CheckCircle2,
  XCircle,
  AlertTriangle,
  ServerOff,
  Clock,
  ShieldCheck,
  EyeOff,
  ArrowLeft,
} from 'lucide-react';

interface VerificationResultCardProps {
  result: UnifiedVerificationResult | null;
  error: VerifierErrorState | null;
  onReset: () => void;
}

export const VerificationResultCard: React.FC<VerificationResultCardProps> = ({
  result,
  error,
  onReset,
}) => {
  if (!result && !error) {
    return null;
  }

  // Handle Operational or Network Errors
  if (error) {
    const isRateLimit = error.type === 'RATE_LIMITED';
    const isNotFound = error.type === 'NOT_FOUND';

    return (
      <Card className="border-slate-200">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center gap-2 text-lg text-slate-900">
              {isRateLimit ? (
                <Clock className="h-5 w-5 text-amber-500" />
              ) : isNotFound ? (
                <AlertTriangle className="h-5 w-5 text-amber-500" />
              ) : (
                <ServerOff className="h-5 w-5 text-rose-500" />
              )}
              {isRateLimit
                ? 'Verification Capacity Saturated'
                : isNotFound
                  ? 'Reference Not Found'
                  : 'Verification Service Unavailable'}
            </CardTitle>
            <Badge variant="warning">{error.type}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            <p className="font-medium">{error.message}</p>
            {!isNotFound && !isRateLimit && (
              <p className="mt-2 text-xs text-amber-800">
                <strong>Important Trust Notice:</strong> An operational failure,
                network timeout, or server unavailability does <em>not</em> mean
                the document or credential is fake or invalid. It simply means
                the cryptographic authority could not be contacted at this
                moment.
              </p>
            )}
          </div>

          {error.targetReference && (
            <div className="flex items-center gap-2 text-xs text-slate-600">
              <span>Target Reference:</span>
              <code className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-slate-800">
                {error.targetReference}
              </code>
            </div>
          )}

          <div className="flex justify-end pt-2">
            <Button
              variant="outline"
              onClick={onReset}
              className="flex items-center gap-2"
            >
              <ArrowLeft className="h-4 w-4" />
              Start New Verification
            </Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  // Render Authoritative Successful / Unsuccessful Verification Outcomes
  if (!result) return null;

  const isPublicCapability = result.kind === 'PUBLIC_CAPABILITY';
  const isPublicDoc = result.kind === 'PUBLIC_DOCUMENT_TOKEN';
  const isDocUpload = result.kind === 'DOCUMENT_UPLOAD';
  const isCredSignature = result.kind === 'CREDENTIAL_SIGNATURE';
  const isPredicate = result.kind === 'PREDICATE_PROOF';

  // Determine overall validity flag based on backend response
  let isValid = false;
  let statusText = 'INVALID';
  let failureReason: string | null = null;

  if (isPublicCapability) {
    isValid = result.response.verified;
    statusText = result.response.status || (isValid ? 'VALID' : 'INVALID');
    failureReason = result.response.failureReason;
  } else if (isPublicDoc) {
    isValid = result.response.signatureValid && result.response.documentActive;
    statusText = isValid ? 'VALID' : 'INVALID';
    if (!result.response.signatureValid)
      failureReason = 'Digital signature verification failed';
    else if (!result.response.documentActive)
      failureReason = 'Document inactive or expired';
  } else if (isDocUpload) {
    isValid = result.response.verified;
    statusText = isValid ? 'VALID' : 'MISMATCH';
    if (!isValid)
      failureReason =
        'Uploaded file hash does not match the stored cryptographic digest';
  } else if (isCredSignature) {
    isValid = result.response.verified;
    statusText = isValid ? 'VALID' : 'INVALID';
    failureReason =
      result.response.message ||
      (isValid ? null : 'Credential verification failed');
  } else if (isPredicate) {
    isValid = result.response.verified;
    statusText = isValid ? 'MATHEMATICALLY_VERIFIED' : 'FAILED_PROOF';
    if (!isValid)
      failureReason =
        'Proof constraint unsatisfied or invalid attestation signature';
  }

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-lg text-slate-900">
            <ShieldCheck
              className="h-5 w-5 text-emerald-600"
              aria-hidden="true"
            />
            Authoritative Verification Result
          </CardTitle>
          <div className="flex items-center gap-2">
            <Badge variant={isValid ? 'success' : 'danger'}>{statusText}</Badge>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Outcome Highlight Box */}
        <div
          className={`flex items-start gap-3 rounded-xl border p-4 ${
            isValid
              ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
              : 'border-rose-200 bg-rose-50 text-rose-900'
          }`}
        >
          {isValid ? (
            <CheckCircle2
              className="mt-0.5 h-6 w-6 shrink-0 text-emerald-600"
              aria-hidden="true"
            />
          ) : (
            <XCircle
              className="mt-0.5 h-6 w-6 shrink-0 text-rose-600"
              aria-hidden="true"
            />
          )}
          <div className="space-y-1">
            <h4 className="text-base font-semibold">
              {isValid
                ? 'Cryptographic Verification Passed'
                : 'Verification Failed or Unusable'}
            </h4>
            <p className="text-xs">
              {isValid
                ? 'The presented artifact was authoritatively verified against backend cryptographic trust anchors and operational status registries.'
                : failureReason ||
                  'The presented reference failed cryptographic or lifecycle validation checks.'}
            </p>
          </div>
        </div>

        {/* Verification Check Matrix */}
        <div className="space-y-3">
          <h4 className="text-xs font-semibold tracking-wider text-slate-800 uppercase">
            Evaluated Verification Checks
          </h4>
          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
            {/* Check 1: Digital Signature */}
            <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-2.5">
              <span className="text-xs font-medium text-slate-700">
                Digital Signature (RSA-PSS)
              </span>
              {isPublicCapability ? (
                <Badge
                  variant={
                    result.response.signatureValid ? 'success' : 'danger'
                  }
                >
                  {result.response.signatureValid ? 'VALID' : 'FAILED'}
                </Badge>
              ) : isPublicDoc ? (
                <Badge
                  variant={
                    result.response.signatureValid ? 'success' : 'danger'
                  }
                >
                  {result.response.signatureValid ? 'VALID' : 'FAILED'}
                </Badge>
              ) : isCredSignature ? (
                <Badge
                  variant={result.response.verified ? 'success' : 'danger'}
                >
                  {result.response.verified ? 'VALID' : 'FAILED'}
                </Badge>
              ) : (
                <Badge variant="neutral">EVALUATED</Badge>
              )}
            </div>

            {/* Check 2: Operational Lifecycle / Active */}
            <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-2.5">
              <span className="text-xs font-medium text-slate-700">
                Operational Status &amp; Validity
              </span>
              {isPublicCapability ? (
                <Badge variant={result.response.active ? 'success' : 'danger'}>
                  {result.response.active ? 'ACTIVE' : 'INACTIVE'}
                </Badge>
              ) : isPublicDoc ? (
                <Badge
                  variant={
                    result.response.documentActive ? 'success' : 'danger'
                  }
                >
                  {result.response.documentActive ? 'ACTIVE' : 'INACTIVE'}
                </Badge>
              ) : isCredSignature ? (
                <Badge
                  variant={
                    result.response.status === 'ACTIVE' ||
                    (!result.response.revokedAt &&
                      !result.response.suspendedAt &&
                      result.response.verified)
                      ? 'success'
                      : 'danger'
                  }
                >
                  {result.response.status ||
                    (result.response.verified ? 'ACTIVE' : 'INACTIVE')}
                </Badge>
              ) : (
                <Badge variant="neutral">VERIFIED</Badge>
              )}
            </div>

            {/* Check 3: Digest / Integrity */}
            <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-2.5">
              <span className="text-xs font-medium text-slate-700">
                SHA-256 Digest Integrity
              </span>
              {isDocUpload ? (
                <Badge
                  variant={result.response.verified ? 'success' : 'danger'}
                >
                  {result.response.verified ? 'MATCHES' : 'MISMATCH'}
                </Badge>
              ) : isCredSignature ? (
                <Badge
                  variant={result.response.verified ? 'success' : 'danger'}
                >
                  {result.response.verified ? 'MATCHES' : 'MISMATCH'}
                </Badge>
              ) : (
                <Badge variant="success">CONFIRMED</Badge>
              )}
            </div>

            {/* Check 4: Protocol / Mode */}
            <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-2.5">
              <span className="text-xs font-medium text-slate-700">
                Verification Protocol
              </span>
              <span className="font-mono text-xs text-slate-700">
                {isPublicCapability
                  ? `${result.response.protocolVersion} (${result.response.mode})`
                  : isPredicate
                    ? `${result.proofVersion} (Range &ge; ${result.threshold})`
                    : isDocUpload
                      ? 'MODE_B (File Digest)'
                      : 'STANDARD_V1'}
              </span>
            </div>
          </div>
        </div>

        {/* Reference & Metadata Details */}
        <div className="space-y-2 rounded-xl border border-slate-200 bg-slate-50 p-3.5 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-slate-500">Verification Target:</span>
            {isPublicCapability || isPublicDoc ? (
              <FormattedHash
                value={result.token}
                prefixLength={10}
                suffixLength={8}
              />
            ) : isDocUpload ? (
              <span className="font-mono text-slate-800">
                {result.fileName}
              </span>
            ) : isCredSignature || isPredicate ? (
              <FormattedHash
                value={result.credentialId}
                prefixLength={8}
                suffixLength={8}
              />
            ) : null}
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-500">Evaluation Timestamp:</span>
            <span className="font-mono text-slate-700">
              {new Date(result.timestamp).toUTCString()}
            </span>
          </div>

          {isPredicate && (
            <div className="flex items-center justify-between">
              <span className="text-slate-500">Proven Statement:</span>
              <span className="font-medium text-emerald-700">
                <code>{result.attributeName}</code> &ge; {result.threshold}
              </span>
            </div>
          )}
        </div>

        {/* Minimal Disclosure & Privacy Enclave Callout */}
        <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-100/70 p-3.5 text-xs text-slate-700">
          <EyeOff
            className="mt-0.5 h-4 w-4 shrink-0 text-slate-500"
            aria-hidden="true"
          />
          <div className="space-y-1">
            <p className="font-semibold text-slate-800">
              Zero-Knowledge Privacy Enclave Protection
            </p>
            <p className="text-slate-600">
              <strong>Intentionally Not Disclosed:</strong> Raw document files,
              private attribute secrets, blinding factors, and identity
              identifiers are never leaked to verifiers. Only cryptographic
              authenticity and lifecycle states are established.
            </p>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex justify-end gap-3 pt-2">
          <Button
            variant="outline"
            onClick={onReset}
            className="flex items-center gap-2"
          >
            <ArrowLeft className="h-4 w-4" />
            Verify Another Reference
          </Button>
        </div>
      </CardContent>
    </Card>
  );
};
