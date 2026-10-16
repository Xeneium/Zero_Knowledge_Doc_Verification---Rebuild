import { useSyncExternalStore } from 'react';
import type {
  CapabilityPublicVerificationResponse,
  PublicVerificationResult,
  DocumentVerificationResult,
  CredentialVerificationResult,
  CredentialPredicateVerificationResponse,
} from '../api/types';
import { registerTeardownListener } from './authStore';

export const MAX_VERIFIER_HISTORY_ITEMS = 20;

export type UnifiedVerificationResult =
  | {
      kind: 'PUBLIC_CAPABILITY';
      token: string;
      response: CapabilityPublicVerificationResponse;
      timestamp: string;
    }
  | {
      kind: 'PUBLIC_DOCUMENT_TOKEN';
      token: string;
      response: PublicVerificationResult;
      timestamp: string;
    }
  | {
      kind: 'DOCUMENT_UPLOAD';
      documentId: string;
      fileName: string;
      fileSize: number;
      response: DocumentVerificationResult;
      timestamp: string;
    }
  | {
      kind: 'CREDENTIAL_SIGNATURE';
      credentialId: string;
      response: CredentialVerificationResult;
      timestamp: string;
    }
  | {
      kind: 'PREDICATE_PROOF';
      credentialId: string;
      attributeName: string;
      threshold: number;
      proofVersion: string;
      response: CredentialPredicateVerificationResponse;
      timestamp: string;
    };

export interface VerifierErrorState {
  type:
    'NOT_FOUND' | 'RATE_LIMITED' | 'UNAVAILABLE' | 'BAD_REQUEST' | 'FORBIDDEN';
  message: string;
  targetReference?: string;
}

export interface VerifierStoreState {
  activeResult: UnifiedVerificationResult | null;
  isLoading: boolean;
  error: VerifierErrorState | null;
  history: UnifiedVerificationResult[];
}

const initialState: VerifierStoreState = {
  activeResult: null,
  isLoading: false,
  error: null,
  history: [],
};

let currentState: VerifierStoreState = { ...initialState };
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) {
    listener();
  }
}

export const verifierStore = {
  getState: (): VerifierStoreState => {
    return currentState;
  },

  subscribe: (listener: () => void): (() => void) => {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },

  setResult(result: UnifiedVerificationResult): void {
    const updatedHistory = [
      result,
      ...currentState.history.filter((item) => {
        // Deduplicate against active target
        if (
          item.kind === 'PUBLIC_CAPABILITY' &&
          result.kind === 'PUBLIC_CAPABILITY'
        ) {
          return item.token !== result.token;
        }
        if (
          item.kind === 'DOCUMENT_UPLOAD' &&
          result.kind === 'DOCUMENT_UPLOAD'
        ) {
          return item.documentId !== result.documentId;
        }
        if (
          item.kind === 'CREDENTIAL_SIGNATURE' &&
          result.kind === 'CREDENTIAL_SIGNATURE'
        ) {
          return item.credentialId !== result.credentialId;
        }
        if (
          item.kind === 'PREDICATE_PROOF' &&
          result.kind === 'PREDICATE_PROOF'
        ) {
          return !(
            item.credentialId === result.credentialId &&
            item.attributeName === result.attributeName
          );
        }
        return true;
      }),
    ].slice(0, MAX_VERIFIER_HISTORY_ITEMS);

    currentState = {
      ...currentState,
      activeResult: result,
      isLoading: false,
      error: null,
      history: updatedHistory,
    };
    notify();
  },

  clearResult(): void {
    currentState = {
      ...currentState,
      activeResult: null,
      error: null,
      isLoading: false,
    };
    notify();
  },

  setLoading(isLoading: boolean): void {
    currentState = {
      ...currentState,
      isLoading,
      error: isLoading ? null : currentState.error,
    };
    notify();
  },

  setError(error: VerifierErrorState | null): void {
    currentState = {
      ...currentState,
      error,
      isLoading: false,
    };
    notify();
  },

  clearHistory(): void {
    currentState = {
      ...currentState,
      history: [],
    };
    notify();
  },

  reset(): void {
    currentState = { ...initialState, history: [] };
    notify();
  },
};

registerTeardownListener(() => verifierStore.reset());

export function useVerifierStore(): VerifierStoreState {
  return useSyncExternalStore(
    (cb) => verifierStore.subscribe(cb),
    () => verifierStore.getState(),
  );
}

export function useActiveVerification(): UnifiedVerificationResult | null {
  return useSyncExternalStore(
    (cb) => verifierStore.subscribe(cb),
    () => verifierStore.getState().activeResult,
  );
}

export function useVerificationHistory(): UnifiedVerificationResult[] {
  return useSyncExternalStore(
    (cb) => verifierStore.subscribe(cb),
    () => verifierStore.getState().history,
  );
}

export function useVerifierLoading(): boolean {
  return useSyncExternalStore(
    (cb) => verifierStore.subscribe(cb),
    () => verifierStore.getState().isLoading,
  );
}

export function useVerifierError(): VerifierErrorState | null {
  return useSyncExternalStore(
    (cb) => verifierStore.subscribe(cb),
    () => verifierStore.getState().error,
  );
}
