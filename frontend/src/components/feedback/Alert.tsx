import React from 'react';
import {
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  Info,
  X,
} from 'lucide-react';
import { cn } from '../../lib/utils/cn';

export interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'info' | 'success' | 'warning' | 'danger';
  title?: string;
  onDismiss?: () => void;
  icon?: React.ReactNode;
}

export const Alert: React.FC<AlertProps> = ({
  className,
  variant = 'info',
  title,
  onDismiss,
  icon,
  children,
  ...props
}) => {
  const variantStyles = {
    info: 'bg-indigo-50 border-indigo-200 text-indigo-900 dark:bg-indigo-950/60 dark:border-indigo-800 dark:text-indigo-200',
    success:
      'bg-green-50 border-green-200 text-green-900 dark:bg-green-950/60 dark:border-green-800 dark:text-green-200',
    warning:
      'bg-amber-50 border-amber-200 text-amber-900 dark:bg-amber-950/60 dark:border-amber-800 dark:text-amber-200',
    danger:
      'bg-red-50 border-red-200 text-red-900 dark:bg-red-950/60 dark:border-red-800 dark:text-red-200',
  };

  const defaultIcons = {
    info: (
      <Info
        className="h-5 w-5 shrink-0 text-indigo-600 dark:text-indigo-400"
        aria-hidden="true"
      />
    ),
    success: (
      <CheckCircle2
        className="h-5 w-5 shrink-0 text-green-600 dark:text-green-400"
        aria-hidden="true"
      />
    ),
    warning: (
      <AlertTriangle
        className="h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400"
        aria-hidden="true"
      />
    ),
    danger: (
      <AlertCircle
        className="h-5 w-5 shrink-0 text-red-600 dark:text-red-400"
        aria-hidden="true"
      />
    ),
  };

  const role = variant === 'danger' ? 'alert' : 'status';

  return (
    <div
      role={role}
      className={cn(
        'relative flex items-start gap-3 rounded-lg border p-4 text-sm transition-colors',
        variantStyles[variant],
        className,
      )}
      {...props}
    >
      <div className="mt-0.5">{icon || defaultIcons[variant]}</div>
      <div className="flex-1">
        {title && <h4 className="mb-1 leading-tight font-semibold">{title}</h4>}
        <div className="text-sm leading-relaxed">{children}</div>
      </div>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss alert"
          className={cn(
            '-mt-2 -mr-2 inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md p-1.5 transition-colors',
            'hover:bg-black/5 dark:hover:bg-white/10',
            'focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none',
          )}
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      )}
    </div>
  );
};
