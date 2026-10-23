import React from 'react';
import { Link } from 'react-router';
import { ShieldAlert, Home, LogOut, KeyRound } from 'lucide-react';
import { Button } from '../components/common/Button';
import { authStore, useAuth } from '../state/authStore';

export const ForbiddenPage: React.FC = () => {
  const auth = useAuth();

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

      {auth.isAuthenticated && auth.user && (
        <div className="mt-4 rounded-md border border-slate-200 bg-slate-50 px-4 py-2 text-xs text-slate-600 dark:border-slate-800 dark:bg-slate-900/60 dark:text-slate-400">
          <span>
            Connected as{' '}
            <strong className="text-slate-900 dark:text-slate-100">
              {auth.user.subject}
            </strong>{' '}
            with roles:{' '}
            <span className="font-semibold text-indigo-600 dark:text-indigo-400">
              {auth.user.roles.join(', ') || 'Standard User'}
            </span>
          </span>
        </div>
      )}

      <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
        <Link to="/">
          <Button variant="primary" className="flex items-center gap-2">
            <Home className="h-4 w-4" aria-hidden="true" />
            <span>Return to Overview</span>
          </Button>
        </Link>

        <Link to="/login">
          <Button variant="outline" className="flex items-center gap-2">
            <KeyRound className="h-4 w-4" aria-hidden="true" />
            <span>Connect Different Session</span>
          </Button>
        </Link>

        {auth.isAuthenticated && (
          <Button
            variant="outline"
            onClick={() => authStore.clearSession()}
            className="flex items-center gap-2 text-red-600 hover:text-red-700 dark:text-red-400"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            <span>Disconnect Session</span>
          </Button>
        )}
      </div>
    </div>
  );
};
