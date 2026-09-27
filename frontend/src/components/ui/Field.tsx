import { Eye, EyeOff } from 'lucide-react';
import {
  forwardRef,
  useId,
  useState,
  type InputHTMLAttributes,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { cn } from '../../lib/utils/cn';
import { IconButton } from './IconButton';

interface FieldMeta {
  label: string;
  hint?: string;
  error?: string;
}

const controlStyles =
  'min-h-11 w-full rounded-lg border border-ink-300 bg-white px-3 text-ink-900 shadow-[var(--shadow-sm)] placeholder:text-ink-400 hover:border-ink-400 disabled:cursor-not-allowed disabled:bg-ink-50 disabled:text-ink-500';

function FieldLabel({
  htmlFor,
  label,
  hint,
  error,
}: FieldMeta & { htmlFor: string }) {
  return (
    <>
      <label
        htmlFor={htmlFor}
        className="text-ink-800 mb-1.5 block text-sm font-semibold"
      >
        {label}
      </label>
      {hint ? (
        <p id={`${htmlFor}-hint`} className="text-ink-600 mb-1.5 text-sm">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${htmlFor}-error`} className="text-danger-700 mt-1.5 text-sm">
          {error}
        </p>
      ) : null}
    </>
  );
}

function describedBy(id: string, hint?: string, error?: string) {
  return (
    [hint ? `${id}-hint` : null, error ? `${id}-error` : null]
      .filter(Boolean)
      .join(' ') || undefined
  );
}

export const Input = forwardRef<
  HTMLInputElement,
  InputHTMLAttributes<HTMLInputElement> & FieldMeta
>(function Input(
  { label, hint, error, id: suppliedId, className, ...props },
  ref,
) {
  const generatedId = useId();
  const id = suppliedId || generatedId;
  return (
    <div>
      <FieldLabel htmlFor={id} label={label} hint={hint} />
      <input
        ref={ref}
        id={id}
        className={cn(controlStyles, error && 'border-danger-700', className)}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={describedBy(id, hint, error)}
        {...props}
      />
      {error ? (
        <p id={`${id}-error`} className="text-danger-700 mt-1.5 text-sm">
          {error}
        </p>
      ) : null}
    </div>
  );
});

export function PasswordInput(
  props: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & FieldMeta,
) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <Input
        {...props}
        type={visible ? 'text' : 'password'}
        className="pr-12"
      />
      <IconButton
        label={visible ? 'Hide password' : 'Show password'}
        className="absolute right-0 bottom-0"
        onClick={() => setVisible((current) => !current)}
      >
        {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
      </IconButton>
    </div>
  );
}

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement> & FieldMeta
>(function Textarea(
  { label, hint, error, id: suppliedId, className, ...props },
  ref,
) {
  const generatedId = useId();
  const id = suppliedId || generatedId;
  return (
    <div>
      <FieldLabel htmlFor={id} label={label} hint={hint} />
      <textarea
        ref={ref}
        id={id}
        className={cn(
          controlStyles,
          'min-h-28 py-2.5',
          error && 'border-danger-700',
          className,
        )}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={describedBy(id, hint, error)}
        {...props}
      />
      {error ? (
        <p id={`${id}-error`} className="text-danger-700 mt-1.5 text-sm">
          {error}
        </p>
      ) : null}
    </div>
  );
});

export const Select = forwardRef<
  HTMLSelectElement,
  SelectHTMLAttributes<HTMLSelectElement> & FieldMeta
>(function Select(
  { label, hint, error, id: suppliedId, className, children, ...props },
  ref,
) {
  const generatedId = useId();
  const id = suppliedId || generatedId;
  return (
    <div>
      <FieldLabel htmlFor={id} label={label} hint={hint} />
      <select
        ref={ref}
        id={id}
        className={cn(controlStyles, error && 'border-danger-700', className)}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={describedBy(id, hint, error)}
        {...props}
      >
        {children}
      </select>
      {error ? (
        <p id={`${id}-error`} className="text-danger-700 mt-1.5 text-sm">
          {error}
        </p>
      ) : null}
    </div>
  );
});

export const Checkbox = forwardRef<
  HTMLInputElement,
  Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & { label: string }
>(function Checkbox({ label, id: suppliedId, className, ...props }, ref) {
  const generatedId = useId();
  const id = suppliedId || generatedId;
  return (
    <label
      htmlFor={id}
      className="text-ink-800 flex min-h-11 cursor-pointer items-center gap-3 text-sm"
    >
      <input
        ref={ref}
        id={id}
        type="checkbox"
        className={cn(
          'border-ink-300 accent-accent-600 size-4 rounded',
          className,
        )}
        {...props}
      />
      <span>{label}</span>
    </label>
  );
});
