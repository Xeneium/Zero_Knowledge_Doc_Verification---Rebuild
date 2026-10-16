import React, { useEffect, useState } from 'react';
import { Navigate, useLocation, Outlet } from 'react-router';
import { authStore, type AuthState } from '../../state/authStore';
import { Skeleton } from '../feedback/Skeleton';

export interface RequireAuthProps {
  children?: React.ReactNode;
}

/**
 * Route protection guard for authenticated routes.
 * Redirects unauthenticated users to /login with a safe return path parameter.
 */
export const RequireAuth: React.FC<RequireAuthProps> = ({ children }) => {
  const [auth, setAuth] = useState<AuthState>(authStore.getState());
  const location = useLocation();

  useEffect(() => {
    return authStore.subscribe(setAuth);
  }, []);

  // During initial session restoration, render an accessible skeleton instead of flashing content
  if (auth.status === 'initializing') {
    return (
      <div
        role="status"
        aria-busy="true"
        aria-label="Resolving authentication status"
        className="mx-auto max-w-4xl space-y-4 p-6"
      >
        <Skeleton variant="text" className="h-10 w-2/5" />
        <Skeleton variant="rectangular" className="h-48 w-full" />
      </div>
    );
  }

  if (!auth.isAuthenticated) {
    const returnUrl = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?returnUrl=${returnUrl}`} replace />;
  }

  return children ? <>{children}</> : <Outlet />;
};
