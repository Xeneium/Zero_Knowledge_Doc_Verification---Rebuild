import { useEffect, useState } from 'react';
import { getBackendHealth } from '../lib/api/health';

export type BackendHealthState =
  { status: 'checking' } | { status: 'available' } | { status: 'unavailable' };

export function useBackendHealth(): BackendHealthState {
  const [state, setState] = useState<BackendHealthState>({
    status: 'checking',
  });

  useEffect(() => {
    const controller = new AbortController();

    void getBackendHealth(controller.signal)
      .then(() => setState({ status: 'available' }))
      .catch(() => {
        if (!controller.signal.aborted) setState({ status: 'unavailable' });
      });

    return () => controller.abort();
  }, []);

  return state;
}
