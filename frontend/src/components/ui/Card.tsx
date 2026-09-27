import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/utils/cn';

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'border-ink-200 rounded-xl border bg-white shadow-[var(--shadow-sm)]',
        className,
      )}
      {...props}
    />
  );
}
