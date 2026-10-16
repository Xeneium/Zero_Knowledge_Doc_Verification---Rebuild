import React from 'react';
import { Link } from 'react-router';
import { ShieldAlert, Home, LogOut } from 'lucide-react';
import { Button } from '../components/common/Button';
import { authStore } from '../state/authStore';

export const ForbiddenPage: React.FC = () => {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-amber-50 text-amber-600 dark:bg-amber-950/50 dark:text-amber-400">
        <ShieldAlert className="h-8 w-8" aria-hidden="true" />
      </div>

      <span className="text-xs font-semibold tracking-wider text-amber-700 uppercase dark:text-amber-400">
        403 Forbidden
      </span>

      <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl dark:text-slate-100">
        Access Restricted
      </h1>

      <p className="mt-4 max-w-md text-sm leading-relaxed text-slate-600 dark:text-slate-400">
        You do not possess the required cryptographic role permissions to access
        this workspace. Contact your platform administrator or authenticate with
        the appropriate role credential.
      </p>

      <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
        <Link to="/">
          <Button variant="primary" className="flex items-center gap-2">
            <Home className="h-4 w-4" aria-hidden="true" />
            <span>Return to Overview</span>
          </Button>
        </Link>

        <Button
          variant="outline"
          onClick={() => authStore.clearSession()}
          className="flex items-center gap-2 text-slate-700 dark:text-slate-300"
        >
          <LogOut className="h-4 w-4" aria-hidden="true" />
          <span>Disconnect Session</span>
        </Button>
      </div>
    </div>
  );
};
