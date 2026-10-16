import React, { useId } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '../../lib/utils/cn';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  helperText?: string;
  error?: string;
  options: SelectOption[];
  placeholder?: string;
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  (
    {
      id: providedId,
      label,
      helperText,
      error,
      options,
      placeholder,
      className,
      required,
      disabled,
      children,
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
        <div className="relative">
          <select
            ref={ref}
            id={id}
            disabled={disabled}
            required={required}
            aria-invalid={error ? 'true' : undefined}
            aria-describedby={describedBy}
            aria-required={required ? 'true' : undefined}
            className={cn(
              'min-h-[44px] w-full appearance-none rounded-md border bg-white px-3.5 py-2 pr-10 text-sm text-slate-900 transition-colors',
              'dark:bg-slate-900 dark:text-slate-100',
              'focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:focus-visible:ring-indigo-400',
              error
                ? 'border-red-500 focus-visible:border-red-500 focus-visible:ring-red-500 dark:border-red-500'
                : 'border-slate-300 hover:border-slate-400 focus-visible:border-indigo-600 dark:border-slate-700 dark:hover:border-slate-600',
              disabled &&
                'cursor-not-allowed bg-slate-100 opacity-60 dark:bg-slate-800',
              className,
            )}
            {...props}
          >
            {placeholder && (
              <option value="" disabled>
                {placeholder}
              </option>
            )}
            {options.map((opt) => (
              <option key={opt.value} value={opt.value} disabled={opt.disabled}>
                {opt.label}
              </option>
            ))}
            {children}
          </select>
          <div
            className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3 text-slate-400 dark:text-slate-500"
            aria-hidden="true"
          >
            <ChevronDown className="h-4 w-4" />
          </div>
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

Select.displayName = 'Select';
