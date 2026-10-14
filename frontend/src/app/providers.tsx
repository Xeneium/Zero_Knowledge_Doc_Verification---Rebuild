import type { ReactNode } from 'react';
import { ErrorBoundary } from './ErrorBoundary';
import { AuthProvider } from '../features/auth/AuthContext';

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ErrorBoundary>
      <AuthProvider>{children}</AuthProvider>
    </ErrorBoundary>
  );
}
