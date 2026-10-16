import React, { useEffect, useState } from 'react';
import { NavLink, Link } from 'react-router';
import {
  ShieldCheck,
  UserCheck,
  KeyRound,
  Menu,
  LogOut,
  Lock,
} from 'lucide-react';
import { authStore, type AuthState } from '../../state/authStore';
import { Badge } from '../common/Badge';
import { MobileNavDrawer } from './MobileNavDrawer';
import { cn } from '../../lib/utils/cn';

export const Header: React.FC = () => {
  const [auth, setAuth] = useState<AuthState>(authStore.getState());
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  useEffect(() => {
    return authStore.subscribe(setAuth);
  }, []);

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'inline-flex min-h-[44px] items-center px-3 py-2 text-sm font-medium transition-colors rounded-md',
      'focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none',
      isActive
        ? 'bg-indigo-50 text-indigo-700 font-semibold border-b-2 border-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-300 dark:border-indigo-400'
        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50 dark:text-slate-300 dark:hover:text-slate-100 dark:hover:bg-slate-800',
    );

  const canAccessHolder = authStore.hasAnyRole(['HOLDER', 'ADMIN']);
  const canAccessIssuer = authStore.hasAnyRole(['ISSUER', 'ADMIN']);
  const canAccessAudit = authStore.hasAnyRole(['ADMIN', 'ISSUER']);
  const canAccessAdmin = authStore.hasAnyRole(['ADMIN']);

  return (
    <>
      {/* Authenticated Workspace Banner (Clear Visual Distinction) */}
      {auth.isAuthenticated && auth.user && (
        <div
          role="status"
          className="border-b border-indigo-200 bg-indigo-900 px-4 py-1.5 text-center text-xs font-medium text-indigo-100 shadow-inner dark:border-indigo-950 dark:bg-indigo-950/90"
        >
          <div className="mx-auto flex max-w-7xl items-center justify-between">
            <div className="flex items-center gap-2">
              <Lock
                className="h-3.5 w-3.5 text-indigo-300"
                aria-hidden="true"
              />
              <span>
                Authenticated Session:{' '}
                <strong className="text-white">{auth.user.subject}</strong>
              </span>
              <span className="hidden text-indigo-300 sm:inline">
                ({auth.user.roles.join(', ') || 'Standard'})
              </span>
            </div>
            <div className="text-[11px] text-indigo-200">
              Cryptographic Bearer Active
            </div>
          </div>
        </div>
      )}

      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur-xs transition-colors dark:border-slate-800 dark:bg-slate-900/95">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="flex h-16 items-center justify-between">
            {/* Brand Identity */}
            <div className="flex items-center gap-3">
              <Link
                to="/"
                className="flex items-center gap-2 rounded-md p-1 text-indigo-600 focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:text-indigo-400"
                aria-label="Zero-Knowledge Document Verification Home"
              >
                <ShieldCheck
                  className="h-7 w-7 text-indigo-600 dark:text-indigo-400"
                  aria-hidden="true"
                />
                <span className="text-lg font-bold tracking-tight text-slate-900 dark:text-slate-100">
                  ZKDoc{' '}
                  <span className="text-indigo-600 dark:text-indigo-400">
                    Verifier
                  </span>
                </span>
              </Link>
            </div>

            {/* Desktop Navigation Links */}
            <nav
              className="hidden items-center gap-1 lg:flex"
              aria-label="Main Navigation"
            >
              {/* Public Routes */}
              <NavLink to="/" className={navLinkClass} end>
                Overview
              </NavLink>
              <NavLink to="/verify" className={navLinkClass}>
                Verify
              </NavLink>
              <NavLink to="/predicate-verification" className={navLinkClass}>
                ZK Predicates
              </NavLink>

              {/* Role-Gated Navigation Links */}
              {canAccessHolder && (
                <NavLink to="/credentials" className={navLinkClass}>
                  Credentials
                </NavLink>
              )}
              {canAccessIssuer && (
                <NavLink to="/issuer" className={navLinkClass}>
                  Issuer
                </NavLink>
              )}
              {canAccessAudit && (
                <NavLink to="/audit-logs" className={navLinkClass}>
                  Audit Logs
                </NavLink>
              )}
              {canAccessAdmin && (
                <NavLink to="/administration" className={navLinkClass}>
                  Admin
                </NavLink>
              )}
            </nav>

            {/* Desktop User / Session Area */}
            <div className="hidden items-center gap-3 sm:flex">
              {auth.isAuthenticated && auth.user ? (
                <div className="flex items-center gap-2">
                  <Badge variant="info">
                    <UserCheck
                      className="mr-1 h-3.5 w-3.5"
                      aria-hidden="true"
                    />
                    {auth.user.subject}
                  </Badge>
                  <button
                    type="button"
                    onClick={() => authStore.clearSession()}
                    aria-label="Disconnect active session"
                    className="inline-flex min-h-[36px] items-center gap-1 rounded-md px-2.5 py-1 text-xs font-medium text-slate-600 hover:bg-slate-100 hover:text-red-600 focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-red-400"
                  >
                    <LogOut className="h-3.5 w-3.5" aria-hidden="true" />
                    <span>Disconnect</span>
                  </button>
                </div>
              ) : (
                <div className="flex items-center gap-2">
                  <Link
                    to="/login"
                    className="inline-flex min-h-[36px] items-center gap-1.5 rounded-md bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white shadow-xs hover:bg-indigo-700 focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:bg-indigo-500 dark:hover:bg-indigo-600"
                    aria-label="Connect session"
                  >
                    <KeyRound className="h-3.5 w-3.5" aria-hidden="true" />
                    <span>Connect Session</span>
                  </Link>
                </div>
              )}
            </div>

            {/* Mobile Hamburger Toggle Button */}
            <div className="flex items-center gap-2 lg:hidden">
              <button
                type="button"
                onClick={() => setIsMobileMenuOpen(true)}
                aria-expanded={isMobileMenuOpen}
                aria-controls="mobile-nav-drawer"
                aria-label="Open navigation menu"
                className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md p-2 text-slate-700 hover:bg-slate-100 focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:text-slate-200 dark:hover:bg-slate-800"
              >
                <Menu className="h-6 w-6" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Responsive Mobile Drawer */}
      <MobileNavDrawer
        isOpen={isMobileMenuOpen}
        onClose={() => setIsMobileMenuOpen(false)}
        auth={auth}
      />
    </>
  );
};
