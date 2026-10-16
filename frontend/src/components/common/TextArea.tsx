import React, { useId } from 'react';
import { cn } from '../../lib/utils/cn';

export interface TextAreaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  helperText?: string;
  error?: string;
  showCharCount?: boolean;
}

export const TextArea = React.forwardRef<HTMLTextAreaElement, TextAreaProps>(
  (
    {
      id: providedId,
      label,
      helperText,
      error,
      showCharCount = false,
      className,
      required,
      disabled,
      maxLength,
      value,
      defaultValue,
      onChange,
      ...props
    },
    ref,
  ) => {
    const generatedId = useId();
    const id = providedId || generatedId;
    const helperId = helperText ? `${id}-helper` : undefined;
    const errorId = error ? `${id}-error` : undefined;

    const [charCount, setCharCount] = React.useState<number>(() => {
      if (typeof value === 'string') return value.length;
      if (typeof defaultValue === 'string') return defaultValue.length;
      return 0;
    });

    const describedBy =
      [errorId, helperId].filter(Boolean).join(' ') || undefined;

    const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      setCharCount(e.target.value.length);
      if (onChange) {
        onChange(e);
      }
    };

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
        <textarea
          ref={ref}
          id={id}
          disabled={disabled}
          required={required}
          maxLength={maxLength}
          value={value}
          defaultValue={defaultValue}
          onChange={handleChange}
          aria-invalid={error ? 'true' : undefined}
          aria-describedby={describedBy}
          aria-required={required ? 'true' : undefined}
          rows={props.rows || 4}
          className={cn(
            'w-full rounded-md border bg-white px-3.5 py-2.5 text-sm text-slate-900 transition-colors',
            'placeholder:text-slate-400 dark:placeholder:text-slate-500',
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
        />
        <div className="mt-1 flex items-center justify-between text-xs">
          {error ? (
            <p
              id={errorId}
              role="alert"
              className="font-medium text-red-600 dark:text-red-400"
            >
              {error}
            </p>
          ) : helperText ? (
            <p id={helperId} className="text-slate-500 dark:text-slate-400">
              {helperText}
            </p>
          ) : (
            <span />
          )}
          {showCharCount && maxLength && (
            <span
              className="text-slate-400 dark:text-slate-500"
              aria-label={`Character count: ${charCount} of ${maxLength}`}
            >
              {charCount} / {maxLength}
            </span>
          )}
        </div>
      </div>
    );
  },
);

TextArea.displayName = 'TextArea';
