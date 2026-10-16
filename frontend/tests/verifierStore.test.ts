import { describe, it, expect, beforeEach } from 'vitest';
import {
  verifierStore,
  MAX_VERIFIER_HISTORY_ITEMS,
  type UnifiedVerificationResult,
} from '../src/state/verifierStore';

describe('verifierStore State Management', () => {
  beforeEach(() => {
    verifierStore.reset();
  });

  it('initializes with clean default state', () => {
    const state = verifierStore.getState();
    expect(state.activeResult).toBeNull();
    expect(state.isLoading).toBe(false);
    expect(state.error).toBeNull();
    expect(state.history).toHaveLength(0);
  });

  it('sets active result, prepends to history, and clears error and loading', () => {
    verifierStore.setError({
      type: 'NOT_FOUND',
      message: 'Token not found',
    });
    verifierStore.setLoading(true);

    const mockResult: UnifiedVerificationResult = {
      kind: 'PUBLIC_CAPABILITY',
      token: 'test-token-1',
      response: {
        verified: true,
        valid: true,
        status: 'VALID',
        mode: 'MODE_B_REGISTRY_CAPABILITY',
        protocolVersion: 'QR_V1',
        signatureValid: true,
        active: true,
        failureReason: null,
      },
      timestamp: '2026-10-15T09:00:00Z',
    };

    verifierStore.setResult(mockResult);

    const state = verifierStore.getState();
    expect(state.activeResult).toEqual(mockResult);
    expect(state.isLoading).toBe(false);
    expect(state.error).toBeNull();
    expect(state.history).toHaveLength(1);
    expect(state.history[0]).toEqual(mockResult);
  });

  it('deduplicates matching items in history', () => {
    const mockResult: UnifiedVerificationResult = {
      kind: 'PUBLIC_CAPABILITY',
      token: 'duplicate-token',
      response: {
        verified: true,
        valid: true,
        status: 'VALID',
        mode: 'MODE_B_REGISTRY_CAPABILITY',
        protocolVersion: 'QR_V1',
        signatureValid: true,
        active: true,
        failureReason: null,
      },
      timestamp: '2026-10-15T09:00:00Z',
    };

    verifierStore.setResult(mockResult);
    expect(verifierStore.getState().history).toHaveLength(1);

    // Re-verify same token with updated status
    const updatedResult: UnifiedVerificationResult = {
      ...mockResult,
      timestamp: '2026-10-15T09:05:00Z',
    };
    verifierStore.setResult(updatedResult);

    const state = verifierStore.getState();
    expect(state.history).toHaveLength(1);
    expect(state.history[0].timestamp).toBe('2026-10-15T09:05:00Z');
  });

  it('enforces maximum capacity of 20 history items', () => {
    for (let i = 0; i < 25; i++) {
      verifierStore.setResult({
        kind: 'PUBLIC_CAPABILITY',
        token: `token-${i}`,
        response: {
          verified: true,
          valid: true,
          status: 'VALID',
          mode: 'MODE_B_REGISTRY_CAPABILITY',
          protocolVersion: 'QR_V1',
          signatureValid: true,
          active: true,
          failureReason: null,
        },
        timestamp: new Date().toISOString(),
      });
    }

    const state = verifierStore.getState();
    expect(state.history).toHaveLength(MAX_VERIFIER_HISTORY_ITEMS);
    expect((state.history[0] as { token: string }).token).toBe('token-24');
  });

  it('clears active result while preserving history', () => {
    verifierStore.setResult({
      kind: 'PUBLIC_CAPABILITY',
      token: 'token-abc',
      response: {
        verified: true,
        valid: true,
        status: 'VALID',
        mode: 'MODE_B_REGISTRY_CAPABILITY',
        protocolVersion: 'QR_V1',
        signatureValid: true,
        active: true,
        failureReason: null,
      },
      timestamp: new Date().toISOString(),
    });

    verifierStore.clearResult();

    const state = verifierStore.getState();
    expect(state.activeResult).toBeNull();
    expect(state.history).toHaveLength(1);
  });

  it('sets and clears error state properly', () => {
    verifierStore.setError({
      type: 'RATE_LIMITED',
      message: 'Capacity saturated',
    });

    expect(verifierStore.getState().error?.type).toBe('RATE_LIMITED');
    expect(verifierStore.getState().isLoading).toBe(false);

    verifierStore.setError(null);
    expect(verifierStore.getState().error).toBeNull();
  });

  it('clears history when requested', () => {
    verifierStore.setResult({
      kind: 'PUBLIC_CAPABILITY',
      token: 'token-to-clear',
      response: {
        verified: true,
        valid: true,
        status: 'VALID',
        mode: 'MODE_B_REGISTRY_CAPABILITY',
        protocolVersion: 'QR_V1',
        signatureValid: true,
        active: true,
        failureReason: null,
      },
      timestamp: new Date().toISOString(),
    });

    expect(verifierStore.getState().history).toHaveLength(1);
    verifierStore.clearHistory();
    expect(verifierStore.getState().history).toHaveLength(0);
  });
});
