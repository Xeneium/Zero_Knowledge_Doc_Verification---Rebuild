import { useState, useEffect } from 'react';
import { registerTeardownListener } from './authStore';

export interface IssuerActivityItem {
  id: string; // Document ID or Credential ID
  type: 'document' | 'credential';
  title: string;
  digest: string;
  timestamp: string;
  status?: string;
  issuerId?: string;
}

export interface IssuerState {
  activities: IssuerActivityItem[];
}

const STORAGE_KEY = 'zkdoc_issuer_activity';

function loadInitialActivities(): IssuerActivityItem[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed as IssuerActivityItem[];
    }
  } catch {
    window.sessionStorage.removeItem(STORAGE_KEY);
  }
  return [];
}

function persistActivities(activities: IssuerActivityItem[]): void {
  if (typeof window === 'undefined') return;
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(activities));
  } catch {
    // Storage failure non-fatal
  }
}

class IssuerStore {
  private state: IssuerState;
  private listeners: Set<(state: IssuerState) => void> = new Set();

  constructor() {
    this.state = {
      activities: loadInitialActivities(),
    };
    registerTeardownListener(() => this.clearActivity());
  }

  public getState(): IssuerState {
    return this.state;
  }

  public subscribe(listener: (state: IssuerState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  public addActivity(item: Omit<IssuerActivityItem, 'timestamp'>): void {
    const filtered = this.state.activities.filter((a) => a.id !== item.id);
    const newActivities: IssuerActivityItem[] = [
      {
        ...item,
        timestamp: new Date().toISOString(),
      },
      ...filtered,
    ].slice(0, 50);

    this.state = { activities: newActivities };
    persistActivities(newActivities);
    this.notify();
  }

  public updateStatus(id: string, status: string): void {
    const newActivities = this.state.activities.map((a) =>
      a.id === id ? { ...a, status } : a,
    );
    this.state = { activities: newActivities };
    persistActivities(newActivities);
    this.notify();
  }

  public clearActivity(): void {
    if (typeof window !== 'undefined') {
      window.sessionStorage.removeItem(STORAGE_KEY);
    }
    this.state = { activities: [] };
    this.notify();
  }

  private notify(): void {
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }
}

export const issuerStore = new IssuerStore();

/**
 * React hook for consuming issuer session activities with reactive updates.
 */
export function useIssuerActivities(): IssuerActivityItem[] {
  const [activities, setActivities] = useState<IssuerActivityItem[]>(
    issuerStore.getState().activities,
  );

  useEffect(() => {
    return issuerStore.subscribe((s) => setActivities(s.activities));
  }, []);

  return activities;
}
