import { ApiError } from '../../../lib/api/api-error';
import { apiClient } from '../../../lib/api/client';
import type { PublicVerificationResponse } from '../types/verification.types';

function isPublicVerificationResponse(
  value: unknown,
): value is PublicVerificationResponse {
  return (
    typeof value === 'object' &&
    value !== null &&
    'signatureValid' in value &&
    typeof value.signatureValid === 'boolean' &&
    'documentActive' in value &&
    typeof value.documentActive === 'boolean'
  );
}

export async function verifyPublicDocument(
  token: string,
  signal?: AbortSignal,
): Promise<PublicVerificationResponse> {
  const response = await apiClient.get<unknown>(
    `/api/public/verify/${encodeURIComponent(token)}`,
    { signal },
  );

  if (!isPublicVerificationResponse(response)) {
    throw new ApiError(
      'The backend returned an unexpected verification response.',
      {
        kind: 'invalid-response',
      },
    );
  }

  return response;
}
