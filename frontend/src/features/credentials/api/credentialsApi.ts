import { apiClient } from '../../../lib/api/client';

export interface CredentialAttributeDto {
  name: string;
  value: number;
  type?: string;
}

export interface IssueCredentialRequest {
  issuerId: string;
  subjectId?: string;
  attributes: CredentialAttributeDto[];
  validFrom?: string;
  validTo?: string;
}

export interface IssueCredentialResponse {
  credentialId: string;
  digest: string;
}

export interface DocumentUploadResponse {
  id: string;
  digest: string;
}

export interface CredentialLifecycleResponse {
  credentialId: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'REVOKED';
  version: number;
  issuerId?: string;
  message?: string;
}

export interface CredentialLifecycleEvent {
  id: string;
  credentialId: string;
  fromStatus: string;
  toStatus: string;
  actor: string;
  role: string;
  reason?: string;
  timestamp: string;
}

export interface HolderCredentialDto {
  id: string;
  holderId: string;
  credentialId: string;
  issuerId: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'REVOKED';
  attributes?: CredentialAttributeDto[];
  validFrom?: string;
  validTo?: string;
  createdAt: string;
}

export interface GenerateProofRequest {
  holderCredentialId: string;
  attributeName: string;
  threshold: number;
}

export interface HolderPredicateProofResponse {
  proofVersion: number;
  credentialId: string;
  attributeName: string;
  threshold: number;
  nonce: string;
  proofBytes: string;
}

export async function issueCredential(
  req: IssueCredentialRequest,
  signal?: AbortSignal,
): Promise<IssueCredentialResponse> {
  return apiClient.post<IssueCredentialResponse>('/api/credentials', req, { signal });
}

export async function uploadDocument(
  file: File,
  signal?: AbortSignal,
): Promise<DocumentUploadResponse> {
  const formData = new FormData();
  formData.append('file', file);
  return apiClient.post<DocumentUploadResponse>('/api/documents', formData, { signal });
}

export async function fetchDocumentQrBlob(
  documentId: string,
  signal?: AbortSignal,
): Promise<Blob> {
  return apiClient.getBlob(`/api/documents/${encodeURIComponent(documentId)}/qr`, { signal });
}

export async function getCredentialLifecycle(
  id: string,
  signal?: AbortSignal,
): Promise<CredentialLifecycleResponse> {
  return apiClient.get<CredentialLifecycleResponse>(
    `/api/credentials/${encodeURIComponent(id)}/lifecycle`,
    { signal },
  );
}

export async function getCredentialHistory(
  id: string,
  signal?: AbortSignal,
): Promise<CredentialLifecycleEvent[]> {
  return apiClient.get<CredentialLifecycleEvent[]>(
    `/api/credentials/${encodeURIComponent(id)}/lifecycle/history`,
    { signal },
  );
}

export async function suspendCredential(
  id: string,
  reason: string,
  signal?: AbortSignal,
): Promise<CredentialLifecycleResponse> {
  return apiClient.post<CredentialLifecycleResponse>(
    `/api/credentials/${encodeURIComponent(id)}/suspend`,
    { reason },
    { signal },
  );
}

export async function resumeCredential(
  id: string,
  reason: string,
  signal?: AbortSignal,
): Promise<CredentialLifecycleResponse> {
  return apiClient.post<CredentialLifecycleResponse>(
    `/api/credentials/${encodeURIComponent(id)}/resume`,
    { reason },
    { signal },
  );
}

export async function revokeCredential(
  id: string,
  reason: string,
  signal?: AbortSignal,
): Promise<CredentialLifecycleResponse> {
  return apiClient.post<CredentialLifecycleResponse>(
    `/api/credentials/${encodeURIComponent(id)}/revoke`,
    { reason },
    { signal },
  );
}

// Holder APIs
export async function listHolderCredentials(
  signal?: AbortSignal,
): Promise<HolderCredentialDto[]> {
  return apiClient.get<HolderCredentialDto[]>('/api/holder/credentials', { signal });
}

export async function getHolderCredential(
  id: string,
  signal?: AbortSignal,
): Promise<HolderCredentialDto> {
  return apiClient.get<HolderCredentialDto>(
    `/api/holder/credentials/${encodeURIComponent(id)}`,
    { signal },
  );
}

export async function provisionHolderCredential(
  credentialId: string,
  signal?: AbortSignal,
): Promise<HolderCredentialDto> {
  return apiClient.post<HolderCredentialDto>(
    '/api/holder/credentials/provision',
    { credentialId },
    { signal },
  );
}

export async function suspendHolderCredential(
  id: string,
  signal?: AbortSignal,
): Promise<HolderCredentialDto> {
  return apiClient.post<HolderCredentialDto>(
    `/api/holder/credentials/${encodeURIComponent(id)}/suspend`,
    {},
    { signal },
  );
}

export async function activateHolderCredential(
  id: string,
  signal?: AbortSignal,
): Promise<HolderCredentialDto> {
  return apiClient.post<HolderCredentialDto>(
    `/api/holder/credentials/${encodeURIComponent(id)}/activate`,
    {},
    { signal },
  );
}

export async function revokeHolderCredential(
  id: string,
  signal?: AbortSignal,
): Promise<HolderCredentialDto> {
  return apiClient.post<HolderCredentialDto>(
    `/api/holder/credentials/${encodeURIComponent(id)}/revoke`,
    {},
    { signal },
  );
}

export async function generateHolderPredicateProof(
  req: GenerateProofRequest,
  signal?: AbortSignal,
): Promise<HolderPredicateProofResponse> {
  return apiClient.post<HolderPredicateProofResponse>(
    '/api/holder/credentials/proofs/generate',
    req,
    { signal },
  );
}
