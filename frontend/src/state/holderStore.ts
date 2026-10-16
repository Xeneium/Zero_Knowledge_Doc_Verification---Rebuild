import { useSyncExternalStore } from 'react';
import type {
  HolderCredentialResponse,
  HolderPredicateProofResponse,
} from '../api/types';
import { registerTeardownListener } from './authStore';

export type HolderFilterStatus = 'ALL' | 'ACTIVE' | 'SUSPENDED' | 'REVOKED';

export interface HolderState {
  credentials: HolderCredentialResponse[];
  isLoading: boolean;
  error: string | null;
  selectedCredentialId: string | null;
  generatedProofs: HolderPredicateProofResponse[];
  filterStatus: HolderFilterStatus;
  searchQuery: string;
}

const MAX_CACHED_PROOFS = 20;

class HolderStoreManager {
  private state: HolderState = {
    credentials: [],
    isLoading: false,
    error: null,
    selectedCredentialId: null,
    generatedProofs: [],
    filterStatus: 'ALL',
    searchQuery: '',
  };

  private listeners = new Set<() => void>();

  constructor() {
    registerTeardownListener(() => this.reset());
  }

  getState(): HolderState {
    return this.state;
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  private notify(): void {
    for (const listener of this.listeners) {
      listener();
    }
  }

  setCredentials(credentials: HolderCredentialResponse[]): void {
    this.state = {
      ...this.state,
      credentials,
      isLoading: false,
      error: null,
    };
    this.notify();
  }

  addCredential(credential: HolderCredentialResponse): void {
    const existingIndex = this.state.credentials.findIndex(
      (c) => c.id === credential.id,
    );
    let updated: HolderCredentialResponse[];
    if (existingIndex >= 0) {
      updated = [...this.state.credentials];
      updated[existingIndex] = credential;
    } else {
      updated = [credential, ...this.state.credentials];
    }
    this.state = {
      ...this.state,
      credentials: updated,
    };
    this.notify();
  }

  updateCredentialStatus(id: string, status: string, version: number): void {
    const updated = this.state.credentials.map((c) =>
      c.id === id
        ? { ...c, status, version, updatedAt: new Date().toISOString() }
        : c,
    );
    this.state = {
      ...this.state,
      credentials: updated,
    };
    this.notify();
  }

  setSelectedCredentialId(id: string | null): void {
    this.state = {
      ...this.state,
      selectedCredentialId: id,
    };
    this.notify();
  }

  addGeneratedProof(proof: HolderPredicateProofResponse): void {
    const updated = [proof, ...this.state.generatedProofs].slice(
      0,
      MAX_CACHED_PROOFS,
    );
    this.state = {
      ...this.state,
      generatedProofs: updated,
    };
    this.notify();
  }

  clearProofs(): void {
    this.state = {
      ...this.state,
      generatedProofs: [],
    };
    this.notify();
  }

  setFilterStatus(filterStatus: HolderFilterStatus): void {
    this.state = {
      ...this.state,
      filterStatus,
    };
    this.notify();
  }

  setSearchQuery(searchQuery: string): void {
    this.state = {
      ...this.state,
      searchQuery,
    };
    this.notify();
  }

  setLoading(isLoading: boolean): void {
    this.state = {
      ...this.state,
      isLoading,
    };
    this.notify();
  }

  setError(error: string | null): void {
    this.state = {
      ...this.state,
      error,
      isLoading: false,
    };
    this.notify();
  }

  reset(): void {
    this.state = {
      credentials: [],
      isLoading: false,
      error: null,
      selectedCredentialId: null,
      generatedProofs: [],
      filterStatus: 'ALL',
      searchQuery: '',
    };
    this.notify();
  }
}

export const holderStore = new HolderStoreManager();

/**
 * Reactive React hook to consume holder wallet state.
 */
export function useHolderState(): HolderState {
  return useSyncExternalStore(
    holderStore.subscribe,
    () => holderStore.getState(),
    () => holderStore.getState(),
  );
}
