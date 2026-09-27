import { lazy, Suspense } from 'react';
import { RouteLoading } from '../components/feedback/RouteLoading';

const PublicVerificationPage = lazy(async () => {
  const page =
    await import('../features/verification/pages/PublicVerificationPage');
  return { default: page.PublicVerificationPage };
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

export function PlatformOperationsRoute() {
  return (
    <Suspense fallback={<RouteLoading />}>
      <PlatformOperationsPage />
    </Suspense>
  );
}
