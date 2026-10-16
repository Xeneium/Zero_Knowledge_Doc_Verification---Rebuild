import React from 'react';
import { AlertTriangle, AlertOctagon, HelpCircle } from 'lucide-react';
import { Dialog } from './Dialog';
import { Button } from '../common/Button';

export interface ConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: React.ReactNode;
  confirmText?: string;
  cancelText?: string;
  variant?: 'danger' | 'warning' | 'primary';
  isLoading?: boolean;
}

export function ConfirmationModal({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  variant = 'danger',
  isLoading = false,
}: ConfirmationModalProps) {
  const iconMap = {
    danger: (
      <AlertOctagon
        className="h-6 w-6 text-red-600 dark:text-red-400"
        aria-hidden="true"
      />
    ),
    warning: (
      <AlertTriangle
        className="h-6 w-6 text-amber-600 dark:text-amber-400"
        aria-hidden="true"
      />
    ),
    primary: (
      <HelpCircle
        className="h-6 w-6 text-indigo-600 dark:text-indigo-400"
        aria-hidden="true"
      />
    ),
  };

  const buttonVariantMap = {
    danger: 'destructive' as const,
    warning: 'primary' as const,
    primary: 'primary' as const,
  };

  const handleConfirm = () => {
    onConfirm();
  };

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-100 dark:bg-slate-800">
            {iconMap[variant]}
          </div>
          <span>{title}</span>
        </div>
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isLoading}>
            {cancelText}
          </Button>
          <Button
            variant={buttonVariantMap[variant]}
            onClick={handleConfirm}
            isLoading={isLoading}
          >
            {confirmText}
          </Button>
        </>
      }
    >
      <div className="text-sm text-slate-600 dark:text-slate-300">
        {message}
      </div>
    </Dialog>
  );
}
