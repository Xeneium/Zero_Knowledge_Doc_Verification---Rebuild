import React, { useId } from 'react';
import { cn } from '../../lib/utils/cn';

export interface RadioOption {
  value: string;
  label: React.ReactNode;
  helperText?: string;
  disabled?: boolean;
}

export interface RadioGroupProps {
  name: string;
  label?: string;
  helperText?: string;
  error?: string;
  options: RadioOption[];
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  className?: string;
  disabled?: boolean;
  required?: boolean;
}

export function RadioGroup({
  name,
  label,
  helperText,
  error,
  options,
  value,
  defaultValue,
  onChange,
  className,
  disabled = false,
  required = false,
}: RadioGroupProps) {
  const groupId = useId();
  const errorId = error ? `${groupId}-error` : undefined;
  const helperId = helperText ? `${groupId}-helper` : undefined;

  const describedBy =
    [errorId, helperId].filter(Boolean).join(' ') || undefined;

  return (
    <fieldset
      className={cn('m-0 w-full border-none p-0', className)}
      aria-describedby={describedBy}
      aria-invalid={error ? 'true' : undefined}
      aria-required={required ? 'true' : undefined}
    >
      {label && (
        <legend className="mb-2 block text-sm font-medium text-slate-900 dark:text-slate-100">
          {label}
          {required && (
            <span
              className="ml-1 text-red-600 dark:text-red-400"
              aria-hidden="true"
            >
              *
            </span>
          )}
        </legend>
      )}
      <div className="space-y-2">
        {options.map((option) => {
          const optionId = `${groupId}-${option.value}`;
          const isOptionDisabled = disabled || option.disabled;
          return (
            <label
              key={option.value}
              htmlFor={optionId}
              className={cn(
                'flex min-h-[44px] cursor-pointer items-start gap-3 rounded-md p-2 transition-colors select-none',
                'hover:bg-slate-50 dark:hover:bg-slate-800/60',
                isOptionDisabled &&
                  'cursor-not-allowed opacity-60 hover:bg-transparent',
              )}
            >
              <div className="relative mt-0.5 flex items-center justify-center">
                <input
                  type="radio"
                  id={optionId}
                  name={name}
                  value={option.value}
                  checked={
                    value !== undefined ? value === option.value : undefined
                  }
                  defaultChecked={
                    defaultValue !== undefined
                      ? defaultValue === option.value
                      : undefined
                  }
                  disabled={isOptionDisabled}
                  onChange={(e) => {
                    if (onChange && e.target.checked) {
                      onChange(option.value);
                    }
                  }}
                  className={cn(
                    'h-4 w-4 appearance-none rounded-full border bg-white transition-colors',
                    'dark:bg-slate-900',
                    'focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:ring-offset-2 focus-visible:outline-none dark:focus-visible:ring-indigo-400',
                    error
                      ? 'border-red-500'
                      : 'border-slate-300 dark:border-slate-700',
                    'checked:border-indigo-600 checked:bg-indigo-600 dark:checked:border-indigo-500 dark:checked:bg-indigo-500',
                  )}
                />
              </div>
              <div className="flex flex-col">
                <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  {option.label}
                </span>
                {option.helperText && (
                  <span className="text-xs text-slate-500 dark:text-slate-400">
                    {option.helperText}
                  </span>
                )}
              </div>
            </label>
          );
        })}
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
    </fieldset>
  );
}
