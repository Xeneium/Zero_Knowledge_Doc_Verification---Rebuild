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
import { Link } from 'react-router';
import { FileCheck, Upload, Lock, AlertCircle } from 'lucide-react';
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

    if (!documentId.trim() || !isUuid(documentId.trim())) {
      setValidationError(
        'Please provide a valid document UUID (e.g. 12345678-1234-1234-1234-1234567890ab)',
      );
      return;
    }

    if (!selectedFile) {
      setValidationError(
        'Please select a document file to verify against the stored record',
      );
      return;
    }

    try {
      await onStartVerify(documentId.trim(), selectedFile);
    } catch (err) {
      const error = err as Error;
      setValidationError(error.message || 'Document verification failed');
    }
  };

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg text-slate-900">
          <FileCheck className="h-5 w-5 text-emerald-600" aria-hidden="true" />
          Mode B: Document Integrity &amp; Upload Verification
        </CardTitle>
      </CardHeader>
      <CardContent>
        {!isAuthenticated || !hasVerifierRole ? (
          <div className="space-y-4">
            <Alert variant="warning" title="Authentication Required">
              Mode B document-upload verification evaluates presentation file
              bytes against the backend cryptographic hash and signature
              authority. This requires an authenticated session with{' '}
              <code>ROLE_VERIFIER</code>, <code>ROLE_ISSUER</code>,{' '}
              <code>ROLE_HOLDER</code>, or <code>ROLE_ADMIN</code>.
            </Alert>
            <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-4">
              <div className="flex items-center gap-3">
                <Lock className="h-5 w-5 text-slate-500" />
                <span className="text-sm text-slate-700">
                  Connect a verifier session to unlock file upload verification.
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
              Upload a candidate document to independently verify its SHA-256
              digest and authoritative RSA-PSS digital signature against the
              stored anchor.
            </p>

            {validationError && (
              <Alert variant="danger" title="Verification Notice">
                {validationError}
              </Alert>
            )}

            <div>
              <label
                htmlFor="doc-id-input"
                className="mb-1 block text-sm font-medium text-slate-700"
              >
                Target Document UUID
              </label>
              <Input
                id="doc-id-input"
                type="text"
                value={documentId}
                onChange={(e) => {
                  setDocumentId(e.target.value);
                  if (validationError) setValidationError(null);
                }}
                placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
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
                      {formatFileSize(selectedFile.size)} • Ready for
                      verification
                    </p>
                  </div>
                ) : (
                  <div className="space-y-1">
                    <p className="text-sm font-medium text-slate-700">
                      Drag and drop candidate document, or browse
                    </p>
                    <p className="text-xs text-slate-500">
                      Supports PDF, PNG, JPEG, and TXT up to 25 MB
                    </p>
                  </div>
                )}
              </div>
            </div>

            <div className="flex items-center justify-between pt-2">
              <span className="flex items-center gap-1.5 text-xs text-slate-500">
                <AlertCircle className="h-3.5 w-3.5 text-slate-400" />
                Authoritative check performed on backend server
              </span>
              <Button
                type="submit"
                variant="primary"
                disabled={
                  !documentId.trim() || !selectedFile || disabled || isLoading
                }
                className="flex items-center gap-2"
              >
                <FileCheck className="h-4 w-4" aria-hidden="true" />
                {isLoading ? 'Verifying...' : 'Verify Document Copy'}
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
};
