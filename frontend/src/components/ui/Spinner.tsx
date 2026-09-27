import { LoaderCircle } from 'lucide-react';
import { cn } from '../../lib/utils/cn';

export interface SpinnerProps {
  label?: string;
  className?: string;
}

export function Spinner({ label = 'Loading', className }: SpinnerProps) {
  return (
    <span
      role="status"
      className={cn(
        'text-ink-600 inline-flex items-center gap-2 text-sm',
        className,
      )}
    >
      <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
      <span>{label}</span>
    </span>
  );
}
