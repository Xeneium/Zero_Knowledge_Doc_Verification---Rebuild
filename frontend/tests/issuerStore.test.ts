import { describe, it, expect, beforeEach, vi } from 'vitest';
import { issuerStore } from '../src/state/issuerStore';

describe('issuerStore Session Activity Ledger', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    issuerStore.clearActivity();
  });

  it('initializes with empty activity ledger when sessionStorage is clean', () => {
    expect(issuerStore.getState().activities).toEqual([]);
  });

  it('adds document and credential activities with ISO timestamp', () => {
    issuerStore.addActivity({
      id: 'doc-uuid-1',
      type: 'document',
      title: 'degree-certificate.pdf',
      digest:
        'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      status: 'STORED & SIGNED',
    });

    const state = issuerStore.getState();
    expect(state.activities).toHaveLength(1);
    expect(state.activities[0].id).toBe('doc-uuid-1');
    expect(state.activities[0].type).toBe('document');
    expect(state.activities[0].title).toBe('degree-certificate.pdf');
    expect(state.activities[0].status).toBe('STORED & SIGNED');
    expect(state.activities[0].timestamp).toBeDefined();

    // Verify persisted in sessionStorage
    const stored = window.sessionStorage.getItem('zkdoc_issuer_activity');
    expect(stored).not.toBeNull();
    const parsed = JSON.parse(stored!) as Array<{ id: string }>;
    expect(parsed[0].id).toBe('doc-uuid-1');
  });

  it('deduplicates and brings re-added activity to top', () => {
    issuerStore.addActivity({
      id: 'item-1',
      type: 'document',
      title: 'Doc 1',
      digest: 'digest-1',
    });

    issuerStore.addActivity({
      id: 'item-2',
      type: 'credential',
      title: 'Cred 2',
      digest: 'digest-2',
    });

    expect(issuerStore.getState().activities[0].id).toBe('item-2');

    // Re-add item-1
    issuerStore.addActivity({
      id: 'item-1',
      type: 'document',
      title: 'Doc 1 Updated',
      digest: 'digest-1',
    });

    const activities = issuerStore.getState().activities;
    expect(activities).toHaveLength(2);
    expect(activities[0].id).toBe('item-1');
    expect(activities[0].title).toBe('Doc 1 Updated');
  });

  it('caps activity list at 50 items', () => {
    for (let i = 0; i < 60; i++) {
      issuerStore.addActivity({
        id: `item-${i}`,
        type: 'document',
        title: `Doc ${i}`,
        digest: `digest-${i}`,
      });
    }

    const activities = issuerStore.getState().activities;
    expect(activities).toHaveLength(50);
    expect(activities[0].id).toBe('item-59');
  });

  it('updates lifecycle status of existing activity item', () => {
    issuerStore.addActivity({
      id: 'cred-100',
      type: 'credential',
      title: 'Diploma',
      digest: 'digest-100',
      status: 'ACTIVE',
    });

    issuerStore.updateStatus('cred-100', 'SUSPENDED');
    expect(issuerStore.getState().activities[0].status).toBe('SUSPENDED');

    issuerStore.updateStatus('cred-100', 'REVOKED');
    expect(issuerStore.getState().activities[0].status).toBe('REVOKED');
  });

  it('clears activity ledger and deletes session storage item', () => {
    issuerStore.addActivity({
      id: 'cred-200',
      type: 'credential',
      title: 'License',
      digest: 'digest-200',
    });

    expect(issuerStore.getState().activities).toHaveLength(1);
    expect(
      window.sessionStorage.getItem('zkdoc_issuer_activity'),
    ).not.toBeNull();

    issuerStore.clearActivity();
    expect(issuerStore.getState().activities).toEqual([]);
    expect(window.sessionStorage.getItem('zkdoc_issuer_activity')).toBeNull();
  });

  it('notifies subscribers upon state mutation', () => {
    const subscriber = vi.fn();
    const unsubscribe = issuerStore.subscribe(subscriber);

    issuerStore.addActivity({
      id: 'sub-test',
      type: 'document',
      title: 'Test',
      digest: 'test-digest',
    });

    expect(subscriber).toHaveBeenCalledTimes(1);
    expect(subscriber).toHaveBeenCalledWith(
      expect.objectContaining({
        activities: expect.arrayContaining([
          expect.objectContaining({ id: 'sub-test' }),
        ]),
      }),
    );

    unsubscribe();
    issuerStore.clearActivity();
    expect(subscriber).toHaveBeenCalledTimes(1); // Not called after unsubscribe
  });
});
