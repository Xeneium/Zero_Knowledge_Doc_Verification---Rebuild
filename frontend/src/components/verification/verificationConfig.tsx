import React from 'react';
import {
  CheckCircle2,
  AlertOctagon,
  Clock,
  ShieldCheck,
  XCircle,
  HelpCircle,
  RefreshCw,
} from 'lucide-react';

export type VerificationState =
  | 'VERIFIED_ACTIVE'
  | 'CREDENTIAL_REVOKED'
  | 'CREDENTIAL_EXPIRED'
  | 'PREDICATE_SATISFIED'
  | 'PROOF_REJECTED'
  | 'NOT_RECOGNIZED'
  | 'UNAVAILABLE';

export interface VerificationStatusConfig {
  label: string;
  title: string;
  description: string;
  variant: 'success' | 'danger' | 'warning' | 'neutral';
  icon: React.ReactNode;
}

export const VERIFICATION_STATES: Record<
  VerificationState,
  VerificationStatusConfig
> = {
  VERIFIED_ACTIVE: {
    label: 'VERIFIED & ACTIVE',
    title: 'Cryptographic Credential Verified',
    description:
      'The digital signature has been verified against the active issuer public key, and the credential status is confirmed ACTIVE in the registry.',
    variant: 'success',
    icon: (
      <CheckCircle2
        className="h-6 w-6 text-green-600 dark:text-green-400"
        aria-hidden="true"
      />
    ),
  },
  CREDENTIAL_REVOKED: {
    label: 'CREDENTIAL REVOKED',
    title: 'Credential Has Been Revoked',
    description:
      'Although the cryptographic signature is authentic, this credential has been revoked by the issuing authority and is no longer valid.',
    variant: 'danger',
    icon: (
      <AlertOctagon
        className="h-6 w-6 text-red-600 dark:text-red-400"
        aria-hidden="true"
      />
    ),
  },
  CREDENTIAL_EXPIRED: {
    label: 'CREDENTIAL EXPIRED',
    title: 'Credential Validity Expired',
    description:
      'The cryptographic signature was authentic, but the established expiration timestamp has passed.',
    variant: 'warning',
    icon: (
      <Clock
        className="h-6 w-6 text-amber-600 dark:text-amber-400"
        aria-hidden="true"
      />
    ),
  },
  PREDICATE_SATISFIED: {
    label: 'PREDICATE SATISFIED',
    title: 'Zero-Knowledge Proof Verified',
    description:
      'The Zero-Knowledge predicate proof mathematically proves that the hidden credential attribute satisfies the threshold claim without disclosing private data.',
    variant: 'success',
    icon: (
      <ShieldCheck
        className="h-6 w-6 text-green-600 dark:text-green-400"
        aria-hidden="true"
      />
    ),
  },
  PROOF_REJECTED: {
    label: 'PROOF REJECTED',
    title: 'Zero-Knowledge Proof Invalid',
    description:
      'The submitted Zero-Knowledge proof failed mathematical verification against the registered commitment and challenge nonce.',
    variant: 'danger',
    icon: (
      <XCircle
        className="h-6 w-6 text-red-600 dark:text-red-400"
        aria-hidden="true"
      />
    ),
  },
  NOT_RECOGNIZED: {
    label: 'NOT RECOGNIZED',
    title: 'Credential Not Found in Registry',
    description:
      'No cryptographic record was found matching this credential identifier. The credential may be unissued or from an unregistered authority.',
    variant: 'neutral',
    icon: (
      <HelpCircle
        className="h-6 w-6 text-slate-500 dark:text-slate-400"
        aria-hidden="true"
      />
    ),
  },
  UNAVAILABLE: {
    label: 'VERIFICATION UNAVAILABLE',
    title: 'Verification Service Unavailable',
    description:
      'Unable to connect to the backend verification engine. The service may be temporarily offline or performing recovery reconciliation.',
    variant: 'warning',
    icon: (
      <RefreshCw
        className="h-6 w-6 text-amber-600 dark:text-amber-400"
        aria-hidden="true"
      />
    ),
  },
};
