import React, { useState, useCallback } from 'react';
import {
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  Info,
  X,
} from 'lucide-react';
import { cn } from '../../lib/utils/cn';
import { ToastContext, type ToastItem } from './ToastContext';

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const addToast = useCallback(
    ({
      title,
      message,
      variant = 'info',
      duration = 5000,
    }: Omit<ToastItem, 'id'>) => {
      const id = Math.random().toString(36).substring(2, 9);
      setToasts((prev) => [...prev, { id, title, message, variant, duration }]);

      if (duration > 0) {
        setTimeout(() => {
          removeToast(id);
        }, duration);
      }
      return id;
    },
    [removeToast],
  );

  return (
    <ToastContext.Provider value={{ toasts, addToast, removeToast }}>
      {children}
      <ToastContainer toasts={toasts} onDismiss={removeToast} />
    </ToastContext.Provider>
  );
}

export function ToastContainer({
  toasts,
  onDismiss,
}: {
  toasts: ToastItem[];
  onDismiss: (id: string) => void;
}) {
  if (toasts.length === 0) return null;

  return (
    <div
      aria-live="polite"
      aria-atomic="false"
      className="pointer-events-none fixed right-4 bottom-4 z-50 flex max-w-sm flex-col gap-2"
    >
      {toasts.map((toast) => (
        <ToastCard
          key={toast.id}
          toast={toast}
          onDismiss={() => onDismiss(toast.id)}
        />
      ))}
    </div>
  );
}

function ToastCard({
  toast,
  onDismiss,
}: {
  toast: ToastItem;
  onDismiss: () => void;
}) {
  const iconMap = {
    info: (
      <Info className="h-5 w-5 shrink-0 text-indigo-500" aria-hidden="true" />
    ),
    success: (
      <CheckCircle2
        className="h-5 w-5 shrink-0 text-green-500"
        aria-hidden="true"
      />
    ),
    warning: (
      <AlertTriangle
        className="h-5 w-5 shrink-0 text-amber-500"
        aria-hidden="true"
      />
    ),
    danger: (
      <AlertCircle
        className="h-5 w-5 shrink-0 text-red-500"
        aria-hidden="true"
      />
    ),
  };

  const variant = toast.variant || 'info';

  return (
    <div
      role={variant === 'danger' ? 'alert' : 'status'}
      className={cn(
        'pointer-events-auto flex w-full items-start gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-lg transition-all',
        'dark:border-slate-800 dark:bg-slate-900',
      )}
    >
      <div className="mt-0.5">{iconMap[variant]}</div>
      <div className="flex-1">
        <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
          {toast.title}
        </h4>
        {toast.message && (
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            {toast.message}
          </p>
        )}
      </div>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Close notification"
        className="-mt-2 -mr-2 inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md p-1.5 text-slate-400 hover:text-slate-600 focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:hover:text-slate-300"
      >
        <X className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}
