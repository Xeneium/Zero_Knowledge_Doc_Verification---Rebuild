import { AlertTriangle, CheckCircle2, CircleOff, ShieldX } from 'lucide-react';
import { Card } from '../../../components/ui/Card';
import type { VerificationResult } from '../types/verification.types';

const presentation = {
  verified: {
    title: 'Document verified',
    description:
      'The backend confirmed both the stored signature and the document’s active status.',
    detail:
      'This result confirms the server-held document record. It does not disclose the document or constitute a zero-knowledge proof.',
    icon: CheckCircle2,
    styles: 'border-success-100 bg-success-50 text-success-700',
  },
  inactive: {
    title: 'Document is inactive',
    description:
      'The backend confirmed the stored signature, but the document record is not active.',
    detail:
      'The public response does not identify why the record is inactive. Contact the document issuer if you need clarification.',
    icon: CircleOff,
    styles: 'border-warning-100 bg-warning-50 text-warning-700',
  },
  invalid: {
    title: 'Document signature is invalid',
    description:
      'The backend completed verification and could not validate the stored document signature.',
    detail:
      'Do not treat this document as verified. Recheck the reference or contact the document issuer.',
    icon: ShieldX,
    styles: 'border-danger-100 bg-danger-50 text-danger-700',
  },
} as const;

export function VerificationResultPanel({
  result,
}: {
  result: VerificationResult;
}) {
  const view = presentation[result.kind];
  const Icon = view.icon;
  return (
    <Card
      role="status"
      aria-live="polite"
      className={`overflow-hidden border ${view.styles}`}
    >
      <div className="flex gap-4 p-5 sm:p-6">
        <span className="inline-flex size-12 shrink-0 items-center justify-center rounded-xl bg-white/80">
          <Icon className="size-6" aria-hidden="true" />
        </span>
        <div>
          <p className="text-xs font-bold tracking-widest uppercase">
            Verification complete
          </p>
          <h2 className="text-ink-950 mt-1 text-xl font-bold">{view.title}</h2>
          <p className="text-ink-800 mt-2 text-sm font-medium">
            {view.description}
          </p>
          <p className="text-ink-600 mt-3 flex gap-2 text-sm leading-6">
            <AlertTriangle
              className="mt-0.5 size-4 shrink-0"
              aria-hidden="true"
            />
            {view.detail}
          </p>
        </div>
      </div>
    </Card>
  );
}
