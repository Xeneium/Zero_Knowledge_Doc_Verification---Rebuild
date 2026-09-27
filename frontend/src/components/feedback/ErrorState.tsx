import { AlertTriangle } from 'lucide-react';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';

export interface ErrorStateProps {
  title?: string;
  description?: string;
  onRetry?: () => void;
}

export function ErrorState({
  title = 'Something went wrong',
  description = 'This view could not be loaded. Try again or return to the overview.',
  onRetry,
}: ErrorStateProps) {
  return (
    <Card
      role="alert"
      className="flex flex-col items-center px-6 py-12 text-center"
    >
      <span className="bg-danger-100 text-danger-700 mb-4 inline-flex size-12 items-center justify-center rounded-xl">
        <AlertTriangle className="size-6" aria-hidden="true" />
      </span>
      <h2 className="text-ink-900 text-lg font-semibold">{title}</h2>
      <p className="text-ink-600 mt-2 max-w-md text-sm">{description}</p>
      {onRetry ? (
        <Button className="mt-5" onClick={onRetry}>
          Try again
        </Button>
      ) : null}
    </Card>
  );
}
