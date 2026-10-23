import React, { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router';
import { authStore, type AuthState, normalizeRole } from '../../state/authStore';
import { Skeleton } from '../feedback/Skeleton';

export interface RequireRoleProps {
  allowedRoles: readonly string[];
  children?: React.ReactNode;
}

/**
 * Role-gating guard for privileged routes.
 * Verifies that the authenticated user possesses at least one of the specified roles
 * (or holds the ADMIN role). Redirects unauthorized users to /forbidden.
 */
export const RequireRole: React.FC<RequireRoleProps> = ({
  allowedRoles,
  children,
}) => {
  const [auth, setAuth] = useState<AuthState>(authStore.getState());

  useEffect(() => {
    return authStore.subscribe(setAuth);
  }, []);

  if (auth.status === 'initializing') {
    return (
      <div
        role="status"
        aria-busy="true"
        aria-label="Resolving authorization permissions"
        className="mx-auto max-w-4xl space-y-4 p-6"
      >
        <Skeleton variant="text" className="h-10 w-2/5" />
        <Skeleton variant="rectangular" className="h-48 w-full" />
      </div>
    );
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const userRoles = auth.user?.roles ?? [];
  const normalizedTargets = allowedRoles.map(normalizeRole);
  const isAuthorized =
    userRoles.includes('ADMIN') ||
    userRoles.some((r) => normalizedTargets.includes(r));
  if (!isAuthorized) {
    return <Navigate to="/forbidden" replace />;
  }

  return children ? <>{children}</> : <Outlet />;
};
