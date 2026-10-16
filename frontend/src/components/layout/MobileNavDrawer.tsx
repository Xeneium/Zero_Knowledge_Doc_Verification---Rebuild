import React, { useEffect, useRef } from 'react';
import { NavLink, Link } from 'react-router';
import { X, ShieldCheck, KeyRound, UserCheck, LogOut } from 'lucide-react';
import { authStore, type AuthState } from '../../state/authStore';
import { Badge } from '../common/Badge';
import { cn } from '../../lib/utils/cn';

export interface MobileNavDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  auth: AuthState;
}

export function MobileNavDrawer({
  isOpen,
  onClose,
  auth,
}: MobileNavDrawerProps) {
  const drawerRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!isOpen) return;

    previousActiveElement.current = document.activeElement as HTMLElement;
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    // Focus close button or first link
    const timer = setTimeout(() => {
      if (drawerRef.current) {
        const focusable = drawerRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input',
        );
        if (focusable.length > 0) {
          focusable[0].focus();
        }
      }
    }, 50);

    return () => {
      clearTimeout(timer);
      document.body.style.overflow = originalOverflow;
      if (previousActiveElement.current) {
        previousActiveElement.current.focus();
      }
    };
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
        return;
      }

      if (e.key === 'Tab' && drawerRef.current) {
        const focusable = drawerRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input',
        );
        if (focusable.length === 0) return;

        const firstElement = focusable[0];
        const lastElement = focusable[focusable.length - 1];

        if (e.shiftKey) {
          if (document.activeElement === firstElement) {
            e.preventDefault();
            lastElement.focus();
          }
        } else {
          if (document.activeElement === lastElement) {
            e.preventDefault();
            firstElement.focus();
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'flex min-h-[44px] items-center rounded-md px-3 py-2 text-sm font-medium transition-colors',
      isActive
        ? 'bg-indigo-50 font-semibold text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300'
        : 'text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800',
    );

  const canAccessHolder = authStore.hasAnyRole(['HOLDER', 'ADMIN']);
  const canAccessIssuer = authStore.hasAnyRole(['ISSUER', 'ADMIN']);
  const canAccessAudit = authStore.hasAnyRole(['ADMIN', 'ISSUER']);
  const canAccessAdmin = authStore.hasAnyRole(['ADMIN']);
  const hasAnyProtectedRoutes =
    canAccessHolder || canAccessIssuer || canAccessAudit || canAccessAdmin;

  return (
    <div
      id="mobile-nav-drawer"
      role="dialog"
      aria-modal="true"
      aria-label="Navigation Menu"
      className="fixed inset-0 z-50 flex"
    >
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs transition-opacity"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Drawer surface */}
      <div
        ref={drawerRef}
        className="relative ml-auto flex h-full w-full max-w-xs flex-col border-l border-slate-200 bg-white p-6 shadow-xl dark:border-slate-800 dark:bg-slate-900"
      >
        <div className="flex items-center justify-between border-b border-slate-100 pb-4 dark:border-slate-800">
          <div className="flex items-center gap-2">
            <ShieldCheck
              className="h-6 w-6 text-indigo-600 dark:text-indigo-400"
              aria-hidden="true"
            />
            <span className="font-bold text-slate-900 dark:text-slate-100">
              ZKDoc Menu
            </span>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close navigation menu"
            className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md text-slate-400 hover:text-slate-600 focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:hover:text-slate-300"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        {/* Identity & Status */}
        <div className="border-b border-slate-100 py-4 dark:border-slate-800">
          {auth.isAuthenticated && auth.user ? (
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Badge variant="info">
                  <UserCheck className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                  {auth.user.subject}
                </Badge>
              </div>
              <div className="text-xs text-slate-500 dark:text-slate-400">
                Roles: {auth.user.roles.join(', ') || 'Standard User'}
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-between">
              <Badge variant="neutral">
                <KeyRound className="mr-1 h-3 w-3" aria-hidden="true" />
                Anonymous Verifier
              </Badge>
              <Link
                to="/login"
                onClick={onClose}
                className="text-xs font-semibold text-indigo-600 hover:underline dark:text-indigo-400"
              >
                Connect
              </Link>
            </div>
          )}
        </div>

        {/* Navigation links */}
        <nav
          className="flex-1 space-y-1 overflow-y-auto py-4"
          aria-label="Mobile Navigation"
        >
          <div className="px-3 pb-1 text-xs font-semibold tracking-wider text-slate-400 uppercase dark:text-slate-500">
            Public Verifier
          </div>
          <NavLink to="/" className={linkClass} onClick={onClose} end>
            Platform Overview
          </NavLink>
          <NavLink to="/verify" className={linkClass} onClick={onClose}>
            Verify Credential
          </NavLink>
          <NavLink
            to="/predicate-verification"
            className={linkClass}
            onClick={onClose}
          >
            ZK Predicates
          </NavLink>

          {hasAnyProtectedRoutes && (
            <>
              <div className="px-3 pt-4 pb-1 text-xs font-semibold tracking-wider text-slate-400 uppercase dark:text-slate-500">
                Authenticated Workspaces
              </div>
              {canAccessHolder && (
                <NavLink
                  to="/credentials"
                  className={linkClass}
                  onClick={onClose}
                >
                  Holder Credentials
                </NavLink>
              )}
              {canAccessIssuer && (
                <NavLink to="/issuer" className={linkClass} onClick={onClose}>
                  Issuer Console
                </NavLink>
              )}
              {canAccessAudit && (
                <NavLink
                  to="/audit-logs"
                  className={linkClass}
                  onClick={onClose}
                >
                  Audit Logs
                </NavLink>
              )}
              {canAccessAdmin && (
                <NavLink
                  to="/administration"
                  className={linkClass}
                  onClick={onClose}
                >
                  Administration
                </NavLink>
              )}
            </>
          )}
        </nav>

        {auth.isAuthenticated ? (
          <div className="border-t border-slate-100 pt-4 dark:border-slate-800">
            <button
              type="button"
              onClick={() => {
                authStore.clearSession();
                onClose();
              }}
              className="flex min-h-[44px] w-full items-center justify-center gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-100 focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:outline-none dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-300"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              <span>Disconnect Session</span>
            </button>
          </div>
        ) : (
          <div className="border-t border-slate-100 pt-4 dark:border-slate-800">
            <Link
              to="/login"
              onClick={onClose}
              className="flex min-h-[44px] w-full items-center justify-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:bg-indigo-500 dark:hover:bg-indigo-600"
            >
              <KeyRound className="h-4 w-4" aria-hidden="true" />
              <span>Connect Session</span>
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
