import { X } from 'lucide-react';
import { useEffect, useId, useRef, type ReactNode } from 'react';
import { IconButton } from './IconButton';

export interface DialogProps {
  open: boolean;
  title: string;
  description?: string;
  children?: ReactNode;
  footer?: ReactNode;
  onClose: () => void;
}

export function Dialog({
  open,
  title,
  description,
  children,
  footer,
  onClose,
}: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onClose={onClose}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="border-ink-200 text-ink-900 backdrop:bg-ink-950/60 m-auto w-[calc(100%-2rem)] max-w-lg rounded-xl border bg-white p-0 shadow-[var(--shadow-lg)]"
    >
      <div className="border-ink-200 flex items-start justify-between gap-4 border-b p-5">
        <div>
          <h2 id={titleId} className="text-lg font-semibold">
            {title}
          </h2>
          {description ? (
            <p id={descriptionId} className="text-ink-600 mt-1 text-sm">
              {description}
            </p>
          ) : null}
        </div>
        <IconButton
          label="Close dialog"
          onClick={onClose}
          className="-mt-2 -mr-2 shrink-0"
        >
          <X className="size-5" aria-hidden="true" />
        </IconButton>
      </div>
      {children ? <div className="p-5">{children}</div> : null}
      {footer ? (
        <div className="border-ink-200 flex justify-end gap-3 border-t p-5">
          {footer}
        </div>
      ) : null}
    </dialog>
  );
}
