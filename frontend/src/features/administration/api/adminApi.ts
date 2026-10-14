import { apiClient } from '../../../lib/api/client';

export interface AuditEventDto {
  id: string;
  sequence: number;
  timestamp: string;
  eventType: string;
  category: string;
  actor: string;
  actorType: string;
  targetType: string;
  targetId?: string;
  action: string;
  outcome: 'SUCCESS' | 'FAILURE' | 'DENIED';
  reasonCode?: string;
  correlationId?: string;
  clientIp?: string;
  details?: Record<string, unknown>;
}

export interface AuditQueryResult {
  items: AuditEventDto[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface AuditIntegrityResult {
  valid: boolean;
  totalEventsChecked: number;
  startSequence: number;
  endSequence: number;
  firstBrokenSequence?: number | null;
  message?: string;
}

export interface RegisteredKeyDto {
  id: string;
  keyId: string;
  purpose: string;
  status: 'ACTIVE' | 'RETIRED' | 'REVOKED';
  ownerId: string;
  ownerType: string;
  algorithm: string;
  fingerprint: string;
  publicKeyPem: string;
  createdAt: string;
  activatedAt?: string;
  retiredAt?: string;
  revokedAt?: string;
  replacementKeyId?: string;
}

export interface AuditFilterParams {
  category?: string;
  type?: string;
  actor?: string;
  targetType?: string;
  targetId?: string;
  page?: number;
  size?: number;
}

export async function fetchAuditEvents(
  filters: AuditFilterParams = {},
  signal?: AbortSignal,
): Promise<AuditQueryResult> {
  const params = new URLSearchParams();
  if (filters.category) params.set('category', filters.category);
  if (filters.type) params.set('type', filters.type);
  if (filters.actor) params.set('actor', filters.actor);
  if (filters.targetType) params.set('targetType', filters.targetType);
  if (filters.targetId) params.set('targetId', filters.targetId);
  params.set('page', String(filters.page ?? 0));
  params.set('size', String(filters.size ?? 20));

  return apiClient.get<AuditQueryResult>(`/api/admin/audit/events?${params.toString()}`, { signal });
}

export async function verifyAuditChainIntegrity(
  startSeq = 1,
  endSeq = 9223372036854775807,
  signal?: AbortSignal,
): Promise<AuditIntegrityResult> {
  return apiClient.get<AuditIntegrityResult>(
    `/api/admin/audit/verify-integrity?startSeq=${startSeq}&endSeq=${endSeq}`,
    { signal },
  );
}

export async function fetchRegisteredKeys(signal?: AbortSignal): Promise<RegisteredKeyDto[]> {
  return apiClient.get<RegisteredKeyDto[]>('/api/admin/keys', { signal });
}

export async function updateKeyStatus(
  keyId: string,
  status: 'ACTIVE' | 'RETIRED' | 'REVOKED',
  reason?: string,
  replacementKeyId?: string,
  signal?: AbortSignal,
): Promise<void> {
  return apiClient.patch<void>(
    `/api/admin/keys/${encodeURIComponent(keyId)}/status`,
    { status, reason, replacementKeyId },
    { signal },
  );
}

export async function rotateKey(
  keyId: string,
  replacementKeyId: string,
  signal?: AbortSignal,
): Promise<void> {
  return apiClient.post<void>(
    `/api/admin/keys/${encodeURIComponent(keyId)}/rotate`,
    { replacementKeyId },
    { signal },
  );
}
