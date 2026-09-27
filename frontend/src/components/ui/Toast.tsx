import { CheckCircle2, X } from 'lucide-react';
import { IconButton } from './IconButton';

export interface ToastProps {
  title: string;
  message?: string;
  onDismiss: () => void;
}

export function Toast({ title, message, onDismiss }: ToastProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="border-ink-200 flex w-full max-w-sm items-start gap-3 rounded-xl border bg-white p-4 shadow-[var(--shadow-lg)]"
    >
      <CheckCircle2
        className="text-success-700 mt-0.5 size-5 shrink-0"
        aria-hidden="true"
      />
      <div className="min-w-0 flex-1">
        <p className="text-ink-900 font-semibold">{title}</p>
        {message ? (
          <p className="text-ink-600 mt-1 text-sm">{message}</p>
        ) : null}
      </div>
      <IconButton
        label="Dismiss notification"
        onClick={onDismiss}
        className="-m-2 size-9"
      >
        <X className="size-4" aria-hidden="true" />
      </IconButton>
    </div>
  );
}
