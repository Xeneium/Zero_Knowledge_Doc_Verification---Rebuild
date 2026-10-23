import React, { useState } from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { Alert } from '../../../components/feedback/Alert';
import {
  validateDocumentFile,
  formatFileSize,
} from '../../../lib/utils/fileValidation';
import { isUuid } from '../../../lib/utils/qrPayload';
import { useAuth, authStore } from '../../../state/authStore';
import { FileCheck, Upload, AlertCircle } from 'lucide-react';
import type { DocumentVerificationResult } from '../../../api/types';

interface DocumentUploadVerifyCardProps {
  onVerifySuccess: (
    result: DocumentVerificationResult,
    fileName: string,
    fileSize: number,
  ) => void;
  disabled?: boolean;
  isLoading?: boolean;
  onStartVerify: (documentId: string, file: File) => Promise<void>;
}

export const DocumentUploadVerifyCard: React.FC<
  DocumentUploadVerifyCardProps
> = ({ disabled = false, isLoading = false, onStartVerify }) => {
  const [documentId, setDocumentId] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);

  const { isAuthenticated } = useAuth();
  const hasVerifierRole =
    authStore.hasRole('VERIFIER') ||
    authStore.hasRole('ISSUER') ||
    authStore.hasRole('HOLDER') ||
    authStore.hasRole('ADMIN');

  const handleFileChange = (file: File | null) => {
    setValidationError(null);
    if (!file) {
      setSelectedFile(null);
      return;
    }

    const valResult = validateDocumentFile(file);
    if (!valResult.valid) {
      setValidationError(valResult.error || 'Invalid file');
      setSelectedFile(null);
      return;
    }

    setSelectedFile(file);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading || disabled) return;
    setValidationError(null);

    // Document UUID is optional. If provided, it must be valid UUID format.
    const trimmedId = documentId.trim();
    if (trimmedId && !isUuid(trimmedId)) {
      setValidationError(
        'Provided Document UUID must be a valid 36-character UUID format (e.g. 550e8400-e29b-41d4-a716-446655440000), or leave blank for automatic fingerprint lookup.',
      );
      return;
    }

    if (!selectedFile) {
      setValidationError(
        'Please select a candidate document file to verify.',
      );
      return;
    }

    try {
      await onStartVerify(trimmedId, selectedFile);
    } catch (err) {
      const error = err as Error;
      setValidationError(error.message || 'Document verification failed');
    }
  };

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-lg text-slate-900">
            <FileCheck className="h-5 w-5 text-emerald-600" aria-hidden="true" />
            Direct Document Verification (No QR Code Required)
          </CardTitle>
          {isAuthenticated && hasVerifierRole ? (
            <span className="inline-flex items-center rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">
              Authenticated Verifier Session
            </span>
          ) : (
            <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
              Public Minimal Verification
            </span>
          )}
        </div>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={(e) => {
            void handleSubmit(e);
          }}
          className="space-y-4"
        >
          <p className="text-sm text-slate-600">
            Upload any original or candidate document. The platform computes its authoritative SHA-256
            cryptographic fingerprint and verifies digital signatures and active registration status directly —{' '}
            <strong>no QR code embedded in the document is required</strong>.
          </p>

          {validationError && (
            <Alert variant="danger" title="Verification Notice">
              {validationError}
            </Alert>
          )}

          <div>
            <div className="flex items-center justify-between">
              <label
                htmlFor="doc-id-input"
                className="mb-1 block text-sm font-medium text-slate-700"
              >
                Target Document UUID{' '}
                <span className="font-normal text-slate-400">(Optional)</span>
              </label>
              <span className="text-xs text-slate-400">
                Leave blank to match fingerprint automatically
              </span>
            </div>
            <Input
              id="doc-id-input"
              type="text"
              value={documentId}
              onChange={(e) => {
                setDocumentId(e.target.value);
                if (validationError) setValidationError(null);
              }}
              placeholder="Leave blank for auto-lookup, or enter e.g. 550e8400-e29b-41d4-a716-446655440000"
              disabled={disabled || isLoading}
              className="font-mono text-xs"
            />
          </div>

          {/* Drag & Drop File Upload Area */}
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">
              Document File (PDF, PNG, JPEG, TXT - Max 25 MB)
            </label>
            <div
              onDragOver={(e) => {
                e.preventDefault();
                setIsDragOver(true);
              }}
              onDragLeave={() => setIsDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setIsDragOver(false);
                const file = e.dataTransfer.files?.[0];
                if (file) handleFileChange(file);
              }}
              className={`relative flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-6 text-center transition-colors ${
                isDragOver
                  ? 'border-emerald-500 bg-emerald-50/50'
                  : selectedFile
                    ? 'border-emerald-300 bg-emerald-50/20'
                    : 'border-slate-300 bg-slate-50 hover:bg-slate-100/50'
              }`}
            >
              <input
                type="file"
                id="verify-file-input"
                accept=".pdf,.png,.jpg,.jpeg,.txt,application/pdf,image/png,image/jpeg,text/plain"
                onChange={(e) =>
                  handleFileChange(e.target.files?.[0] || null)
                }
                disabled={disabled || isLoading}
                className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
                aria-label="Upload document file for verification"
              />

              <Upload
                className={`mb-2 h-8 w-8 ${selectedFile ? 'text-emerald-600' : 'text-slate-400'}`}
                aria-hidden="true"
              />

              {selectedFile ? (
                <div className="space-y-1">
                  <p className="text-sm font-medium text-slate-900">
                    {selectedFile.name}
                  </p>
                  <p className="text-xs text-slate-500">
                    {formatFileSize(selectedFile.size)} • Ready for verification
                  </p>
                </div>
              ) : (
                <div className="space-y-1">
                  <p className="text-sm font-medium text-slate-700">
                    Drag and drop candidate document, or browse
                  </p>
                  <p className="text-xs text-slate-500">
                    Supports PDF, PNG, JPEG, and TXT up to 25 MB (no QR required)
                  </p>
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center justify-between pt-2">
            <span className="flex items-center gap-1.5 text-xs text-slate-500">
              <AlertCircle className="h-3.5 w-3.5 text-slate-400" />
              Cryptographic integrity &amp; signature verified server-side
            </span>
            <Button
              type="submit"
              variant="primary"
              disabled={!selectedFile || disabled || isLoading}
              className="flex items-center gap-2"
            >
              <FileCheck className="h-4 w-4" aria-hidden="true" />
              {isLoading ? 'Verifying Document...' : 'Verify Document'}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
};
