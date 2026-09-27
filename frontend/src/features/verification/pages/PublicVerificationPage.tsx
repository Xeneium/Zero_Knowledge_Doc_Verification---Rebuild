import { RotateCcw, ScanLine, ShieldCheck } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Alert } from '../../../components/ui/Alert';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Input } from '../../../components/ui/Field';
import { ApiError } from '../../../lib/api/api-error';
import { verifyPublicDocument } from '../api/verificationApi';
import { QrScanner } from '../components/QrScanner';
import { VerificationResultPanel } from '../components/VerificationResultPanel';
import {
  toVerificationResult,
  type VerificationResult,
} from '../types/verification.types';
import {
  parseVerificationReference,
  VerificationReferenceError,
} from '../validation/verificationReference';

type RequestState =
  | { status: 'idle' }
  | { status: 'submitting' }
  | { status: 'success'; result: VerificationResult }
  | { status: 'error'; message: string };

function verificationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError))
    return 'Verification could not be completed. Try again.';
  if (error.status === 404)
    return 'This verification reference was not recognized.';
  if (error.status === 429)
    return 'Too many verification attempts. Wait a minute and try again.';
  if (error.kind === 'network')
    return 'The verification service is unavailable. Check the connection and try again.';
  if (error.kind === 'timeout')
    return 'The verification request timed out. No result was established.';
  if (error.kind === 'cancelled')
    return 'The verification request was cancelled.';
  return 'The backend could not establish a trustworthy verification result.';
}

export function PublicVerificationPage() {
  const [reference, setReference] = useState('');
  const [validationError, setValidationError] = useState<string>();
  const [requestState, setRequestState] = useState<RequestState>({
    status: 'idle',
  });
  const referenceInput = useRef<HTMLInputElement>(null);
  const activeRequest = useRef<AbortController | undefined>(undefined);
  const requestPending = useRef(false);
  const requestSequence = useRef(0);

  useEffect(
    () => () => {
      activeRequest.current?.abort();
    },
    [],
  );

  async function executeVerification(rawReference: string) {
    if (requestPending.current) return;

    let token: string;
    try {
      token = parseVerificationReference(rawReference);
    } catch (error) {
      setValidationError(
        error instanceof VerificationReferenceError
          ? error.message
          : 'Enter a valid verification reference.',
      );
      referenceInput.current?.focus();
      return;
    }

    setValidationError(undefined);
    setReference('');
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    requestPending.current = true;
    const sequence = ++requestSequence.current;
    setRequestState({ status: 'submitting' });

    try {
      const response = await verifyPublicDocument(token, controller.signal);
      if (sequence !== requestSequence.current) return;
      setRequestState({
        status: 'success',
        result: toVerificationResult(response),
      });
    } catch (error) {
      if (sequence !== requestSequence.current) return;
      setRequestState({
        status: 'error',
        message: verificationErrorMessage(error),
      });
    } finally {
      if (sequence === requestSequence.current)
        activeRequest.current = undefined;
      if (sequence === requestSequence.current) requestPending.current = false;
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void executeVerification(reference);
  }

  function reset() {
    activeRequest.current?.abort();
    activeRequest.current = undefined;
    requestPending.current = false;
    requestSequence.current += 1;
    setReference('');
    setValidationError(undefined);
    setRequestState({ status: 'idle' });
  }

  return (
    <>
      <PageHeader
        title="Public document verification"
        description="Scan a platform QR code or enter its verification reference. Only the minimum public status is returned."
        breadcrumbs={[
          { label: 'Overview', to: '/' },
          { label: 'Public verification' },
        ]}
      />

      <div className="grid gap-5 lg:grid-cols-[1fr_0.9fr]">
        <Card className="p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <span className="bg-accent-50 text-accent-700 inline-flex size-10 shrink-0 items-center justify-center rounded-lg">
              <ShieldCheck className="size-5" aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-ink-900 font-semibold">
                Enter a verification reference
              </h2>
              <p className="text-ink-600 mt-1 text-sm leading-6">
                Paste the URL encoded by a Verity Zero document QR code, or
                enter its opaque reference. Arbitrary links are rejected and
                never opened.
              </p>
            </div>
          </div>

          <form className="mt-5" onSubmit={submit} noValidate>
            <Input
              ref={referenceInput}
              label="Verification URL or reference"
              name="verificationReference"
              autoComplete="off"
              spellCheck={false}
              value={reference}
              maxLength={2048}
              error={validationError}
              placeholder="https://…/api/public/verify/…"
              onChange={(event) => {
                setReference(event.target.value);
                if (validationError) setValidationError(undefined);
              }}
            />
            <div className="mt-4 flex flex-wrap gap-3">
              <Button
                type="submit"
                loading={requestState.status === 'submitting'}
              >
                <ScanLine className="size-4" aria-hidden="true" />
                {requestState.status === 'submitting'
                  ? 'Verifying…'
                  : 'Verify document'}
              </Button>
              {requestState.status !== 'idle' ? (
                <Button type="button" variant="secondary" onClick={reset}>
                  <RotateCcw className="size-4" aria-hidden="true" /> Verify
                  another
                </Button>
              ) : null}
            </div>
          </form>

          <div className="border-ink-200 mt-6 border-t pt-5">
            <h3 className="text-ink-800 text-sm font-semibold">
              What this public check reveals
            </h3>
            <ul className="text-ink-600 mt-2 space-y-2 text-sm">
              <li>
                • Whether the backend validates the stored document signature.
              </li>
              <li>• Whether the document record is currently active.</li>
              <li>
                • No document contents, owner identity, hash, or signature
                material.
              </li>
            </ul>
          </div>
        </Card>

        <QrScanner onScan={(value) => void executeVerification(value)} />
      </div>

      <section className="mt-5" aria-label="Verification result">
        {requestState.status === 'submitting' ? (
          <Card role="status" aria-live="polite" className="p-5 sm:p-6">
            <div className="flex items-center gap-3">
              <span
                className="border-ink-200 border-t-accent-600 size-5 animate-spin rounded-full border-2"
                aria-hidden="true"
              />
              <div>
                <h2 className="text-ink-900 font-semibold">
                  Verifying with the backend
                </h2>
                <p className="text-ink-600 mt-1 text-sm">
                  No result will be shown until the server completes its
                  cryptographic checks.
                </p>
              </div>
            </div>
          </Card>
        ) : null}
        {requestState.status === 'success' ? (
          <VerificationResultPanel result={requestState.result} />
        ) : null}
        {requestState.status === 'error' ? (
          <Alert tone="danger" title="Unable to verify" className="p-5 sm:p-6">
            {requestState.message} A service failure is not evidence that the
            document is invalid.
          </Alert>
        ) : null}
      </section>
    </>
  );
}
