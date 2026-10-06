import type { PublicVerificationResponse } from '../types/verification.types';

/**
 * A single local-only record for presenting the verification experience before
 * an issuer-backed document store is connected. It is intentionally isolated
 * from the API client and never creates or changes backend data.
 */
export const PRESENTATION_DEMO_TOKEN =
  '9AiKKGmUzf3TzfaaYrisSz1A4cdlXZbuqNRID-iYaJU';

export const PRESENTATION_DEMO_URL = `http://localhost:8080/api/public/verify/${PRESENTATION_DEMO_TOKEN}`;

export const PRESENTATION_DEMO_RESPONSE: PublicVerificationResponse = {
  signatureValid: true,
  documentActive: true,
};

export function isPresentationDemoToken(token: string): boolean {
  return token === PRESENTATION_DEMO_TOKEN;
}
