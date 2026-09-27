import {
  createBrowserRouter,
  createMemoryRouter,
  type RouteObject,
} from 'react-router';
import { FileCheck2, Files, ScrollText, ShieldCheck } from 'lucide-react';
import { AppShell } from '../components/layout/AppShell';
import { OverviewPage } from '../features/dashboard/OverviewPage';
import { FeaturePlaceholderPage } from '../routes/FeaturePlaceholderPage';
import { NotFoundPage } from '../routes/NotFoundPage';
import { PlatformOperationsRoute, PublicVerificationRoute } from './LazyPages';

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
        handle: { title: 'Credentials' },
        element: (
          <FeaturePlaceholderPage
            title="Credentials"
            description="Issue and assess signed credentials through an authorization-aware interface."
            detail="The route, feedback patterns, and feature boundary are in place for credential operations. No credential data is fabricated or loaded in this phase."
            icon={Files}
            backendNote="POST /api/credentials is issuer-only; GET /api/credentials/{id}/verify requires an authenticated platform role."
          />
        ),
      },
      {
        path: 'verification',
        handle: { title: 'Verification' },
        element: (
          <FeaturePlaceholderPage
            title="Verification"
            description="Prepare document verification workflows with explicit, trustworthy outcomes."
            detail="This page reserves the document verification experience while keeping uploads and results inactive until authentication and request validation are implemented."
            icon={FileCheck2}
            backendNote="Stored-copy and uploaded-copy verification routes exist under /api/documents/{id}."
          />
        ),
      },
      {
        path: 'predicate-verification',
        handle: { title: 'Predicate verification' },
        element: (
          <FeaturePlaceholderPage
            title="Predicate verification"
            description="Verify a threshold claim without disclosing the underlying credential value."
            detail="The future proof-verification flow has a dedicated route and presentation boundary, without accepting or persisting proof material in Phase 1."
            icon={ShieldCheck}
            backendNote="POST /api/v2/zkp/credential-predicates/verify is verifier-only and rejects unsupported fields."
          />
        ),
      },
      {
        path: 'audit-logs',
        handle: { title: 'Audit logs' },
        element: (
          <FeaturePlaceholderPage
            title="Audit logs"
            description="Review verification metadata without exposing document contents or proof material."
            detail="A responsive page boundary is ready for a future audit query contract. The current backend records events but does not expose a query controller."
            icon={ScrollText}
            backendNote="The backend currently stores actor, event type, target identifiers, timestamp, and result only; no audit-read endpoint was found."
          />
        ),
      },
      {
        path: 'administration',
        handle: { title: 'Platform operations' },
        element: <PlatformOperationsRoute />,
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
