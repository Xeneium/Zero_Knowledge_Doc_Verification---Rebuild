export interface PublicVerificationResponse {
  signatureValid: boolean;
  documentActive: boolean;
}

export type VerificationResult =
  | { kind: 'verified'; signatureValid: true; documentActive: true }
  | { kind: 'inactive'; signatureValid: true; documentActive: false }
  | { kind: 'invalid'; signatureValid: false; documentActive: boolean };

export function toVerificationResult(
  response: PublicVerificationResponse,
): VerificationResult {
  if (!response.signatureValid) {
    return {
      kind: 'invalid',
      signatureValid: false,
      documentActive: response.documentActive,
    };
  }
  if (!response.documentActive) {
    return { kind: 'inactive', signatureValid: true, documentActive: false };
  }
  return { kind: 'verified', signatureValid: true, documentActive: true };
}
