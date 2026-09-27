import { Camera, CameraOff, RefreshCw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Alert } from '../../../components/ui/Alert';
import { Button } from '../../../components/ui/Button';
import {
  startQrScanner,
  type ScannerSession,
  type ScannerStarter,
} from '../scanner/startQrScanner';

type ScannerState = 'idle' | 'starting' | 'scanning' | 'error';

export interface QrScannerProps {
  onScan: (value: string) => void;
  scannerStarter?: ScannerStarter;
}

function cameraErrorMessage(error: unknown): string {
  if (error instanceof DOMException && error.name === 'NotAllowedError') {
    return 'Camera permission was denied. Use manual entry or allow camera access and try again.';
  }
  if (error instanceof DOMException && error.name === 'NotFoundError') {
    return 'No compatible camera was found. Use manual verification instead.';
  }
  return 'The camera could not be started. Use manual verification or try again.';
}

export function QrScanner({
  onScan,
  scannerStarter = startQrScanner,
}: QrScannerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const sessionRef = useRef<ScannerSession | undefined>(undefined);
  const activeRef = useRef(false);
  const operationRef = useRef(0);
  const mountedRef = useRef(true);
  const [state, setState] = useState<ScannerState>('idle');
  const [error, setError] = useState<string>();

  const stop = () => {
    activeRef.current = false;
    operationRef.current += 1;
    sessionRef.current?.stop();
    sessionRef.current = undefined;
    if (mountedRef.current) setState('idle');
  };

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      activeRef.current = false;
      operationRef.current += 1;
      sessionRef.current?.stop();
      sessionRef.current = undefined;
    };
  }, []);

  async function start() {
    if (activeRef.current) return;
    if (!navigator.mediaDevices?.getUserMedia) {
      setError(
        'Camera scanning is not supported in this browser. Use manual verification instead.',
      );
      setState('error');
      return;
    }

    const video = videoRef.current;
    if (!video) return;
    activeRef.current = true;
    const operation = ++operationRef.current;
    setError(undefined);
    setState('starting');

    try {
      const session = await scannerStarter(video, (value) => {
        if (!mountedRef.current || operation !== operationRef.current) return;
        activeRef.current = false;
        sessionRef.current?.stop();
        sessionRef.current = undefined;
        setState('idle');
        onScan(value);
      });
      if (
        !mountedRef.current ||
        operation !== operationRef.current ||
        !activeRef.current
      ) {
        session.stop();
        return;
      }
      sessionRef.current = session;
      setState('scanning');
    } catch (scannerError) {
      if (!mountedRef.current || operation !== operationRef.current) return;
      activeRef.current = false;
      setError(cameraErrorMessage(scannerError));
      setState('error');
    }
  }

  const active = state === 'starting' || state === 'scanning';

  return (
    <section
      aria-labelledby="qr-scanner-heading"
      className="border-ink-200 rounded-xl border bg-white p-5 sm:p-6"
    >
      <div className="flex items-start gap-3">
        <span className="bg-accent-50 text-accent-700 inline-flex size-10 shrink-0 items-center justify-center rounded-lg">
          <Camera className="size-5" aria-hidden="true" />
        </span>
        <div>
          <h2 id="qr-scanner-heading" className="text-ink-900 font-semibold">
            Scan a verification QR code
          </h2>
          <p className="text-ink-600 mt-1 text-sm leading-6">
            Camera access starts only when you choose to scan. Frames stay in
            your browser and are not uploaded.
          </p>
        </div>
      </div>

      <div className="bg-ink-950 relative mt-5 overflow-hidden rounded-lg">
        <video
          ref={videoRef}
          muted
          playsInline
          aria-label="QR-code camera preview"
          className={active ? 'aspect-video w-full object-cover' : 'hidden'}
        />
        {!active ? (
          <div className="text-ink-300 flex aspect-video items-center justify-center">
            <div className="text-center">
              <CameraOff className="mx-auto size-8" aria-hidden="true" />
              <p className="mt-2 text-sm">Camera is off</p>
            </div>
          </div>
        ) : null}
        {state === 'scanning' ? (
          <div
            className="border-accent-400 pointer-events-none absolute inset-6 rounded-lg border-2"
            aria-hidden="true"
          />
        ) : null}
      </div>

      {error ? (
        <Alert tone="warning" title="Camera unavailable" className="mt-4">
          {error}
        </Alert>
      ) : null}

      <div className="mt-4 flex flex-wrap gap-3">
        {!active ? (
          <Button type="button" onClick={() => void start()}>
            {state === 'error' ? (
              <RefreshCw className="size-4" aria-hidden="true" />
            ) : (
              <Camera className="size-4" aria-hidden="true" />
            )}
            {state === 'error' ? 'Try camera again' : 'Start camera'}
          </Button>
        ) : (
          <Button type="button" variant="secondary" onClick={stop}>
            <CameraOff className="size-4" aria-hidden="true" /> Stop camera
          </Button>
        )}
        {state === 'starting' ? (
          <span role="status" className="text-ink-600 self-center text-sm">
            Requesting camera access…
          </span>
        ) : null}
        {state === 'scanning' ? (
          <span role="status" className="text-ink-600 self-center text-sm">
            Looking for a supported QR code…
          </span>
        ) : null}
      </div>
    </section>
  );
}
