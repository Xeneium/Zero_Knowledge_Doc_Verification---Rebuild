import {
  ArrowRight,
  Braces,
  Fingerprint,
  LockKeyhole,
  ServerCog,
  ShieldCheck,
} from 'lucide-react';
import { Link } from 'react-router';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { BackendStatus } from './BackendStatus';

const principles = [
  {
    title: 'Privacy by design',
    description:
      'Public verification reveals only signature validity and active status, never the stored document contents.',
    icon: Fingerprint,
  },
  {
    title: 'Server-enforced trust',
    description:
      'Authorization and cryptographic decisions remain with Spring Security and the backend services.',
    icon: LockKeyhole,
  },
  {
    title: 'Verifiable outcomes',
    description:
      'Backend results, inactive records, invalid signatures, and service failures are presented as distinct outcomes.',
    icon: ShieldCheck,
  },
];

export function OverviewPage() {
  return (
    <>
      <PageHeader
        title="Verification workspace"
        description="A secure, accessible foundation for privacy-preserving document and credential workflows."
      />

      <section
        className="bg-ink-950 relative overflow-hidden rounded-xl px-6 py-8 text-white shadow-[var(--shadow-md)] sm:px-8 sm:py-10"
        aria-labelledby="workspace-heading"
      >
        <div
          className="absolute top-0 right-0 h-full w-2/5 opacity-25"
          aria-hidden="true"
        >
          <div className="border-accent-400/50 absolute top-8 right-10 size-40 rounded-full border" />
          <div className="border-accent-400/50 absolute top-20 right-24 size-24 rounded-full border" />
          <div className="bg-accent-400/50 absolute right-4 bottom-0 h-px w-64 rotate-[-25deg]" />
        </div>
        <div className="relative max-w-3xl">
          <Badge tone="accent">Public verification available</Badge>
          <h2
            id="workspace-heading"
            className="mt-5 text-3xl font-bold tracking-tight sm:text-4xl"
          >
            Trust the result. Keep the data private.
          </h2>
          <p className="text-ink-200 mt-4 max-w-2xl text-base leading-7 sm:text-lg">
            Scan a platform QR code or enter its opaque reference. The backend
            performs the cryptographic check while the interface discloses only
            the minimum public result.
          </p>
          <Link
            to="/verify"
            className="bg-accent-500 text-ink-950 hover:bg-accent-400 mt-7 inline-flex min-h-11 items-center gap-2 rounded-lg px-4 text-sm font-bold transition-colors"
          >
            Verify a document{' '}
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </div>
      </section>

      <section
        className="mt-6 grid gap-4 md:grid-cols-3"
        aria-label="Platform principles"
      >
        {principles.map((principle) => {
          const Icon = principle.icon;
          return (
            <Card key={principle.title} className="p-5 sm:p-6">
              <span className="bg-accent-50 text-accent-700 inline-flex size-10 items-center justify-center rounded-lg">
                <Icon className="size-5" aria-hidden="true" />
              </span>
              <h2 className="text-ink-900 mt-4 text-lg font-semibold">
                {principle.title}
              </h2>
              <p className="text-ink-600 mt-2 text-sm leading-6">
                {principle.description}
              </p>
            </Card>
          );
        })}
      </section>

      <section className="mt-6 grid gap-4 lg:grid-cols-[1.15fr_0.85fr]">
        <Card className="p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <span className="bg-info-50 text-info-700 inline-flex size-10 shrink-0 items-center justify-center rounded-lg">
              <Braces className="size-5" aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-ink-900 text-lg font-semibold">
                Built for the actual API
              </h2>
              <p className="text-ink-600 mt-1 text-sm leading-6">
                The client validates configuration, handles JSON and empty
                responses, normalizes failures, supports cancellation, and uses
                the backend's exact public verification contract.
              </p>
            </div>
          </div>
          <div className="border-ink-200 bg-ink-50 text-ink-700 mt-5 rounded-lg border p-4 font-mono text-sm">
            <p>
              <span className="text-accent-700">GET</span> /api/health{' '}
              <span className="text-ink-400">→</span> {'{ "status": "ok" }'}
            </p>
            <p className="mt-2 break-all">
              <span className="text-accent-700">GET</span> /api/public/verify/
              {'{token}'} <span className="text-ink-400">→</span>{' '}
              {'{ signatureValid, documentActive }'}
            </p>
          </div>
        </Card>

        <Card className="p-5 sm:p-6">
          <div className="mb-5 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <ServerCog className="text-ink-600 size-5" aria-hidden="true" />
              <h2 className="text-ink-900 font-semibold">Service connection</h2>
            </div>
            <Badge tone="neutral">Live check</Badge>
          </div>
          <BackendStatus />
        </Card>
      </section>
    </>
  );
}
