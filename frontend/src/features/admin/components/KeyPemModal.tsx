import React, { useState } from 'react';
import { Key, Copy, Check, Fingerprint } from 'lucide-react';
import { Dialog } from '../../../components/feedback/Dialog';
import { Button } from '../../../components/common/Button';
import type { KeyMetadataResponse } from '../../../api/types';

interface KeyPemModalProps {
  keyMetadata: KeyMetadataResponse | null;
  isOpen: boolean;
  onClose: () => void;
}

export const KeyPemModal: React.FC<KeyPemModalProps> = ({
  keyMetadata,
  isOpen,
  onClose,
}) => {
  const [copied, setCopied] = useState(false);

  if (!keyMetadata) return null;

  const handleCopy = () => {
    void navigator.clipboard.writeText(keyMetadata.publicKeyPem);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={
        <div className="flex items-center gap-2">
          <Key
            className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
            aria-hidden="true"
          />
          <span>Public Key Specification: {keyMetadata.keyId}</span>
        </div>
      }
      footer={
        <div className="flex w-full items-center justify-between">
          <Button variant="outline" size="sm" onClick={handleCopy}>
            {copied ? (
              <>
                <Check
                  className="mr-1.5 h-3.5 w-3.5 text-emerald-600"
                  aria-hidden="true"
                />
                Copied to Clipboard
              </>
            ) : (
              <>
                <Copy className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
                Copy PEM
              </>
            )}
          </Button>
          <Button variant="outline" onClick={onClose}>
            Close
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Fingerprint Header */}
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/50">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-700 dark:text-slate-300">
            <Fingerprint
              className="h-4 w-4 text-indigo-600 dark:text-indigo-400"
              aria-hidden="true"
            />
            <span>Cryptographic Fingerprint (SHA-256)</span>
          </div>
          <div className="mt-1 font-mono text-xs text-slate-800 break-all select-all dark:text-slate-200">
            {keyMetadata.fingerprint}
          </div>
        </div>

        {/* Algorithm & Purpose */}
        <div className="grid grid-cols-2 gap-3 text-xs text-slate-600 dark:text-slate-400">
          <div>
            <span className="font-semibold text-slate-800 dark:text-slate-200">
              Algorithm:
            </span>{' '}
            {keyMetadata.algorithm}
          </div>
          <div>
            <span className="font-semibold text-slate-800 dark:text-slate-200">
              Purpose:
            </span>{' '}
            {keyMetadata.purpose}
          </div>
        </div>

        {/* PEM Monospace Viewer */}
        <div>
          <label className="text-xs font-medium text-slate-700 dark:text-slate-300">
            Public Key Material (PEM / X.509)
          </label>
          <pre className="mt-1 max-h-60 overflow-x-auto rounded-md border border-slate-200 bg-slate-950 p-3 font-mono text-xs text-emerald-400 select-all dark:border-slate-800">
            {keyMetadata.publicKeyPem}
          </pre>
        </div>
      </div>
    </Dialog>
  );
};
