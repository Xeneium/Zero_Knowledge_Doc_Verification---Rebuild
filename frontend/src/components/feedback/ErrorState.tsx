import React from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { Button } from '../common/Button';
import { cn } from '../../lib/utils/cn';

export interface ErrorStateProps extends React.HTMLAttributes<HTMLDivElement> {
  title?: string;
  message?: string;
  errorCode?: string;
  correlationId?: string;
  onRetry?: () => void;
  action?: React.ReactNode;
}

export function ErrorState({
  title = 'An error occurred',
  message = 'We encountered an unexpected problem communicating with the verification service.',
  errorCode,
  correlationId,
  onRetry,
  action,
  className,
  ...props
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center justify-center rounded-xl border border-red-200 bg-red-50/50 p-8 text-center transition-colors',
        'dark:border-red-900/50 dark:bg-red-950/20',
        className,
      )}
      {...props}
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600 dark:bg-red-900/40 dark:text-red-400">
        <AlertCircle className="h-6 w-6" aria-hidden="true" />
      </div>
      <h3 className="mt-4 text-base font-semibold text-slate-900 dark:text-slate-100">
        {title}
      </h3>
      <p className="mt-1 max-w-md text-sm text-slate-600 dark:text-slate-300">
        {message}
      </p>

      {(errorCode || correlationId) && (
        <div className="mt-4 inline-flex flex-col gap-1 rounded-md border border-red-200 bg-white px-3.5 py-2 text-left font-mono text-xs text-slate-600 shadow-2xs dark:border-red-900/40 dark:bg-slate-900 dark:text-slate-400">
          {errorCode && (
            <div>
              <span className="font-semibold text-slate-700 dark:text-slate-300">
                Error Code:
              </span>{' '}
              {errorCode}
            </div>
          )}
          {correlationId && (
            <div>
              <span className="font-semibold text-slate-700 dark:text-slate-300">
                Correlation ID:
              </span>{' '}
              <span className="break-all">{correlationId}</span>
            </div>
          )}
        </div>
      )}

      <div className="mt-6 flex items-center gap-3">
        {onRetry && (
          <Button
            variant="outline"
            onClick={onRetry}
            className="flex items-center gap-2"
          >
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            <span>Try Again</span>
          </Button>
        )}
        {action}
      </div>
    </div>
  );
}
