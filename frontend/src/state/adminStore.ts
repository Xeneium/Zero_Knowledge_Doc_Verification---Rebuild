import { useSyncExternalStore } from 'react';
import type {
  KeyMetadataResponse,
  RecoveryReconciliationReport,
  AuditQueryResult,
  AuditQueryFilterParams,
  SecurityAuditEventResponse,
  AuditIntegrityResult,
  CredentialLifecycleResponse,
  CredentialLifecycleEvent,
  CredentialVerificationResult,
} from '../api/types';
import { registerTeardownListener } from './authStore';

export interface CredentialOversightState {
  credentialId: string;
  lifecycle: CredentialLifecycleResponse | null;
  history: CredentialLifecycleEvent[];
  verification: CredentialVerificationResult | null;
  isLoading: boolean;
  error: string | null;
}

export interface AdminStoreState {
  // Key Registry
  keys: KeyMetadataResponse[];
  selectedKey: KeyMetadataResponse | null;
  isLoadingKeys: boolean;
  keysError: string | null;

  // Platform Recovery & Health
  recoveryReport: RecoveryReconciliationReport | null;
  isLoadingRecovery: boolean;
  isReconciling: boolean;
  recoveryError: string | null;

  // Security Audit Trail
  auditQueryResult: AuditQueryResult | null;
  auditFilter: AuditQueryFilterParams;
  selectedAuditEvent: SecurityAuditEventResponse | null;
  isLoadingAudit: boolean;
  auditError: string | null;
  auditIntegrity: AuditIntegrityResult | null;
  isVerifyingAudit: boolean;

  // Credential Oversight
  credentialOversight: CredentialOversightState;
}

const initialCredentialOversight: CredentialOversightState = {
  credentialId: '',
  lifecycle: null,
  history: [],
  verification: null,
  isLoading: false,
  error: null,
};

const initialState: AdminStoreState = {
  keys: [],
  selectedKey: null,
  isLoadingKeys: false,
  keysError: null,

  recoveryReport: null,
  isLoadingRecovery: false,
  isReconciling: false,
  recoveryError: null,

  auditQueryResult: null,
  auditFilter: {
    page: 0,
    size: 20,
  },
  selectedAuditEvent: null,
  isLoadingAudit: false,
  auditError: null,
  auditIntegrity: null,
  isVerifyingAudit: false,

  credentialOversight: initialCredentialOversight,
};

let currentState: AdminStoreState = { ...initialState };
const listeners = new Set<() => void>();

function notify() {
  for (const listener of listeners) {
    listener();
  }
}

export const adminStore = {
  getState(): AdminStoreState {
    return currentState;
  },

  subscribe(listener: () => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },

  // Key Registry Mutations
  setKeys(keys: KeyMetadataResponse[]): void {
    currentState = { ...currentState, keys, keysError: null };
    notify();
  },

  setSelectedKey(key: KeyMetadataResponse | null): void {
    currentState = { ...currentState, selectedKey: key };
    notify();
  },

  setLoadingKeys(isLoadingKeys: boolean): void {
    currentState = { ...currentState, isLoadingKeys };
    notify();
  },

  setKeysError(keysError: string | null): void {
    currentState = { ...currentState, keysError };
    notify();
  },

  // Recovery Report Mutations
  setRecoveryReport(report: RecoveryReconciliationReport | null): void {
    currentState = { ...currentState, recoveryReport: report, recoveryError: null };
    notify();
  },

  setLoadingRecovery(isLoadingRecovery: boolean): void {
    currentState = { ...currentState, isLoadingRecovery };
    notify();
  },

  setIsReconciling(isReconciling: boolean): void {
    currentState = { ...currentState, isReconciling };
    notify();
  },

  setRecoveryError(recoveryError: string | null): void {
    currentState = { ...currentState, recoveryError };
    notify();
  },

  // Audit Log Mutations
  setAuditQueryResult(result: AuditQueryResult | null): void {
    currentState = { ...currentState, auditQueryResult: result, auditError: null };
    notify();
  },

  setAuditFilter(filter: Partial<AuditQueryFilterParams>): void {
    currentState = {
      ...currentState,
      auditFilter: { ...currentState.auditFilter, ...filter },
    };
    notify();
  },

  setSelectedAuditEvent(event: SecurityAuditEventResponse | null): void {
    currentState = { ...currentState, selectedAuditEvent: event };
    notify();
  },

  setLoadingAudit(isLoadingAudit: boolean): void {
    currentState = { ...currentState, isLoadingAudit };
    notify();
  },

  setAuditError(auditError: string | null): void {
    currentState = { ...currentState, auditError };
    notify();
  },

  setAuditIntegrity(integrity: AuditIntegrityResult | null): void {
    currentState = { ...currentState, auditIntegrity: integrity };
    notify();
  },

  setIsVerifyingAudit(isVerifyingAudit: boolean): void {
    currentState = { ...currentState, isVerifyingAudit };
    notify();
  },

  // Credential Oversight Mutations
  setCredentialOversight(
    oversight: Partial<CredentialOversightState>,
  ): void {
    currentState = {
      ...currentState,
      credentialOversight: { ...currentState.credentialOversight, ...oversight },
    };
    notify();
  },

  resetCredentialOversight(): void {
    currentState = {
      ...currentState,
      credentialOversight: { ...initialCredentialOversight },
    };
    notify();
  },

  reset(): void {
    currentState = { ...initialState };
    notify();
  },
};

registerTeardownListener(() => adminStore.reset());

// React 18 external store hooks
export function useAdminStore(): AdminStoreState {
  return useSyncExternalStore(
    (listener) => adminStore.subscribe(listener),
    () => adminStore.getState(),
  );
}

export function useAdminKeys(): KeyMetadataResponse[] {
  return useSyncExternalStore(
    (listener) => adminStore.subscribe(listener),
    () => adminStore.getState().keys,
  );
}

export function useAdminRecoveryReport(): RecoveryReconciliationReport | null {
  return useSyncExternalStore(
    (listener) => adminStore.subscribe(listener),
    () => adminStore.getState().recoveryReport,
  );
}

export function useAdminAuditQueryResult(): AuditQueryResult | null {
  return useSyncExternalStore(
    (listener) => adminStore.subscribe(listener),
    () => adminStore.getState().auditQueryResult,
  );
}

export function useAdminCredentialOversight(): CredentialOversightState {
  return useSyncExternalStore(
    (listener) => adminStore.subscribe(listener),
    () => adminStore.getState().credentialOversight,
  );
}
