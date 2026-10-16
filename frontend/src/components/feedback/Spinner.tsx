import React from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '../../lib/utils/cn';

export interface SpinnerProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: 'sm' | 'md' | 'lg' | 'xl';
  label?: string;
  inline?: boolean;
}

export function Spinner({
  size = 'md',
  label = 'Loading...',
  inline = false,
  className,
  ...props
}: SpinnerProps) {
  const sizeStyles = {
    sm: 'h-4 w-4',
    md: 'h-6 w-6',
    lg: 'h-8 w-8',
    xl: 'h-12 w-12',
  };

  const containerClasses = inline
    ? 'inline-flex items-center gap-2'
    : 'flex flex-col items-center justify-center p-4 gap-2';

  return (
    <div role="status" className={cn(containerClasses, className)} {...props}>
      <Loader2
        className={cn(
          'animate-spin text-indigo-600 dark:text-indigo-400',
          sizeStyles[size],
        )}
        aria-hidden="true"
      />
      <span
        className={
          inline ? 'text-xs text-slate-500 dark:text-slate-400' : 'sr-only'
        }
      >
        {label}
      </span>
    </div>
  );
}
