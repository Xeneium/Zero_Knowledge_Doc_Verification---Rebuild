import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useSearchParams, useLocation } from 'react-router';
import { PageHeader } from '../components/layout/PageHeader';
import {
  QrScannerCard,
  ManualReferenceCard,
  DocumentUploadVerifyCard,
  PredicateProofVerifyCard,
  VerificationResultCard,
  VerificationHistoryCard,
} from '../features/verifier';
import {
  verifierStore,
  useActiveVerification,
  useVerifierLoading,
  useVerifierError,
} from '../state/verifierStore';
import { verifierApi } from '../api/verifierApi';
import { parseQrPayload, type ParsedQrPayload } from '../lib/utils/qrPayload';
import type { ApiError } from '../api/errors';
import { Camera, Search, FileCheck, Cpu } from 'lucide-react';
import type { CredentialPredicateVerificationRequest } from '../api/types';

export type VerifierTab = 'qr-scanner' | 'manual' | 'file-upload' | 'predicate';

export const PublicVerificationPage: React.FC = () => {
  const { token: routeToken } = useParams<{ token?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();

  const queryTab = searchParams.get('tab') as VerifierTab | null;
  const queryToken = searchParams.get('token');
  const isPredicateRoute = location.pathname.includes('predicate-verification');

  const [activeTab, setActiveTab] = useState<VerifierTab>(
    queryTab === 'predicate' || isPredicateRoute
      ? 'predicate'
      : queryTab === 'file-upload'
        ? 'file-upload'
        : queryTab === 'manual'
          ? 'manual'
          : 'qr-scanner',
  );

  const activeResult = useActiveVerification();
  const isLoading = useVerifierLoading();
  const error = useVerifierError();

  const handleExecuteVerification = useCallback(async (token: string) => {
    verifierStore.setLoading(true);
    verifierStore.clearResult();

    try {
      // 1. Try capability verification endpoint
      try {
        const capResult = await verifierApi.publicVerifyCapability(token);
        verifierStore.setResult({
          kind: 'PUBLIC_CAPABILITY',
          token,
          response: capResult,
          timestamp: new Date().toISOString(),
        });
        return;
      } catch (err) {
        const apiErr = err as ApiError;
        // If not a 404, throw to outer handler (e.g. 429, 500)
        if (apiErr.status !== 404) {
          throw err;
        }
      }

      // 2. Fall back to standard document token verification (Mode A)
      const docResult = await verifierApi.publicVerifyToken(token);
      verifierStore.setResult({
        kind: 'PUBLIC_DOCUMENT_TOKEN',
        token,
        response: docResult,
        timestamp: new Date().toISOString(),
      });
    } catch (err) {
      const apiErr = err as ApiError;
      if (apiErr.status === 404) {
        verifierStore.setError({
          type: 'NOT_FOUND',
          message:
            'No active credential, document, or verification capability was found matching this token reference.',
          targetReference: token,
        });
      } else if (apiErr.status === 429) {
        verifierStore.setError({
          type: 'RATE_LIMITED',
          message:
            'Public verification rate limit reached or server capacity saturated. Please retry in a few moments.',
          targetReference: token,
        });
      } else if (apiErr.status === 400) {
        verifierStore.setError({
          type: 'BAD_REQUEST',
          message: apiErr.message || 'Malformed verification token format.',
          targetReference: token,
        });
      } else {
        verifierStore.setError({
          type: 'UNAVAILABLE',
          message:
            apiErr.message ||
            'Verification authority is temporarily unreachable. An operational issue does not indicate an invalid credential.',
          targetReference: token,
        });
      }
    }
  }, []);

  // Direct Token from URL parameters
  useEffect(() => {
    const targetToken = routeToken || queryToken;
    if (targetToken) {
      void handleExecuteVerification(targetToken.trim());
    }
  }, [routeToken, queryToken, handleExecuteVerification]);

  const handleTabChange = (tab: VerifierTab) => {
    setActiveTab(tab);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('tab', tab);
      return next;
    });
  };

  const handleScanSuccess = (rawPayload: string) => {
    const parsed = parseQrPayload(rawPayload);
    if (parsed.success) {
      void handleExecuteVerification(parsed.payload.token);
    } else {
      verifierStore.setError({
        type: 'BAD_REQUEST',
        message: parsed.error,
      });
    }
  };

  const handleManualVerify = (parsed: ParsedQrPayload) => {
    void handleExecuteVerification(parsed.token);
  };

  const handleFileUploadVerify = async (documentId: string, file: File) => {
    verifierStore.setLoading(true);
    verifierStore.clearResult();

    try {
      const outcome = await verifierApi.verifyUploadedDocumentDirect(
        file,
        documentId.trim() || undefined,
      );
      verifierStore.setResult({
        kind: 'DOCUMENT_UPLOAD',
        documentId: outcome.documentId || (documentId.trim() || undefined),
        digest: outcome.digest,
        fileName: file.name,
        fileSize: file.size,
        outcome: outcome,
        response: {
          verified: outcome.status === 'VERIFIED',
          documentId: outcome.documentId,
          status: outcome.status,
          digest: outcome.digest,
          message: outcome.message,
        },
        timestamp: new Date().toISOString(),
      });
    } catch (err) {
      const apiErr = err as ApiError;
      if (apiErr.status === 404) {
        verifierStore.setError({
          type: 'NOT_FOUND',
          message: documentId.trim()
            ? `Document UUID ${documentId} was not found in the platform registry.`
            : 'No matching document record was found for this file. It may be unregistered or altered.',
          targetReference: documentId.trim() || file.name,
        });
      } else if (apiErr.status === 403) {
        verifierStore.setError({
          type: 'FORBIDDEN',
          message:
            'Access denied. Document verification requires authorization or an active session.',
          targetReference: documentId.trim() || file.name,
        });
      } else if (apiErr.status === 429) {
        verifierStore.setError({
          type: 'RATE_LIMITED',
          message:
            'Public verification rate limit reached. Please wait a moment before trying again.',
          targetReference: file.name,
        });
      } else {
        verifierStore.setError({
          type: 'UNAVAILABLE',
          message:
            apiErr.message ||
            'Verification service failed while verifying uploaded document.',
          targetReference: documentId.trim() || file.name,
        });
      }
    }
  };

  const handlePredicateVerify = async (
    payload: CredentialPredicateVerificationRequest,
  ) => {
    verifierStore.setLoading(true);
    verifierStore.clearResult();

    try {
      const result = await verifierApi.verifyPredicateProof(payload);
      verifierStore.setResult({
        kind: 'PREDICATE_PROOF',
        credentialId: payload.credentialId,
        attributeName: payload.attributeName,
        threshold: payload.threshold,
        proofVersion: payload.proofVersion,
        response: result,
        timestamp: new Date().toISOString(),
      });
    } catch (err) {
      const apiErr = err as ApiError;
      if (apiErr.status === 429) {
        verifierStore.setError({
          type: 'RATE_LIMITED',
          message:
            'Cryptographic zero-knowledge verification capacity saturated. Server concurrency limit reached. Please retry in a moment.',
        });
      } else if (apiErr.status === 400) {
        verifierStore.setError({
          type: 'BAD_REQUEST',
          message:
            apiErr.message ||
            'Invalid proof parameters or unsupported verification fields.',
        });
      } else {
        verifierStore.setError({
          type: 'UNAVAILABLE',
          message:
            apiErr.message ||
            'Verification failure during zero-knowledge proof evaluation.',
        });
      }
    }
  };

  const handleResetVerification = () => {
    verifierStore.clearResult();
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <PageHeader
        title="Public Cryptographic Verification"
        description="Verify document integrity, digital signatures, and zero-knowledge predicate proofs against authoritative trust anchors with zero personal data leakage."
      />

      {/* When a result or error is active, show the Result Visualizer */}
      {activeResult || error ? (
        <VerificationResultCard
          result={activeResult}
          error={error}
          onReset={handleResetVerification}
        />
      ) : (
        /* Otherwise, render the verification input methods */
        <div className="space-y-6">
          {/* Navigation Tab Bar */}
          <div className="flex border-b border-slate-200">
            <button
              type="button"
              onClick={() => handleTabChange('qr-scanner')}
              className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
                activeTab === 'qr-scanner'
                  ? 'border-emerald-600 text-emerald-700'
                  : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
              }`}
            >
              <Camera className="h-4 w-4" />
              QR Camera Scanner
            </button>

            <button
              type="button"
              onClick={() => handleTabChange('manual')}
              className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
                activeTab === 'manual'
                  ? 'border-emerald-600 text-emerald-700'
                  : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
              }`}
            >
              <Search className="h-4 w-4" />
              Manual Reference Entry
            </button>

            <button
              type="button"
              onClick={() => handleTabChange('file-upload')}
              className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
                activeTab === 'file-upload'
                  ? 'border-emerald-600 text-emerald-700'
                  : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
              }`}
            >
              <FileCheck className="h-4 w-4" />
              File Integrity (Mode B)
            </button>

            <button
              type="button"
              onClick={() => handleTabChange('predicate')}
              className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
                activeTab === 'predicate'
                  ? 'border-emerald-600 text-emerald-700'
                  : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
              }`}
            >
              <Cpu className="h-4 w-4" />
              ZKP Predicate Proof
            </button>
          </div>

          {/* Active Tab Panel */}
          {activeTab === 'qr-scanner' && (
            <QrScannerCard
              onScanSuccess={handleScanSuccess}
              disabled={isLoading}
            />
          )}

          {activeTab === 'manual' && (
            <ManualReferenceCard
              onVerify={handleManualVerify}
              disabled={isLoading}
              isLoading={isLoading}
            />
          )}

          {activeTab === 'file-upload' && (
            <DocumentUploadVerifyCard
              onVerifySuccess={() => {}}
              onStartVerify={handleFileUploadVerify}
              disabled={isLoading}
              isLoading={isLoading}
            />
          )}

          {activeTab === 'predicate' && (
            <PredicateProofVerifyCard
              onVerify={handlePredicateVerify}
              disabled={isLoading}
              isLoading={isLoading}
            />
          )}
        </div>
      )}

      {/* Recent Session Verification History */}
      <VerificationHistoryCard
        onSelectResult={(item) => verifierStore.setResult(item)}
      />
    </div>
  );
};
