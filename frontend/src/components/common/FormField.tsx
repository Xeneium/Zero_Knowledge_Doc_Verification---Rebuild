import React, { useId } from 'react';
import { cn } from '../../lib/utils/cn';

export interface FormFieldProps {
  id?: string;
  label?: string;
  helperText?: string;
  error?: string;
  required?: boolean;
  optional?: boolean;
  className?: string;
  children: (props: {
    id: string;
    'aria-invalid'?: 'true';
    'aria-describedby'?: string;
    'aria-required'?: 'true';
    required?: boolean;
  }) => React.ReactNode;
}

export function FormField({
  id: providedId,
  label,
  helperText,
  error,
  required,
  optional,
  className,
  children,
}: FormFieldProps) {
  const generatedId = useId();
  const id = providedId || generatedId;
  const helperId = helperText ? `${id}-helper` : undefined;
  const errorId = error ? `${id}-error` : undefined;

  const describedBy =
    [errorId, helperId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={cn('w-full', className)}>
      {label && (
        <div className="mb-1.5 flex items-center justify-between">
          <label
            htmlFor={id}
            className="block text-sm font-medium text-slate-900 dark:text-slate-100"
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
          {optional && !required && (
            <span className="text-xs text-slate-500 dark:text-slate-400">
              Optional
            </span>
          )}
        </div>
      )}
      {children({
        id,
        'aria-invalid': error ? 'true' : undefined,
        'aria-describedby': describedBy,
        'aria-required': required ? 'true' : undefined,
        required,
      })}
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
}
