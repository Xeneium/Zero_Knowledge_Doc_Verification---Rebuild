import { apiClient } from '../../../lib/api/client';

export interface DocumentVerificationResult {
  verified: boolean;
  signatureValid: boolean;
  documentActive: boolean;
  storedDigestHex?: string;
  computedDigestHex?: string;
}

export interface CredentialVerificationResult {
  credentialId: string;
  verified: boolean;
  signatureValid: boolean;
  operationalStatusValid: boolean;
  status?: string;
  asOf?: string;
}

export interface PredicateVerificationRequest {
  proofVersion: number;
  credentialId: string;
  attributeName: string;
  threshold: number;
  nonce: string;
  proofBytes: string;
}

export interface PredicateVerificationResult {
  verified: boolean;
  credentialId?: string;
  attributeName?: string;
  threshold?: number;
  signatureValid?: boolean;
  commitmentValid?: boolean;
  proofValid?: boolean;
  reason?: string;
}

export async function verifyStoredDocumentCopy(
  documentId: string,
  signal?: AbortSignal,
): Promise<DocumentVerificationResult> {
  return apiClient.get<DocumentVerificationResult>(
    `/api/documents/${encodeURIComponent(documentId)}/verify`,
    { signal },
  );
}

export async function verifyUploadedDocumentCopy(
  documentId: string,
  file: File,
  signal?: AbortSignal,
): Promise<DocumentVerificationResult> {
  const formData = new FormData();
  formData.append('file', file);
  return apiClient.post<DocumentVerificationResult>(
    `/api/documents/${encodeURIComponent(documentId)}/verify-upload`,
    formData,
    { signal },
  );
}

export async function verifyCredential(
  credentialId: string,
  asOf?: string,
  signal?: AbortSignal,
): Promise<CredentialVerificationResult> {
  const query = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
  return apiClient.get<CredentialVerificationResult>(
    `/api/credentials/${encodeURIComponent(credentialId)}/verify${query}`,
    { signal },
  );
}

export async function verifyPredicateProof(
  req: PredicateVerificationRequest,
  signal?: AbortSignal,
): Promise<PredicateVerificationResult> {
  return apiClient.post<PredicateVerificationResult>(
    '/api/v2/zkp/credential-predicates/verify',
    req,
    { signal },
  );
}
