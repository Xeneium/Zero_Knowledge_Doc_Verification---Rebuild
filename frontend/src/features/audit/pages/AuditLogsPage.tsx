import { useState, useEffect } from 'react';
import {
  CheckCircle2,
  FileText,
  Filter,
  Link2,
  RefreshCw,
  Search,
  Shield,
  ShieldAlert,
  ShieldCheck,
  XCircle,
} from 'lucide-react';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Alert } from '../../../components/ui/Alert';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Input, Select } from '../../../components/ui/Field';
import { Dialog } from '../../../components/ui/Dialog';
import { useAuth } from '../../auth/AuthContext';
import {
  fetchAuditEvents,
  verifyAuditChainIntegrity,
  type AuditEventDto,
  type AuditIntegrityResult,
} from '../../administration/api/adminApi';

export function AuditLogsPage() {
  const { session, isAuthenticated, hasRole, connectPreset } = useAuth();

  const [events, setEvents] = useState<AuditEventDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState('');
  const [actorFilter, setActorFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Integrity Check State
  const [integrityResult, setIntegrityResult] = useState<AuditIntegrityResult | null>(null);
  const [integrityLoading, setIntegrityLoading] = useState(false);
  const [integrityError, setIntegrityError] = useState<string | null>(null);

  // Detail Modal
  const [selectedEvent, setSelectedEvent] = useState<AuditEventDto | null>(null);

  useEffect(() => {
    if (isAuthenticated) {
      loadEvents(page);
    }
  }, [isAuthenticated, page, categoryFilter]);

  const loadEvents = async (pageIndex = 0) => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetchAuditEvents({
        page: pageIndex,
        size: 15,
        category: categoryFilter || undefined,
        actor: actorFilter.trim() || undefined,
        type: typeFilter.trim() || undefined,
      });
      setEvents(res.items);
      setTotal(res.total);
      setPage(res.page);
      setTotalPages(res.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load audit events');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyIntegrity = async () => {
    setIntegrityLoading(true);
    setIntegrityError(null);
    try {
      const res = await verifyAuditChainIntegrity();
      setIntegrityResult(res);
    } catch (err) {
      setIntegrityError(err instanceof Error ? err.message : 'Integrity verification failed');
    } finally {
      setIntegrityLoading(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Audit logs"
        description="Review verification metadata and cryptographically verify tamper-evident audit hash chain integrity."
        breadcrumbs={[{ label: 'Overview', to: '/' }, { label: 'Audit logs' }]}
        actions={
          isAuthenticated ? (
            <Badge tone="success">
              {session?.roles[0]} · Admin Session
            </Badge>
          ) : (
            <Badge tone="warning">Admin Role Required</Badge>
          )
        }
      />

      {!isAuthenticated || !hasRole('ADMIN') ? (
        <Alert
          tone="info"
          title="Administrator access recommended"
          className="mb-6"
        >
          <p>
            Audit events and cryptographic hash chain verification are protected by the platform's RBAC controls (<code>ROLE_ADMIN</code>).
          </p>
          <div className="mt-3">
            <Button size="sm" variant="secondary" onClick={() => connectPreset('ADMIN')}>
              Connect as Administrator (admin-root)
            </Button>
          </div>
        </Alert>
      ) : null}

      <div className="space-y-6">
        {/* Hash Chain Integrity Section */}
        <Card className="p-6">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-ink-100 pb-4">
            <div className="flex items-center gap-3">
              <span className="inline-flex size-10 items-center justify-center rounded-lg bg-accent-50 text-accent-700">
                <Link2 className="size-5" />
              </span>
              <div>
                <h2 className="text-base font-semibold text-ink-900">Cryptographic Hash Chain Integrity</h2>
                <p className="text-xs text-ink-600">
                  Each audit event contains a SHA-256 hash incorporating the previous event's hash. Verifies the sequence is continuous and tamper-evident.
                </p>
              </div>
            </div>
            <Button
              variant="primary"
              size="sm"
              loading={integrityLoading}
              onClick={handleVerifyIntegrity}
              disabled={!isAuthenticated}
            >
              <ShieldCheck className="size-4" />
              Verify Chain Integrity
            </Button>
          </div>

          {integrityError && (
            <Alert tone="danger" title="Integrity Check Failed" className="mt-4">
              {integrityError}
            </Alert>
          )}

          {integrityResult && (
            <div
              className={`rounded-xl border p-4 mt-4 ${
                integrityResult.valid
                  ? 'border-success-200 bg-success-50 text-success-900'
                  : 'border-danger-200 bg-danger-50 text-danger-900'
              }`}
            >
              <div className="flex items-center gap-2 mb-2">
                {integrityResult.valid ? (
                  <CheckCircle2 className="size-5 text-success-700" />
                ) : (
                  <XCircle className="size-5 text-danger-700" />
                )}
                <h3 className="text-sm font-semibold">
                  {integrityResult.valid
                    ? 'Cryptographic Hash Chain Integrity Confirmed: Unbroken'
                    : 'Hash Chain Broken / Tampering Detected!'}
                </h3>
              </div>
              <div className="text-xs space-y-1">
                <p>
                  <strong>Total Events Verified:</strong> {integrityResult.totalEventsChecked} events
                  (Sequence {integrityResult.startSequence} to {integrityResult.endSequence})
                </p>
                {integrityResult.firstBrokenSequence && (
                  <p className="font-semibold text-danger-700">
                    Broken sequence detected at record #{integrityResult.firstBrokenSequence}!
                  </p>
                )}
                <p className="text-ink-600 mt-1">{integrityResult.message}</p>
              </div>
            </div>
          )}
        </Card>

        {/* Audit Events Explorer */}
        <Card className="p-6">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-4">
            <h2 className="text-base font-semibold text-ink-900">Security Audit Trail ({total} Events)</h2>
            <Button
              variant="secondary"
              size="sm"
              loading={loading}
              onClick={() => loadEvents(page)}
              disabled={!isAuthenticated}
            >
              <RefreshCw className="size-3.5" />
              Refresh Events
            </Button>
          </div>

          {/* Filter Bar */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4 p-3 bg-ink-50 rounded-lg border">
            <div>
              <label className="text-xs font-semibold text-ink-700 block mb-1">Category</label>
              <select
                value={categoryFilter}
                onChange={(e) => {
                  setCategoryFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full text-xs rounded border border-ink-300 p-1.5 bg-white"
              >
                <option value="">All Categories</option>
                <option value="AUTHENTICATION">AUTHENTICATION</option>
                <option value="AUTHORIZATION">AUTHORIZATION</option>
                <option value="CREDENTIAL_LIFECYCLE">CREDENTIAL_LIFECYCLE</option>
                <option value="VERIFICATION">VERIFICATION</option>
                <option value="DOCUMENT_SECURITY">DOCUMENT_SECURITY</option>
                <option value="SYSTEM">SYSTEM</option>
              </select>
            </div>
            <div>
              <label className="text-xs font-semibold text-ink-700 block mb-1">Actor Search</label>
              <input
                type="text"
                placeholder="Filter by actor"
                value={actorFilter}
                onChange={(e) => setActorFilter(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && loadEvents(0)}
                className="w-full text-xs rounded border border-ink-300 p-1.5 bg-white"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-ink-700 block mb-1">Event Type</label>
              <input
                type="text"
                placeholder="Filter by event type"
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && loadEvents(0)}
                className="w-full text-xs rounded border border-ink-300 p-1.5 bg-white"
              />
            </div>
          </div>

          {error && (
            <Alert tone="danger" title="Error Loading Events" className="mb-4">
              {error}
            </Alert>
          )}

          {events.length === 0 ? (
            <div className="text-center py-10 text-sm text-ink-500 border rounded-lg bg-ink-50/50">
              {isAuthenticated
                ? 'No audit events found matching filters.'
                : 'Connect an Administrator session to query live security audit events.'}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b bg-ink-50/80 text-ink-700">
                    <th className="py-2.5 px-3 font-semibold">Seq</th>
                    <th className="py-2.5 px-3 font-semibold">Timestamp</th>
                    <th className="py-2.5 px-3 font-semibold">Event Type</th>
                    <th className="py-2.5 px-3 font-semibold">Category</th>
                    <th className="py-2.5 px-3 font-semibold">Actor</th>
                    <th className="py-2.5 px-3 font-semibold">Action</th>
                    <th className="py-2.5 px-3 font-semibold">Outcome</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-ink-100">
                  {events.map((evt) => (
                    <tr
                      key={evt.id}
                      onClick={() => setSelectedEvent(evt)}
                      className="hover:bg-accent-50/50 cursor-pointer transition"
                    >
                      <td className="py-2 px-3 font-mono text-ink-500">#{evt.sequence}</td>
                      <td className="py-2 px-3 text-ink-600 whitespace-nowrap">
                        {new Date(evt.timestamp).toLocaleString()}
                      </td>
                      <td className="py-2 px-3 font-semibold text-ink-900">{evt.eventType}</td>
                      <td className="py-2 px-3">
                        <Badge tone="neutral">{evt.category}</Badge>
                      </td>
                      <td className="py-2 px-3 font-mono text-ink-700">{evt.actor}</td>
                      <td className="py-2 px-3 text-ink-600">{evt.action}</td>
                      <td className="py-2 px-3">
                        <Badge
                          tone={
                            evt.outcome === 'SUCCESS'
                              ? 'success'
                              : evt.outcome === 'DENIED'
                              ? 'warning'
                              : 'danger'
                          }
                        >
                          {evt.outcome}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-ink-100 pt-4 mt-4 text-xs">
              <span className="text-ink-600">
                Page {page + 1} of {totalPages}
              </span>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={page <= 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </Card>
      </div>

      {/* Event Details Dialog */}
      <Dialog
        open={selectedEvent !== null}
        onClose={() => setSelectedEvent(null)}
        title="Security Audit Event Details"
        description="Structured tamper-evident audit record"
        footer={
          <Button variant="secondary" size="sm" onClick={() => setSelectedEvent(null)}>
            Close
          </Button>
        }
      >
        {selectedEvent && (
          <div className="space-y-3 font-mono text-xs">
            <div className="p-3 bg-ink-900 text-ink-100 rounded-lg overflow-x-auto space-y-1">
              <p><strong>Sequence:</strong> #{selectedEvent.sequence}</p>
              <p><strong>Event ID:</strong> {selectedEvent.id}</p>
              <p><strong>Event Type:</strong> {selectedEvent.eventType}</p>
              <p><strong>Category:</strong> {selectedEvent.category}</p>
              <p><strong>Actor:</strong> {selectedEvent.actor} ({selectedEvent.actorType})</p>
              <p><strong>Action:</strong> {selectedEvent.action}</p>
              <p><strong>Outcome:</strong> {selectedEvent.outcome}</p>
              <p><strong>Target:</strong> {selectedEvent.targetType} ({selectedEvent.targetId || 'N/A'})</p>
              <p><strong>Correlation ID:</strong> {selectedEvent.correlationId || 'N/A'}</p>
              <p><strong>Client IP:</strong> {selectedEvent.clientIp || 'N/A'}</p>
              <p><strong>Timestamp:</strong> {selectedEvent.timestamp}</p>
            </div>
            {selectedEvent.details && Object.keys(selectedEvent.details).length > 0 && (
              <div>
                <span className="font-sans font-semibold text-ink-900 text-xs block mb-1">
                  Metadata Details:
                </span>
                <pre className="p-2.5 bg-ink-50 rounded border text-[11px] overflow-x-auto">
                  {JSON.stringify(selectedEvent.details, null, 2)}
                </pre>
              </div>
            )}
          </div>
        )}
      </Dialog>
    </>
  );
}
