import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../lib/utils/cn';

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  label: string;
  children: ReactNode;
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  function IconButton({ label, className, children, ...props }, ref) {
    return (
      <button
        ref={ref}
        type="button"
        aria-label={label}
        title={label}
        className={cn(
          'text-ink-600 hover:bg-ink-100 hover:text-ink-950 inline-flex size-11 cursor-pointer items-center justify-center rounded-lg border border-transparent transition-colors disabled:cursor-not-allowed disabled:opacity-50',
          className,
        )}
        {...props}
      >
        {children}
      </button>
    );
  },
);
