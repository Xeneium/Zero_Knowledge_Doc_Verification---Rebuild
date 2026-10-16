import React from 'react';
import {
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  Info,
  Circle,
} from 'lucide-react';
import { cn } from '../../lib/utils/cn';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'success' | 'warning' | 'danger' | 'info' | 'neutral';
  icon?: React.ReactNode;
  showDefaultIcon?: boolean;
}

export const Badge: React.FC<BadgeProps> = ({
  className,
  variant = 'neutral',
  icon,
  showDefaultIcon = true,
  children,
  ...props
}) => {
  const variantStyles = {
    success:
      'bg-green-50 text-green-800 border-green-200 dark:bg-green-950/60 dark:text-green-300 dark:border-green-800',
    warning:
      'bg-amber-50 text-amber-900 border-amber-200 dark:bg-amber-950/60 dark:text-amber-300 dark:border-amber-800',
    danger:
      'bg-red-50 text-red-800 border-red-200 dark:bg-red-950/60 dark:text-red-300 dark:border-red-800',
    info: 'bg-indigo-50 text-indigo-800 border-indigo-200 dark:bg-indigo-950/60 dark:text-indigo-300 dark:border-indigo-800',
    neutral:
      'bg-slate-100 text-slate-800 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700',
  };

  const defaultIcons = {
    success: (
      <CheckCircle2 className="mr-1 h-3 w-3 shrink-0" aria-hidden="true" />
    ),
    warning: (
      <AlertTriangle className="mr-1 h-3 w-3 shrink-0" aria-hidden="true" />
    ),
    danger: (
      <AlertCircle className="mr-1 h-3 w-3 shrink-0" aria-hidden="true" />
    ),
    info: <Info className="mr-1 h-3 w-3 shrink-0" aria-hidden="true" />,
    neutral: (
      <Circle
        className="mr-1 h-2 w-2 shrink-0 fill-current"
        aria-hidden="true"
      />
    ),
  };

  const renderedIcon =
    icon !== undefined ? (
      icon ? (
        <span
          className="mr-1 inline-flex shrink-0 items-center"
          aria-hidden="true"
        >
          {icon}
        </span>
      ) : null
    ) : showDefaultIcon ? (
      defaultIcons[variant]
    ) : null;

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium',
        variantStyles[variant],
        className,
      )}
      {...props}
    >
      {renderedIcon}
      {children}
    </span>
  );
};
