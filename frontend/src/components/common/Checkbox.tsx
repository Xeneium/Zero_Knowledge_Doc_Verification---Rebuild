import React, { useId } from 'react';
import { Check } from 'lucide-react';
import { cn } from '../../lib/utils/cn';

export interface CheckboxProps extends Omit<
  React.InputHTMLAttributes<HTMLInputElement>,
  'type'
> {
  label: React.ReactNode;
  helperText?: string;
  error?: string;
}

export const Checkbox = React.forwardRef<HTMLInputElement, CheckboxProps>(
  (
    {
      id: providedId,
      label,
      helperText,
      error,
      className,
      disabled,
      checked,
      defaultChecked,
      onChange,
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
      <div className="flex flex-col">
        <label
          htmlFor={id}
          className={cn(
            'inline-flex min-h-[44px] cursor-pointer items-center gap-3 select-none',
            disabled && 'cursor-not-allowed opacity-60',
            className,
          )}
        >
          <div className="relative flex items-center justify-center">
            <input
              ref={ref}
              type="checkbox"
              id={id}
              disabled={disabled}
              checked={checked}
              defaultChecked={defaultChecked}
              onChange={onChange}
              aria-invalid={error ? 'true' : undefined}
              aria-describedby={describedBy}
              className={cn(
                'peer h-5 w-5 appearance-none rounded border bg-white transition-colors',
                'dark:bg-slate-900',
                'focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:ring-offset-2 focus-visible:outline-none dark:focus-visible:ring-indigo-400',
                error
                  ? 'border-red-500'
                  : 'border-slate-300 hover:border-slate-400 dark:border-slate-700 dark:hover:border-slate-600',
                'checked:border-indigo-600 checked:bg-indigo-600 dark:checked:border-indigo-500 dark:checked:bg-indigo-500',
              )}
              {...props}
            />
            <Check
              className="pointer-events-none absolute h-3.5 w-3.5 text-white opacity-0 transition-opacity peer-checked:opacity-100"
              aria-hidden="true"
            />
          </div>
          <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
            {label}
          </span>
        </label>
        {error && (
          <p
            id={errorId}
            role="alert"
            className="-mt-1 ml-8 text-xs font-medium text-red-600 dark:text-red-400"
          >
            {error}
          </p>
        )}
        {!error && helperText && (
          <p
            id={helperId}
            className="-mt-1 ml-8 text-xs text-slate-500 dark:text-slate-400"
          >
            {helperText}
          </p>
        )}
      </div>
    );
  },
);

Checkbox.displayName = 'Checkbox';
