import {
  createBrowserRouter,
  createMemoryRouter,
  type RouteObject,
} from 'react-router';
import { AppShell } from '../components/layout/AppShell';
import { OverviewPage } from '../features/dashboard/OverviewPage';
import { CredentialsPage } from '../features/credentials/pages/CredentialsPage';
import { NotFoundPage } from '../routes/NotFoundPage';
import {
  AuditLogsRoute,
  DocumentVerificationRoute,
  PlatformOperationsRoute,
  PredicateVerificationRoute,
  PublicVerificationRoute,
} from './LazyPages';

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <OverviewPage />, handle: { title: 'Overview' } },
      {
        path: 'verify',
        element: <PublicVerificationRoute />,
        handle: { title: 'Public verification' },
      },
      {
        path: 'credentials',
        element: <CredentialsPage />,
        handle: { title: 'Credentials' },
      },
      {
        path: 'verification',
        element: <DocumentVerificationRoute />,
        handle: { title: 'Verification' },
      },
      {
        path: 'predicate-verification',
        element: <PredicateVerificationRoute />,
        handle: { title: 'Predicate verification' },
      },
      {
        path: 'audit-logs',
        element: <AuditLogsRoute />,
        handle: { title: 'Audit logs' },
      },
      {
        path: 'administration',
        element: <PlatformOperationsRoute />,
        handle: { title: 'Platform operations' },
      },
      {
        path: '*',
        element: <NotFoundPage />,
        handle: { title: 'Page not found' },
      },
    ],
  },
];

export const router = createBrowserRouter(routes);

export function createTestRouter(initialEntries: string[] = ['/']) {
  return createMemoryRouter(routes, { initialEntries });
}
