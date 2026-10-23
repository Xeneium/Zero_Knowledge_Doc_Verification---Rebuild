import React, { useState, useRef, useEffect } from 'react';
import { Link } from 'react-router';
import {
  UploadCloud,
  FileCheck,
  AlertCircle,
  QrCode,
  ShieldCheck,
  X,
  ExternalLink,
} from 'lucide-react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Alert } from '../../../components/feedback/Alert';
import { Badge } from '../../../components/common/Badge';
import { FormattedHash } from '../../../components/data/FormattedHash';
import { CopyToClipboard } from '../../../components/data/CopyToClipboard';
import { issuerApi } from '../../../api/issuerApi';
import { issuerStore } from '../../../state/issuerStore';
import {
  validateDocumentFile,
  formatFileSize,
  ALLOWED_EXTENSIONS,
} from '../../../lib/utils/fileValidation';
import type { DocumentUploadResponse } from '../../../api/types';

export interface DocumentUploadCardProps {
  onSuccess?: (response: DocumentUploadResponse) => void;
  onNavigateToLifecycle?: (credentialId: string) => void;
}

export const DocumentUploadCard: React.FC<DocumentUploadCardProps> = ({
  onSuccess,
}) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadResult, setUploadResult] =
    useState<DocumentUploadResponse | null>(null);
  const [isVerifying, setIsVerifying] = useState(false);
  const [verifyStatus, setVerifyStatus] = useState<boolean | null>(null);
  const [qrBlobUrl, setQrBlobUrl] = useState<string | null>(null);
  const [isLoadingQr, setIsLoadingQr] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [uploadedFileDetails, setUploadedFileDetails] = useState<{
    name: string;
    size: number;
    type: string;
  } | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Clean up object URL on unmount to prevent browser memory leaks
  useEffect(() => {
    return () => {
      if (qrBlobUrl) {
        URL.revokeObjectURL(qrBlobUrl);
      }
    };
  }, [qrBlobUrl]);

  const handleFileChange = (file: File | null) => {
    setUploadResult(null);
    setUploadError(null);
    setVerifyStatus(null);
    if (qrBlobUrl) {
      URL.revokeObjectURL(qrBlobUrl);
      setQrBlobUrl(null);
    }

    if (!file) {
      setSelectedFile(null);
      setValidationError(null);
      return;
    }

    const validation = validateDocumentFile(file);
    if (!validation.valid) {
      setValidationError(validation.error || 'Invalid file');
      setSelectedFile(null);
      return;
    }

    setValidationError(null);
    setSelectedFile(file);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleFileChange(e.dataTransfer.files[0]);
    }
  };

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile || isUploading) return;

    setIsUploading(true);
    setUploadError(null);
    setVerifyStatus(null);

    try {
      setUploadedFileDetails({
        name: selectedFile.name,
        size: selectedFile.size,
        type: selectedFile.type || 'text/plain',
      });
      const result = await issuerApi.uploadDocument(selectedFile);
      setUploadResult(result);
      issuerStore.addActivity({
        id: result.id,
        type: 'document',
        title: selectedFile.name,
        digest: result.digest,
        status: 'STORED & SIGNED',
      });

      // Automatically fetch the QR code image
      try {
        const blob = await issuerApi.getDocumentQrBlob(result.id);
        const url = URL.createObjectURL(blob);
        setQrBlobUrl(url);
      } catch {
        // Non-fatal if QR fails to auto-load
      }

      if (onSuccess) {
        onSuccess(result);
      }
    } catch (err) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Failed to upload and secure document';
      setUploadError(msg);
    } finally {
      setIsUploading(false);
    }
  };

  const handleVerifyStored = async () => {
    if (!uploadResult || isVerifying) return;
    setIsVerifying(true);
    try {
      const res = await issuerApi.verifyStoredDocument(uploadResult.id);
      setVerifyStatus(res.verified);
    } catch {
      setVerifyStatus(false);
    } finally {
      setIsVerifying(false);
    }
  };

  const handleFetchQr = async () => {
    if (!uploadResult || isLoadingQr) return;
    setIsLoadingQr(true);
    try {
      const blob = await issuerApi.getDocumentQrBlob(uploadResult.id);
      if (qrBlobUrl) {
        URL.revokeObjectURL(qrBlobUrl);
      }
      const url = URL.createObjectURL(blob);
      setQrBlobUrl(url);
    } catch (err) {
      setUploadError(
        err instanceof Error ? err.message : 'Failed to retrieve QR code image',
      );
    } finally {
      setIsLoadingQr(false);
    }
  };

  const handleReset = () => {
    setSelectedFile(null);
    setValidationError(null);
    setUploadError(null);
    setUploadResult(null);
    setUploadedFileDetails(null);
    setVerifyStatus(null);
    if (qrBlobUrl) {
      URL.revokeObjectURL(qrBlobUrl);
      setQrBlobUrl(null);
    }
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400">
              <UploadCloud className="h-5 w-5" />
            </div>
            <div>
              <CardTitle>Secure Document Upload</CardTitle>
              <CardDescription>
                Upload an authentic document artifact for SHA-256 fingerprinting
                and RSA-PSS signature anchoring.
              </CardDescription>
            </div>
          </div>
          <Badge variant="neutral">25 MB Max Bound</Badge>
        </div>
      </CardHeader>

      <CardContent className="space-y-6">
        {/* Upload Form */}
        {!uploadResult && (
          <form
            onSubmit={(e) => {
              void handleUpload(e);
            }}
            className="space-y-4"
          >
            {/* Drag & Drop Zone */}
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 text-center transition-colors ${
                isDragging
                  ? 'border-indigo-500 bg-indigo-50/50 dark:border-indigo-400 dark:bg-indigo-950/20'
                  : 'border-slate-300 hover:border-slate-400 dark:border-slate-700 dark:hover:border-slate-600'
              }`}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  fileInputRef.current?.click();
                }
              }}
              aria-label="Document file upload dropzone"
            >
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                accept={ALLOWED_EXTENSIONS.join(',')}
                onChange={(e) => {
                  if (e.target.files && e.target.files.length > 0) {
                    handleFileChange(e.target.files[0]);
                  }
                }}
              />
              <UploadCloud className="mb-3 h-10 w-10 text-slate-400 dark:text-slate-500" />
              <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Click to select or drag and drop document artifact
              </p>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Supported formats: PDF, PNG, JPEG, Plain Text (up to 25 MB)
              </p>
            </div>

            {/* Validation Error Alert */}
            {validationError && (
              <Alert
                variant="danger"
                title="Invalid Document File"
                icon={<AlertCircle className="h-5 w-5" />}
              >
                {validationError}
              </Alert>
            )}

            {/* Upload Error Alert */}
            {uploadError && (
              <Alert
                variant="danger"
                title="Upload Failed"
                icon={<AlertCircle className="h-5 w-5" />}
              >
                {uploadError}
              </Alert>
            )}

            {/* Selected File Card */}
            {selectedFile && (
              <div className="flex items-center justify-between rounded-md border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/50">
                <div className="flex items-center gap-3 overflow-hidden">
                  <FileCheck className="h-6 w-6 shrink-0 text-emerald-600 dark:text-emerald-400" />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                      {selectedFile.name}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {formatFileSize(selectedFile.size)} •{' '}
                      {selectedFile.type || 'text/plain'}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={(e) => {
                      e.stopPropagation();
                      fileInputRef.current?.click();
                    }}
                    aria-label="Replace selected file"
                  >
                    Replace
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleReset();
                    }}
                    aria-label="Remove selected file"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}

            {/* Upload Progress Indicator */}
            {isUploading && (
              <div
                role="progressbar"
                aria-label="Document upload progress"
                aria-busy="true"
                className="w-full space-y-1.5"
              >
                <div className="flex justify-between text-xs text-slate-500 dark:text-slate-400">
                  <span>Uploading and computing cryptographic SHA-256 digest...</span>
                  <span className="font-mono">Processing</span>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                  <div className="h-full w-2/3 animate-pulse rounded-full bg-indigo-600 dark:bg-indigo-500" />
                </div>
              </div>
            )}

            {/* Submit Action */}
            <div className="flex items-center justify-end gap-3 pt-2">
              <Button
                type="submit"
                variant="primary"
                disabled={!selectedFile || isUploading}
                isLoading={isUploading}
              >
                {isUploading
                  ? 'Securing Document...'
                  : 'Upload & Secure Document'}
              </Button>
            </div>
          </form>
        )}

        {/* Upload Success View */}
        {uploadResult && (
          <div className="space-y-6">
            <Alert
              variant="success"
              title="Document Successfully Uploaded & Secured"
            >
              The backend has verified the magic byte content, computed the
              authoritative SHA-256 digest, signed the artifact with RSA-PSS,
              and stored it securely.
            </Alert>

            <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-5 dark:border-slate-800 dark:bg-slate-900/60">
              {uploadedFileDetails && (
                <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                  <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                    Registered Document
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-xs text-slate-900 dark:text-slate-100">
                      {uploadedFileDetails.name} ({formatFileSize(uploadedFileDetails.size)})
                    </span>
                  </div>
                </div>
              )}

              <div className="flex flex-col gap-1 border-t border-slate-200/60 pt-3 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
                <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                  Document ID (UUID)
                </span>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-sm font-bold text-slate-900 dark:text-slate-100">
                    {uploadResult.id}
                  </span>
                  <CopyToClipboard
                    value={uploadResult.id}
                    label="Copy Document ID"
                  />
                </div>
              </div>

              <div className="flex flex-col gap-1 border-t border-slate-200/60 pt-3 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
                <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                  SHA-256 Digest
                </span>
                <div className="flex items-center gap-2">
                  <FormattedHash value={uploadResult.digest} />
                </div>
              </div>

              {uploadResult.publicToken && (
                <div className="flex flex-col gap-1 border-t border-slate-200/60 pt-3 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
                  <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                    Public Verification Token
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs text-slate-700 dark:text-slate-300">
                      {uploadResult.publicToken}
                    </span>
                    <CopyToClipboard
                      value={uploadResult.publicToken}
                      label="Copy Public Token"
                    />
                  </div>
                </div>
              )}

              {uploadResult.verificationUrl && (
                <div className="flex flex-col gap-1 border-t border-slate-200/60 pt-3 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
                  <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                    Direct Verification Link
                  </span>
                  <div className="flex items-center gap-2">
                    <a
                      href={uploadResult.verificationUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="font-mono text-xs text-indigo-600 hover:underline dark:text-indigo-400 truncate max-w-xs sm:max-w-md"
                    >
                      {uploadResult.verificationUrl}
                    </a>
                    <CopyToClipboard
                      value={uploadResult.verificationUrl}
                      label="Copy Verification Link"
                    />
                  </div>
                </div>
              )}
            </div>

            {/* Verification Status */}
            {verifyStatus !== null && (
              <Alert
                variant={verifyStatus ? 'success' : 'danger'}
                title={
                  verifyStatus
                    ? 'Cryptographic Verification Passed'
                    : 'Cryptographic Verification Failed'
                }
              >
                {verifyStatus
                  ? 'Server copy matched SHA-256 digest and valid RSA-PSS signature.'
                  : 'Stored copy failed integrity check or signature verification.'}
              </Alert>
            )}

            {/* Workflow Separation & Privacy Notice */}
            <div className="rounded-md border border-indigo-100 bg-indigo-50/60 p-4 text-xs text-indigo-900 dark:border-indigo-950 dark:bg-indigo-950/40 dark:text-indigo-200">
              <p className="font-semibold">Workflow Separation &amp; Privacy Notice:</p>
              <p className="mt-1">
                The original document did not require an embedded QR code. Document registration and QR-based verification are distinct workflows. The QR code above provides a privacy-preserving capability token for public verification without disclosing private document contents.
              </p>
            </div>

            {/* QR Code Presentation */}
            {qrBlobUrl && (
              <div className="flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white p-6 shadow-xs dark:border-slate-800 dark:bg-slate-900">
                <h4 className="mb-3 text-sm font-semibold text-slate-800 dark:text-slate-200">
                  Public Verification QR Code
                </h4>
                <img
                  src={qrBlobUrl}
                  alt="Document Public Verification QR Code"
                  className="h-48 w-48 rounded-md border border-slate-200 p-2 dark:border-slate-700"
                />
                <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
                  Scan to verify this document publicly without disclosing
                  private contents.
                </p>
                <a
                  href={qrBlobUrl}
                  download={`zkdoc-verify-${uploadResult.id.slice(0, 8)}.png`}
                  className="mt-3 text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                >
                  Download QR Code (PNG)
                </a>
              </div>
            )}

            {/* Actions Toolbar */}
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4 dark:border-slate-800">
              <Button type="button" variant="outline" onClick={handleReset}>
                Upload Another Document
              </Button>

              <div className="flex flex-wrap items-center gap-2">
                {uploadResult.publicToken && (
                  <Link to={`/verify?token=${uploadResult.publicToken}`}>
                    <Button type="button" variant="outline" className="flex items-center gap-2">
                      <ExternalLink className="h-4 w-4" aria-hidden="true" />
                      <span>Open in Public Verification</span>
                    </Button>
                  </Link>
                )}

                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    void handleVerifyStored();
                  }}
                  isLoading={isVerifying}
                >
                  <ShieldCheck className="mr-2 h-4 w-4" />
                  Verify Stored Copy
                </Button>

                <Button
                  type="button"
                  variant="primary"
                  onClick={() => {
                    void handleFetchQr();
                  }}
                  isLoading={isLoadingQr}
                >
                  <QrCode className="mr-2 h-4 w-4" />
                  {qrBlobUrl ? 'Refresh QR Code' : 'Generate QR Code'}
                </Button>
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
};
