import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router';
import { ErrorBoundary } from './ErrorBoundary';
import { AppLayout } from '../components/layout/AppLayout';
import { HomePage } from '../routes/HomePage';
import { LoginPage } from '../routes/LoginPage';
import { PublicVerificationPage } from '../routes/PublicVerificationPage';
import { AuditLogsPage } from '../routes/AuditLogsPage';
import { AdminDashboardPage } from '../routes/AdminDashboardPage';
import { IssuerDashboardPage } from '../routes/IssuerDashboardPage';
import { HolderDashboardPage } from '../routes/HolderDashboardPage';
import { ForbiddenPage } from '../routes/ForbiddenPage';
import { NotFoundPage } from '../routes/NotFoundPage';
import { RequireAuth, RequireRole } from '../components/auth';

export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <Routes>
          <Route element={<AppLayout />}>
            {/* Public Routes */}
            <Route index element={<HomePage />} />
            <Route path="login" element={<LoginPage />} />
            <Route path="verify" element={<PublicVerificationPage />} />
            <Route path="verify/:token" element={<PublicVerificationPage />} />
            <Route path="verification" element={<PublicVerificationPage />} />
            <Route
              path="predicate-verification"
              element={<PublicVerificationPage />}
            />
            <Route path="forbidden" element={<ForbiddenPage />} />

            {/* Protected Routes Requiring Valid Authenticated Session */}
            <Route element={<RequireAuth />}>
              {/* Holder Workspace (Phase F7) */}
              <Route
                element={<RequireRole allowedRoles={['HOLDER', 'ADMIN']} />}
              >
                <Route path="credentials" element={<HolderDashboardPage />} />
                <Route path="holder" element={<HolderDashboardPage />} />
              </Route>

              {/* Issuer Workspace (Phase F6) */}
              <Route
                element={<RequireRole allowedRoles={['ISSUER', 'ADMIN']} />}
              >
                <Route path="issuer" element={<IssuerDashboardPage />} />
              </Route>

              {/* Audit Logs Workspace (Admin & Issuer) */}
              <Route
                element={<RequireRole allowedRoles={['ADMIN', 'ISSUER']} />}
              >
                <Route path="audit-logs" element={<AuditLogsPage />} />
              </Route>

              {/* Administration Console (Admin only) */}
              <Route element={<RequireRole allowedRoles={['ADMIN']} />}>
                <Route
                  path="administration"
                  element={<AdminDashboardPage />}
                />
              </Route>
            </Route>

            {/* Fallback 404 Route */}
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ErrorBoundary>
  );
};
