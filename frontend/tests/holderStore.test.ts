import { describe, it, expect, beforeEach } from 'vitest';
import { holderStore } from '../src/state/holderStore';
import type {
  HolderCredentialResponse,
  HolderPredicateProofResponse,
} from '../src/api/types';

describe('holderStore Wallet State Management', () => {
  const sampleCredential: HolderCredentialResponse = {
    id: 'hc-1',
    holderId: 'holder-alice',
    credentialId: 'cred-1',
    credentialDigestHex: 'abcdef1234567890',
    status: 'ACTIVE',
    version: 1,
    provisionedAt: '2026-10-15T00:00:00Z',
    updatedAt: '2026-10-15T00:00:00Z',
  };

  const sampleProof: HolderPredicateProofResponse = {
    holderCredentialId: 'hc-1',
    credentialId: 'cred-1',
    credentialDigestHex: 'abcdef1234567890',
    attributeName: 'age',
    operator: '>=',
    threshold: 18,
    commitmentPointHex: 'pt-1',
    attestationSignatureHex: 'sig-1',
    attestationKeyId: 'key-1',
    nonceHex: '01020304',
    proofVersion: 'V1_BULLETPROOF',
    bitSize: 64,
    proofBytesHex: 'proof-bytes-hex',
  };

  beforeEach(() => {
    holderStore.reset();
  });

  it('initializes with clean default state', () => {
    const state = holderStore.getState();
    expect(state.credentials).toEqual([]);
    expect(state.isLoading).toBe(false);
    expect(state.error).toBeNull();
    expect(state.selectedCredentialId).toBeNull();
    expect(state.generatedProofs).toEqual([]);
    expect(state.filterStatus).toBe('ALL');
    expect(state.searchQuery).toBe('');
  });

  it('setCredentials populates wallet credentials and resets loading/error', () => {
    holderStore.setLoading(true);
    holderStore.setError('Some error');
    holderStore.setCredentials([sampleCredential]);

    const state = holderStore.getState();
    expect(state.credentials).toHaveLength(1);
    expect(state.credentials[0].id).toBe('hc-1');
    expect(state.isLoading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('addCredential adds new credential to head or updates existing', () => {
    holderStore.addCredential(sampleCredential);
    expect(holderStore.getState().credentials).toHaveLength(1);

    // Add updated version of same credential
    const updated = {
      ...sampleCredential,
      version: 2,
      status: 'SUSPENDED',
    };
    holderStore.addCredential(updated);
    const state = holderStore.getState();
    expect(state.credentials).toHaveLength(1);
    expect(state.credentials[0].version).toBe(2);
    expect(state.credentials[0].status).toBe('SUSPENDED');

    // Add a different credential
    holderStore.addCredential({
      ...sampleCredential,
      id: 'hc-2',
      credentialId: 'cred-2',
    });
    expect(holderStore.getState().credentials).toHaveLength(2);
    expect(holderStore.getState().credentials[0].id).toBe('hc-2');
  });

  it('updateCredentialStatus modifies status and increments version', () => {
    holderStore.setCredentials([sampleCredential]);
    holderStore.updateCredentialStatus('hc-1', 'REVOKED', 3);

    const state = holderStore.getState();
    expect(state.credentials[0].status).toBe('REVOKED');
    expect(state.credentials[0].version).toBe(3);
    expect(state.credentials[0].updatedAt).toBeDefined();
  });

  it('manages selectedCredentialId', () => {
    holderStore.setSelectedCredentialId('hc-1');
    expect(holderStore.getState().selectedCredentialId).toBe('hc-1');

    holderStore.setSelectedCredentialId(null);
    expect(holderStore.getState().selectedCredentialId).toBeNull();
  });

  it('caches generated proofs up to 20 entries and clears on command', () => {
    holderStore.addGeneratedProof(sampleProof);
    expect(holderStore.getState().generatedProofs).toHaveLength(1);
    expect(holderStore.getState().generatedProofs[0].attributeName).toBe('age');

    // Add 25 proofs
    for (let i = 0; i < 25; i++) {
      holderStore.addGeneratedProof({
        ...sampleProof,
        nonceHex: `nonce-${i}`,
      });
    }

    // Must be capped at 20
    expect(holderStore.getState().generatedProofs).toHaveLength(20);

    holderStore.clearProofs();
    expect(holderStore.getState().generatedProofs).toEqual([]);
  });

  it('updates filterStatus and searchQuery', () => {
    holderStore.setFilterStatus('ACTIVE');
    expect(holderStore.getState().filterStatus).toBe('ACTIVE');

    holderStore.setSearchQuery('age-check');
    expect(holderStore.getState().searchQuery).toBe('age-check');
  });

  it('notifies subscribers upon state change', () => {
    let callCount = 0;
    const unsubscribe = holderStore.subscribe(() => {
      callCount++;
    });

    holderStore.setFilterStatus('SUSPENDED');
    holderStore.setSearchQuery('test');
    expect(callCount).toBe(2);

    unsubscribe();
    holderStore.setSearchQuery('test-2');
    expect(callCount).toBe(2);
  });
});
