import { CheckCircle2, CircleOff, LoaderCircle } from 'lucide-react';
import { useBackendHealth } from '../../hooks/useBackendHealth';

export function BackendStatus() {
  const health = useBackendHealth();

  if (health.status === 'checking') {
    return (
      <div
        role="status"
        className="text-ink-600 flex items-center gap-3 text-sm"
      >
        <LoaderCircle className="size-5 animate-spin" aria-hidden="true" />
        <div>
          <p className="text-ink-800 font-semibold">Checking API connection</p>
          <p>Contacting the configured health endpoint…</p>
        </div>
      </div>
    );
  }

  if (health.status === 'available') {
    return (
      <div
        role="status"
        className="text-success-700 flex items-center gap-3 text-sm"
      >
        <CheckCircle2 className="size-5" aria-hidden="true" />
        <div>
          <p className="font-semibold">Backend available</p>
          <p className="text-ink-600">
            The real health endpoint returned status ok.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div
      role="status"
      className="text-warning-700 flex items-center gap-3 text-sm"
    >
      <CircleOff className="size-5" aria-hidden="true" />
      <div>
        <p className="font-semibold">Backend unavailable</p>
        <p className="text-ink-600">
          The interface remains available. Start the API to restore
          connectivity.
        </p>
      </div>
    </div>
  );
}
