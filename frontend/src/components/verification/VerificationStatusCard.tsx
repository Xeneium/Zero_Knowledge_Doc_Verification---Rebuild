import React from 'react';
import { cn } from '../../lib/utils/cn';
import {
  VERIFICATION_STATES,
  type VerificationState,
} from './verificationConfig';

export interface VerificationStatusCardProps {
  status: VerificationState;
  customTitle?: string;
  customDescription?: string;
  metadata?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}

export function VerificationStatusCard({
  status,
  customTitle,
  customDescription,
  metadata,
  actions,
  className,
}: VerificationStatusCardProps) {
  const config = VERIFICATION_STATES[status];

  const variantCardStyles = {
    success:
      'border-green-200 bg-green-50/60 dark:border-green-900/60 dark:bg-green-950/20',
    danger:
      'border-red-200 bg-red-50/60 dark:border-red-900/60 dark:bg-red-950/20',
    warning:
      'border-amber-200 bg-amber-50/60 dark:border-amber-900/60 dark:bg-amber-950/20',
    neutral:
      'border-slate-200 bg-slate-50/60 dark:border-slate-800 dark:bg-slate-900/50',
  };

  const variantBadgeStyles = {
    success:
      'bg-green-100 text-green-800 dark:bg-green-900/60 dark:text-green-300 border-green-300 dark:border-green-800',
    danger:
      'bg-red-100 text-red-800 dark:bg-red-900/60 dark:text-red-300 border-red-300 dark:border-red-800',
    warning:
      'bg-amber-100 text-amber-900 dark:bg-amber-900/60 dark:text-amber-300 border-amber-300 dark:border-amber-800',
    neutral:
      'bg-slate-200 text-slate-800 dark:bg-slate-800 dark:text-slate-300 border-slate-300 dark:border-slate-700',
  };

  return (
    <div
      role="region"
      aria-label={`Verification Result: ${config.label}`}
      className={cn(
        'rounded-xl border p-6 shadow-xs transition-colors',
        variantCardStyles[config.variant],
        className,
      )}
    >
      <div className="flex flex-col items-start justify-between gap-3 border-b border-black/5 pb-4 sm:flex-row sm:items-center dark:border-white/5">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white shadow-2xs dark:bg-slate-900">
            {config.icon}
          </div>
          <div>
            <span
              className={cn(
                'inline-flex items-center rounded-full border px-3 py-0.5 text-xs font-semibold tracking-wide uppercase',
                variantBadgeStyles[config.variant],
              )}
            >
              {config.label}
            </span>
            <h3 className="mt-1 text-base font-semibold text-slate-900 dark:text-slate-100">
              {customTitle || config.title}
            </h3>
          </div>
        </div>
      </div>

      <p className="mt-3 text-sm leading-relaxed text-slate-600 dark:text-slate-300">
        {customDescription || config.description}
      </p>

      {metadata && (
        <div className="mt-4 border-t border-black/5 pt-4 dark:border-white/5">
          {metadata}
        </div>
      )}

      {actions && <div className="mt-5 flex items-center gap-3">{actions}</div>}
    </div>
  );
}

export function VerificationBadge({
  status,
  className,
}: {
  status: VerificationState;
  className?: string;
}) {
  const config = VERIFICATION_STATES[status];

  const variantStyles = {
    success:
      'bg-green-50 text-green-800 border-green-200 dark:bg-green-950/60 dark:text-green-300 dark:border-green-800',
    danger:
      'bg-red-50 text-red-800 border-red-200 dark:bg-red-950/60 dark:text-red-300 dark:border-red-800',
    warning:
      'bg-amber-50 text-amber-900 border-amber-200 dark:bg-amber-950/60 dark:text-amber-300 dark:border-amber-800',
    neutral:
      'bg-slate-100 text-slate-800 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700',
  };

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold tracking-wide uppercase',
        variantStyles[config.variant],
        className,
      )}
    >
      <span className="origin-center scale-75">{config.icon}</span>
      <span>{config.label}</span>
    </span>
  );
}
