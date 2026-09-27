import { LoaderCircle } from 'lucide-react';
import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '../../lib/utils/cn';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}

const variantStyles: Record<ButtonVariant, string> = {
  primary:
    'border-accent-600 bg-accent-600 text-white shadow-sm hover:border-accent-700 hover:bg-accent-700',
  secondary:
    'border-ink-200 bg-white text-ink-800 shadow-sm hover:border-ink-300 hover:bg-ink-50',
  ghost:
    'border-transparent bg-transparent text-ink-700 hover:bg-ink-100 hover:text-ink-950',
  danger:
    'border-danger-700 bg-danger-700 text-white shadow-sm hover:brightness-90',
};

const sizeStyles: Record<ButtonSize, string> = {
  sm: 'min-h-9 px-3 text-sm',
  md: 'min-h-11 px-4 text-sm',
  lg: 'min-h-12 px-5 text-base',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  function Button(
    {
      className,
      variant = 'primary',
      size = 'md',
      loading = false,
      disabled,
      children,
      ...props
    },
    ref,
  ) {
    return (
      <button
        ref={ref}
        className={cn(
          'inline-flex cursor-pointer items-center justify-center gap-2 rounded-lg border font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-55',
          variantStyles[variant],
          sizeStyles[size],
          className,
        )}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        {...props}
      >
        {loading ? (
          <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
        ) : null}
        {children}
      </button>
    );
  },
);
