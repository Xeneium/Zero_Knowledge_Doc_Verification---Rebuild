import {
  ExternalLink,
  FileClock,
  KeyRound,
  LockKeyhole,
  ShieldAlert,
  UserRoundCog,
} from 'lucide-react';
import { Link } from 'react-router';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Alert } from '../../../components/ui/Alert';
import { Badge, type BadgeTone } from '../../../components/ui/Badge';
import { Card } from '../../../components/ui/Card';

interface Capability {
  name: string;
  backend: string;
  decision: string;
  status: string;
  tone: BadgeTone;
}

const capabilities: Capability[] = [
  {
    name: 'Account profile and password',
    backend: 'No user, profile, or password endpoints',
    decision: 'No settings form or account data is displayed.',
    status: 'Unavailable',
    tone: 'neutral',
  },
  {
    name: 'Sessions and token revocation',
    backend: 'Stateless bearer JWT validation; no session records',
    decision: 'No client-only session or revoke-all control is presented.',
    status: 'Unavailable',
    tone: 'neutral',
  },
  {
    name: 'Audit records',
    backend: 'Verification metadata is recorded without a read API',
    decision: 'Viewing, filtering, details, and export remain disabled.',
    status: 'Write-only',
    tone: 'info',
  },
  {
    name: 'Users and roles',
    backend: 'No account directory or role-management endpoints',
    decision: 'No fabricated user list or role controls are rendered.',
    status: 'Unavailable',
    tone: 'neutral',
  },
  {
    name: 'Signing-key registry',
    backend: 'ADMIN-only registration and status mutation endpoints',
    decision:
      'No mutation UI until authenticated ADMIN context and a safe read contract exist.',
    status: 'Backend only',
    tone: 'warning',
  },
];

export function PlatformOperationsPage() {
  return (
    <>
      <PageHeader
        title="Platform operations"
        description="A truthful view of the current administration and account-security boundaries."
        breadcrumbs={[
          { label: 'Overview', to: '/' },
          { label: 'Platform operations' },
        ]}
        actions={<Badge tone="warning">No admin session</Badge>}
      />

      <Alert
        tone="warning"
        title="Administrative access is not connected"
        className="mb-5"
      >
        The backend accepts externally issued bearer JWTs, but this client has
        no identity provider or authenticated account context. It does not send
        requests to protected administration endpoints or expose controls that
        could imply otherwise.
      </Alert>

      <section
        className="grid gap-4 lg:grid-cols-3"
        aria-label="Security architecture"
      >
        <Card className="p-5 sm:p-6">
          <LockKeyhole className="text-accent-700 size-6" aria-hidden="true" />
          <h2 className="text-ink-900 mt-4 font-semibold">
            Stateless authentication
          </h2>
          <p className="text-ink-600 mt-2 text-sm leading-6">
            Spring Security validates bearer JWTs against registered JWT public
            keys. The application does not issue tokens, refresh them, or keep
            server-side sessions.
          </p>
        </Card>
        <Card className="p-5 sm:p-6">
          <UserRoundCog className="text-accent-700 size-6" aria-hidden="true" />
          <h2 className="text-ink-900 mt-4 font-semibold">
            Backend-enforced roles
          </h2>
          <p className="text-ink-600 mt-2 text-sm leading-6">
            The recognized roles are ISSUER, HOLDER, VERIFIER, and ADMIN.
            Frontend visibility would improve usability only; it would never
            replace backend authorization.
          </p>
        </Card>
        <Card className="p-5 sm:p-6">
          <FileClock className="text-accent-700 size-6" aria-hidden="true" />
          <h2 className="text-ink-900 mt-4 font-semibold">
            Metadata-only auditing
          </h2>
          <p className="text-ink-600 mt-2 text-sm leading-6">
            Verification attempts record actor, event type, target identifiers,
            timestamp, and result. The records are not exposed through an API
            and are not claimed to be tamper-proof.
          </p>
        </Card>
      </section>

      <Card className="mt-5 overflow-hidden">
        <div className="border-ink-200 border-b p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <ShieldAlert
              className="text-warning-700 mt-0.5 size-5 shrink-0"
              aria-hidden="true"
            />
            <div>
              <h2 className="text-ink-900 font-semibold">
                Capability decisions
              </h2>
              <p className="text-ink-600 mt-1 text-sm leading-6">
                Every unavailable control below is intentionally omitted because
                the required backend contract or authenticated client context is
                absent.
              </p>
            </div>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] border-collapse text-left text-sm">
            <caption className="sr-only">
              Phase 5 account and administration capability decisions
            </caption>
            <thead className="bg-ink-50 text-ink-700">
              <tr>
                <th scope="col" className="px-5 py-3 font-semibold sm:px-6">
                  Capability
                </th>
                <th scope="col" className="px-5 py-3 font-semibold sm:px-6">
                  Backend contract
                </th>
                <th scope="col" className="px-5 py-3 font-semibold sm:px-6">
                  Frontend decision
                </th>
                <th scope="col" className="px-5 py-3 font-semibold sm:px-6">
                  Status
                </th>
              </tr>
            </thead>
            <tbody className="divide-ink-200 divide-y">
              {capabilities.map((capability) => (
                <tr key={capability.name} className="align-top">
                  <th
                    scope="row"
                    className="text-ink-900 px-5 py-4 font-semibold sm:px-6"
                  >
                    {capability.name}
                  </th>
                  <td className="text-ink-600 px-5 py-4 sm:px-6">
                    {capability.backend}
                  </td>
                  <td className="text-ink-600 px-5 py-4 sm:px-6">
                    {capability.decision}
                  </td>
                  <td className="px-5 py-4 sm:px-6">
                    <Badge tone={capability.tone}>{capability.status}</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <section className="mt-5 grid gap-4 lg:grid-cols-2">
        <Card className="p-5 sm:p-6">
          <KeyRound className="text-warning-700 size-6" aria-hidden="true" />
          <h2 className="text-ink-900 mt-4 font-semibold">
            Backend-only key administration
          </h2>
          <p className="text-ink-600 mt-2 text-sm leading-6">
            An authenticated ADMIN may register public-key metadata with{' '}
            <code>POST /api/admin/keys</code> and change a known key status with{' '}
            <code>PATCH /api/admin/keys/{'{keyId}'}/status</code>. There is no
            list or detail endpoint, so this interface does not offer a partial
            or blind mutation workflow.
          </p>
        </Card>
        <Card className="p-5 sm:p-6">
          <ExternalLink
            className="text-success-700 size-6"
            aria-hidden="true"
          />
          <h2 className="text-ink-900 mt-4 font-semibold">
            Available public workflow
          </h2>
          <p className="text-ink-600 mt-2 text-sm leading-6">
            Public document verification remains available without exposing
            account, document, or administration data.
          </p>
          <Link
            to="/verify"
            className="text-accent-700 focus-visible:outline-accent-600 mt-4 inline-flex min-h-11 items-center rounded-lg font-semibold underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2"
          >
            Open public verification
          </Link>
        </Card>
      </section>
    </>
  );
}
