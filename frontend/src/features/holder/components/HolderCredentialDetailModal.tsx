import React, { useState } from 'react';
import { Dialog } from '../../../components/feedback/Dialog';
import { Button } from '../../../components/common/Button';
import { Badge } from '../../../components/common/Badge';
import { Alert } from '../../../components/feedback/Alert';
import { Spinner } from '../../../components/feedback/Spinner';
import { FormattedHash } from '../../../components/data/FormattedHash';
import { CopyToClipboard } from '../../../components/data/CopyToClipboard';
import { VerificationStatusCard } from '../../../components/verification/VerificationStatusCard';
import { holderApi } from '../../../api/holderApi';
import type {
  HolderCredentialResponse,
  CredentialVerificationResult,
  CredentialLifecycleEvent,
} from '../../../api/types';
import { ShieldCheck, Clock, History, QrCode, FileKey } from 'lucide-react';

interface HolderCredentialDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  credential: HolderCredentialResponse | null;
  onNavigateToProof?: (holderCredentialId: string) => void;
  onNavigateToCapability?: (credentialId: string) => void;
}

interface HolderCredentialDetailBodyProps {
  credential: HolderCredentialResponse;
  onClose: () => void;
  onNavigateToProof?: (holderCredentialId: string) => void;
  onNavigateToCapability?: (credentialId: string) => void;
}

const HolderCredentialDetailBody: React.FC<HolderCredentialDetailBodyProps> = ({
  credential,
  onClose,
  onNavigateToProof,
  onNavigateToCapability,
}) => {
  const [isVerifying, setIsVerifying] = useState(false);
  const [verifyResult, setVerifyResult] =
    useState<CredentialVerificationResult | null>(null);
  const [verifyError, setVerifyError] = useState<string | null>(null);

  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [historyEvents, setHistoryEvents] = useState<
    CredentialLifecycleEvent[] | null
  >(null);
  const [historyError, setHistoryError] = useState<string | null>(null);

  const handleVerify = async () => {
    setIsVerifying(true);
    setVerifyError(null);
    try {
      const res = await holderApi.verifyCredential(credential.credentialId);
      setVerifyResult(res);
    } catch (err) {
      setVerifyError(
        err instanceof Error
          ? err.message
          : 'Failed to verify credential signature',
      );
    } finally {
      setIsVerifying(false);
    }
  };

  const handleLoadHistory = async () => {
    setIsLoadingHistory(true);
    setHistoryError(null);
    try {
      const history = await holderApi.getCredentialLifecycleHistory(
        credential.credentialId,
      );
      setHistoryEvents(history);
    } catch (err) {
      setHistoryError(
        err instanceof Error
          ? err.message
          : 'Failed to retrieve lifecycle history',
      );
    } finally {
      setIsLoadingHistory(false);
    }
  };

  const digest =
    credential.credentialDigestHex || credential.credentialDigest || '';

  const getStatusBadge = (status: string) => {
    switch (status.toUpperCase()) {
      case 'ACTIVE':
        return <Badge variant="success">ACTIVE</Badge>;
      case 'SUSPENDED':
        return <Badge variant="warning">SUSPENDED</Badge>;
      case 'REVOKED':
        return <Badge variant="danger">REVOKED</Badge>;
      default:
        return <Badge variant="neutral">{status}</Badge>;
    }
  };

  return (
    <div className="space-y-6 pt-2">
      {/* Core Metadata Grid */}
      <div className="grid grid-cols-1 gap-4 rounded-lg border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2 dark:border-slate-800 dark:bg-slate-900/60">
        <div>
          <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
            Holder Record UUID
          </span>
          <div className="mt-1 flex items-center gap-1 font-mono text-xs text-slate-900 dark:text-slate-100">
            <span>{credential.id}</span>
            <CopyToClipboard value={credential.id} label="Record UUID" />
          </div>
        </div>

        <div>
          <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
            Underlying Credential UUID
          </span>
          <div className="mt-1 flex items-center gap-1 font-mono text-xs text-slate-900 dark:text-slate-100">
            <span>{credential.credentialId}</span>
            <CopyToClipboard
              value={credential.credentialId}
              label="Credential UUID"
            />
          </div>
        </div>

        <div>
          <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
            Current Status &amp; Version
          </span>
          <div className="mt-1 flex items-center gap-2">
            {getStatusBadge(credential.status)}
            <span className="text-xs text-slate-600 dark:text-slate-400">
              Version: <strong>{credential.version}</strong>
            </span>
          </div>
        </div>

        <div>
          <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
            Authenticated Holder Subject
          </span>
          <p className="mt-1 font-mono text-xs text-slate-900 dark:text-slate-100">
            {credential.holderId}
          </p>
        </div>

        <div className="sm:col-span-2">
          <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
            Authoritative SHA-256 Digest
          </span>
          <div className="mt-1 flex items-center justify-between rounded-md border border-slate-200 bg-white p-2 dark:border-slate-800 dark:bg-slate-950">
            <FormattedHash value={digest} prefixLength={16} suffixLength={16} />
          </div>
        </div>

        <div>
          <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
            Provisioned In Wallet
          </span>
          <p className="mt-1 text-xs text-slate-700 dark:text-slate-300">
            {new Date(credential.provisionedAt).toLocaleString()}
          </p>
        </div>

        <div>
          <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
            Last State Transition
          </span>
          <p className="mt-1 text-xs text-slate-700 dark:text-slate-300">
            {new Date(credential.updatedAt).toLocaleString()}
          </p>
        </div>
      </div>

      {/* Zero-Knowledge & Document Privacy Notice */}
      <Alert
        variant="info"
        title="Privacy Enclave & Minimal Disclosure Architecture"
      >
        <div className="space-y-1 text-xs">
          <p>
            • <strong>Witness Privacy:</strong> Raw attribute values and
            Pedersen commitment blinding factors are held strictly inside the
            platform enclave. They are never sent to your browser, preventing
            client-side memory leakage.
          </p>
          <p>
            • <strong>Document Confidentiality:</strong> Raw source documents
            are not transmitted or downloaded over REST APIs. Proofs and
            verification rely purely on authoritative SHA-256 hashes and RSA-PSS
            digital signatures.
          </p>
        </div>
      </Alert>

      {/* Signature Verification Section */}
      <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
          <div>
            <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              Underlying Digital Signature Verification
            </h4>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Verify the issuer&apos;s cryptographic RSA-PSS signature against
              the public key registry.
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleVerify()}
            disabled={isVerifying}
            className="min-h-[44px]"
          >
            {isVerifying ? (
              <>
                <Spinner size="sm" className="mr-2" />
                Verifying...
              </>
            ) : (
              <>
                <ShieldCheck className="mr-1.5 h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                Verify Signature
              </>
            )}
          </Button>
        </div>

        {verifyError && (
          <div className="mt-3">
            <Alert variant="danger" title="Signature Verification Failed">
              {verifyError}
            </Alert>
          </div>
        )}

        {verifyResult && (
          <div className="mt-3">
            <VerificationStatusCard
              status={
                verifyResult.verified
                  ? 'VERIFIED_ACTIVE'
                  : verifyResult.status === 'REVOKED'
                    ? 'CREDENTIAL_REVOKED'
                    : verifyResult.status === 'EXPIRED'
                      ? 'CREDENTIAL_EXPIRED'
                      : 'PROOF_REJECTED'
              }
              customDescription={verifyResult.message}
              metadata={
                <div className="space-y-1 font-mono text-xs text-slate-700 dark:text-slate-300">
                  <p>Credential UUID: {verifyResult.credentialId}</p>
                  {verifyResult.asOf && (
                    <p>
                      Verified As Of:{' '}
                      {new Date(verifyResult.asOf).toLocaleString()}
                    </p>
                  )}
                </div>
              }
            />
          </div>
        )}
      </div>

      {/* Lifecycle Audit History Section */}
      <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
          <div>
            <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              Lifecycle State Transition Audit History
            </h4>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Inspect immutable audit events recorded when this credential
              changed state.
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleLoadHistory()}
            disabled={isLoadingHistory}
            className="min-h-[44px]"
          >
            {isLoadingHistory ? (
              <>
                <Spinner size="sm" className="mr-2" />
                Loading...
              </>
            ) : (
              <>
                <History className="mr-1.5 h-4 w-4 text-slate-600 dark:text-slate-400" />
                Fetch History
              </>
            )}
          </Button>
        </div>

        {historyError && (
          <div className="mt-3">
            <Alert variant="danger" title="History Retrieval Failed">
              {historyError}
            </Alert>
          </div>
        )}

        {historyEvents && (
          <div className="mt-4 space-y-2">
            {historyEvents.length === 0 ? (
              <p className="text-xs text-slate-500 italic dark:text-slate-400">
                No lifecycle transitions recorded yet.
              </p>
            ) : (
              historyEvents.map((ev) => (
                <div
                  key={ev.id}
                  className="flex items-start gap-3 rounded-md border border-slate-100 bg-slate-50 p-2.5 text-xs text-slate-700 dark:border-slate-800 dark:bg-slate-900/40 dark:text-slate-300"
                >
                  <Clock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" />
                  <div className="flex-1 space-y-0.5">
                    <div className="flex items-center justify-between">
                      <strong className="font-semibold text-slate-900 dark:text-slate-100">
                        {ev.eventType}
                      </strong>
                      <span className="font-mono text-[11px] text-slate-500">
                        {new Date(ev.timestamp).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-slate-600 dark:text-slate-400">
                      Transition:{' '}
                      <span className="font-semibold">{ev.fromStatus}</span> →{' '}
                      <span className="font-semibold">{ev.toStatus}</span> by{' '}
                      <code className="text-[11px]">{ev.actor}</code> (
                      {ev.actorRole})
                    </p>
                    {ev.reason && (
                      <p className="text-slate-500 italic">
                        Reason: &ldquo;{ev.reason}&rdquo;
                      </p>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {/* Modal Footer Actions */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4 dark:border-slate-800">
        <div className="flex items-center gap-2">
          {onNavigateToProof && credential.status === 'ACTIVE' && (
            <Button
              variant="primary"
              size="sm"
              onClick={() => {
                onClose();
                onNavigateToProof(credential.id);
              }}
              className="min-h-[44px]"
            >
              <FileKey className="mr-1.5 h-4 w-4" />
              Generate ZK Predicate Proof
            </Button>
          )}
          {onNavigateToCapability && credential.status === 'ACTIVE' && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                onClose();
                onNavigateToCapability(credential.credentialId);
              }}
              className="min-h-[44px]"
            >
              <QrCode className="mr-1.5 h-4 w-4" />
              Mint Capability QR
            </Button>
          )}
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={onClose}
          className="min-h-[44px]"
        >
          Close Inspection
        </Button>
      </div>
    </div>
  );
};

export const HolderCredentialDetailModal: React.FC<
  HolderCredentialDetailModalProps
> = ({
  isOpen,
  onClose,
  credential,
  onNavigateToProof,
  onNavigateToCapability,
}) => {
  if (!credential) return null;

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title="Holder Credential Inspection"
      description="Cryptographic metadata, signature verification, and lifecycle audit records."
      maxWidth="lg"
    >
      <HolderCredentialDetailBody
        key={credential.id}
        credential={credential}
        onClose={onClose}
        onNavigateToProof={onNavigateToProof}
        onNavigateToCapability={onNavigateToCapability}
      />
    </Dialog>
  );
};
