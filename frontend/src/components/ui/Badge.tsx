import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/utils/cn';

export type BadgeTone =
  'neutral' | 'accent' | 'info' | 'success' | 'warning' | 'danger';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
}

const toneStyles: Record<BadgeTone, string> = {
  neutral: 'bg-ink-100 text-ink-700',
  accent: 'bg-accent-100 text-accent-700',
  info: 'bg-info-100 text-info-700',
  success: 'bg-success-100 text-success-700',
  warning: 'bg-warning-100 text-warning-700',
  danger: 'bg-danger-100 text-danger-700',
};

export function Badge({
  tone = 'neutral',
  className,
  children,
  ...props
}: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex min-h-6 items-center rounded-full px-2.5 py-0.5 text-xs font-semibold',
        toneStyles[tone],
        className,
      )}
      {...props}
    >
      {children}
    </span>
  );
}
