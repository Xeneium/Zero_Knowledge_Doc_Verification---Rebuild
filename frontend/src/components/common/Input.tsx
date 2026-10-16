import React, { useId } from 'react';
import { cn } from '../../lib/utils/cn';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  helperText?: string;
  error?: string;
  prefixIcon?: React.ReactNode;
  suffixIcon?: React.ReactNode;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  (
    {
      id: providedId,
      label,
      helperText,
      error,
      prefixIcon,
      suffixIcon,
      className,
      required,
      disabled,
      ...props
    },
    ref,
  ) => {
    const generatedId = useId();
    const id = providedId || generatedId;
    const helperId = helperText ? `${id}-helper` : undefined;
    const errorId = error ? `${id}-error` : undefined;

    const describedBy =
      [errorId, helperId].filter(Boolean).join(' ') || undefined;

    return (
      <div className="w-full">
        {label && (
          <label
            htmlFor={id}
            className="mb-1.5 block text-sm font-medium text-slate-900 dark:text-slate-100"
          >
            {label}
            {required && (
              <span
                className="ml-1 text-red-600 dark:text-red-400"
                aria-hidden="true"
              >
                *
              </span>
            )}
          </label>
        )}
        <div className="relative flex items-center">
          {prefixIcon && (
            <div
              className="pointer-events-none absolute left-3 flex items-center text-slate-500 dark:text-slate-400"
              aria-hidden="true"
            >
              {prefixIcon}
            </div>
          )}
          <input
            ref={ref}
            id={id}
            disabled={disabled}
            required={required}
            aria-invalid={error ? 'true' : undefined}
            aria-describedby={describedBy}
            aria-required={required ? 'true' : undefined}
            className={cn(
              'min-h-[44px] w-full rounded-md border bg-white px-3.5 py-2 text-sm text-slate-900 transition-colors',
              'placeholder:text-slate-400 dark:placeholder:text-slate-500',
              'dark:bg-slate-900 dark:text-slate-100',
              'focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:focus-visible:ring-indigo-400',
              error
                ? 'border-red-500 focus-visible:border-red-500 focus-visible:ring-red-500 dark:border-red-500'
                : 'border-slate-300 hover:border-slate-400 focus-visible:border-indigo-600 dark:border-slate-700 dark:hover:border-slate-600',
              disabled &&
                'cursor-not-allowed bg-slate-100 opacity-60 dark:bg-slate-800',
              prefixIcon ? 'pl-10' : undefined,
              suffixIcon ? 'pr-10' : undefined,
              className,
            )}
            {...props}
          />
          {suffixIcon && (
            <div
              className="absolute right-3 flex items-center text-slate-500 dark:text-slate-400"
              aria-hidden="true"
            >
              {suffixIcon}
            </div>
          )}
        </div>
        {error && (
          <p
            id={errorId}
            role="alert"
            className="mt-1 text-xs font-medium text-red-600 dark:text-red-400"
          >
            {error}
          </p>
        )}
        {!error && helperText && (
          <p
            id={helperId}
            className="mt-1 text-xs text-slate-500 dark:text-slate-400"
          >
            {helperText}
          </p>
        )}
      </div>
    );
  },
);

Input.displayName = 'Input';
