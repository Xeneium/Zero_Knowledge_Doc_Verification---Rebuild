import { lazy, Suspense } from 'react';
import { RouteLoading } from '../components/feedback/RouteLoading';

const PublicVerificationPage = lazy(async () => {
  const page =
    await import('../features/verification/pages/PublicVerificationPage');
  return { default: page.PublicVerificationPage };
});

const CredentialsPage = lazy(async () => {
  const page = await import('../features/credentials/pages/CredentialsPage');
  return { default: page.CredentialsPage };
});

const DocumentVerificationPage = lazy(async () => {
  const page =
    await import('../features/verification/pages/DocumentVerificationPage');
  return { default: page.DocumentVerificationPage };
});

const PredicateVerificationPage = lazy(async () => {
  const page =
    await import('../features/verification/pages/PredicateVerificationPage');
  return { default: page.PredicateVerificationPage };
});

const AuditLogsPage = lazy(async () => {
  const page = await import('../features/audit/pages/AuditLogsPage');
  return { default: page.AuditLogsPage };
});

const PlatformOperationsPage = lazy(async () => {
  const page =
    await import('../features/administration/pages/PlatformOperationsPage');
  return { default: page.PlatformOperationsPage };
});

export function PublicVerificationRoute() {
  return (
    <Suspense fallback={<RouteLoading />}>
      <PublicVerificationPage />
    </Suspense>
  );
}

export function CredentialsRoute() {
  return (
    <Suspense fallback={<RouteLoading />}>
      <CredentialsPage />
    </Suspense>
  );
}

export function DocumentVerificationRoute() {
  return (
    <Suspense fallback={<RouteLoading />}>
      <DocumentVerificationPage />
    </Suspense>
  );
}

export function PredicateVerificationRoute() {
  return (
    <Suspense fallback={<RouteLoading />}>
      <PredicateVerificationPage />
    </Suspense>
  );
}

export function AuditLogsRoute() {
  return (
    <Suspense fallback={<RouteLoading />}>
      <AuditLogsPage />
    </Suspense>
  );
}

export function PlatformOperationsRoute() {
  return (
    <Suspense fallback={<RouteLoading />}>
      <PlatformOperationsPage />
    </Suspense>
  );
}
