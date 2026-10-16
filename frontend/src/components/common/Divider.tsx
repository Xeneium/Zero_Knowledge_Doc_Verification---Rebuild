import React from 'react';
import { cn } from '../../lib/utils/cn';

export interface DividerProps extends React.HTMLAttributes<HTMLDivElement> {
  orientation?: 'horizontal' | 'vertical';
  label?: string;
}

export function Divider({
  orientation = 'horizontal',
  label,
  className,
  ...props
}: DividerProps) {
  if (orientation === 'vertical') {
    return (
      <div
        role="separator"
        aria-orientation="vertical"
        className={cn(
          'inline-block h-full w-px bg-slate-200 dark:bg-slate-800',
          className,
        )}
        {...props}
      />
    );
  }

  if (label) {
    return (
      <div
        role="separator"
        aria-orientation="horizontal"
        className={cn(
          'relative my-4 flex items-center justify-center',
          className,
        )}
        {...props}
      >
        <div className="absolute inset-0 flex items-center" aria-hidden="true">
          <div className="w-full border-t border-slate-200 dark:border-slate-800" />
        </div>
        <div className="relative bg-white px-3 text-xs font-medium tracking-wider text-slate-500 uppercase dark:bg-slate-900 dark:text-slate-400">
          {label}
        </div>
      </div>
    );
  }

  return (
    <hr
      className={cn(
        'my-4 border-t border-slate-200 dark:border-slate-800',
        className,
      )}
      {...props}
    />
  );
}
