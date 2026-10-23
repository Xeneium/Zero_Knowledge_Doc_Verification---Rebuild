import React from 'react';
import { Shield, CheckCircle2, Lock } from 'lucide-react';

export const Footer: React.FC = () => {
  return (
    <footer role="contentinfo" className="mt-auto border-t border-slate-200 bg-white py-6 transition-colors dark:border-slate-800 dark:bg-slate-900">
      <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-4 px-4 text-xs text-slate-500 sm:flex-row sm:px-6 lg:px-8 dark:text-slate-400">
        <div className="flex items-center gap-2">
          <Shield
            className="h-4 w-4 text-indigo-600 dark:text-indigo-400"
            aria-hidden="true"
          />
          <span className="font-medium text-slate-700 dark:text-slate-300">
            ZKDoc Platform
          </span>
          <span className="text-slate-300 dark:text-slate-700">|</span>
          <span>Zero-Knowledge Cryptographic Core</span>
        </div>

        <div className="flex items-center gap-2 text-center sm:text-left">
          <Lock
            className="h-3.5 w-3.5 shrink-0 text-indigo-500"
            aria-hidden="true"
          />
          <span className="max-w-md">
            Zero-Knowledge proofs are evaluated with zero private data exposure.
            All assertions verified via SHA-256 and RSA-PSS signatures.
          </span>
        </div>

        <div className="flex items-center gap-3">
          <span className="inline-flex items-center gap-1 font-medium text-green-700 dark:text-green-400">
            <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
            Active Key Registry V1
          </span>
          <span className="text-slate-300 dark:text-slate-700">|</span>
          <span>WCAG 2.2 AA</span>
        </div>
      </div>
    </footer>
  );
};
