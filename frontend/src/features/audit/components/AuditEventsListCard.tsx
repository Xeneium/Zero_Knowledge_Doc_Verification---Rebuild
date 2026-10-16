import React, { useState, useEffect } from 'react';
import {
  FileText,
  Search,
  RefreshCw,
  Download,
  ChevronLeft,
  ChevronRight,
  Eye,
} from 'lucide-react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '../../../components/common/Card';
import { Badge } from '../../../components/common/Badge';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { Select } from '../../../components/common/Select';
import { Alert } from '../../../components/feedback/Alert';
import { adminApi } from '../../../api/adminApi';
import {
  adminStore,
  useAdminAuditQueryResult,
  useAdminStore,
} from '../../../state/adminStore';
import type {
  AuditEventCategory,
  AuditQueryFilterParams,
  SecurityAuditEventResponse,
} from '../../../api/types';
import { AuditEventDetailModal } from './AuditEventDetailModal';

export const AuditEventsListCard: React.FC = () => {
  const queryResult = useAdminAuditQueryResult();
  const { auditFilter, isLoadingAudit, auditError } = useAdminStore();

  const [category, setCategory] = useState<string>(auditFilter.category || '');
  const [eventType, setEventType] = useState<string>(auditFilter.type || '');
  const [actor, setActor] = useState<string>(auditFilter.actor || '');
  const [targetId, setTargetId] = useState<string>(auditFilter.targetId || '');
  const [correlationId, setCorrelationId] = useState<string>(
    auditFilter.correlationId || '',
  );
  const [fromTime, setFromTime] = useState<string>(auditFilter.from || '');
  const [toTime, setToTime] = useState<string>(auditFilter.to || '');

  const [selectedEvent, setSelectedEvent] =
    useState<SecurityAuditEventResponse | null>(null);

  const fetchEvents = async (page = 0, size = auditFilter.size || 20) => {
    adminStore.setLoadingAudit(true);

    const filterParams: AuditQueryFilterParams = {
      category: (category as AuditEventCategory) || undefined,
      type: eventType.trim() || undefined,
      actor: actor.trim() || undefined,
      targetId: targetId.trim() || undefined,
      correlationId: correlationId.trim() || undefined,
      from: fromTime ? new Date(fromTime).toISOString() : undefined,
      to: toTime ? new Date(toTime).toISOString() : undefined,
      page,
      size,
    };

    adminStore.setAuditFilter(filterParams);

    try {
      const res = await adminApi.queryAuditEvents(filterParams);
      adminStore.setAuditQueryResult(res);
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : 'Failed to query audit trail';
      adminStore.setAuditError(msg);
    } finally {
      adminStore.setLoadingAudit(false);
    }
  };

  useEffect(() => {
    // Initial fetch on mount
    void fetchEvents(0, 20);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    void fetchEvents(0, auditFilter.size || 20);
  };

  const handleReset = () => {
    setCategory('');
    setEventType('');
    setActor('');
    setTargetId('');
    setCorrelationId('');
    setFromTime('');
    setToTime('');
    adminStore.setAuditFilter({ page: 0, size: 20 });
    void adminApi
      .queryAuditEvents({ page: 0, size: 20 })
      .then((res) => adminStore.setAuditQueryResult(res))
      .catch((err) => {
        const msg =
          err instanceof Error ? err.message : 'Failed to query audit trail';
        adminStore.setAuditError(msg);
      });
  };

  const handleExportJson = () => {
    if (!queryResult || queryResult.items.length === 0) return;
    const blob = new Blob([JSON.stringify(queryResult.items, null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `audit-trail-p${queryResult.page}-${new Date().toISOString()}.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const outcomeBadge = (outcome: string) => {
    switch (outcome) {
      case 'SUCCESS':
        return <Badge variant="success">SUCCESS</Badge>;
      case 'FAILURE':
        return <Badge variant="danger">FAILURE</Badge>;
      case 'DENIED':
        return <Badge variant="warning">DENIED</Badge>;
      default:
        return <Badge variant="neutral">{outcome}</Badge>;
    }
  };

  return (
    <Card className="shadow-xs">
      <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <FileText
              className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
              aria-hidden="true"
            />
            <CardTitle>Immutable Security Audit Trail</CardTitle>
          </div>
          <CardDescription>
            Search, filter, and review tamper-evident security events,
            cryptographic actions, access checks, and administrative overrides.
          </CardDescription>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void fetchEvents(queryResult?.page || 0)}
            isLoading={isLoadingAudit}
            aria-label="Refresh audit events"
          >
            <RefreshCw className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
            Refresh
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={handleExportJson}
            disabled={!queryResult || queryResult.items.length === 0}
            aria-label="Export audit events as JSON"
          >
            <Download className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
            Export Page
          </Button>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {auditError && (
          <Alert variant="danger" title="Audit Query Error">
            {auditError}
          </Alert>
        )}

        {/* Filter Controls Bar */}
        <form
          onSubmit={handleSearch}
          className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50"
        >
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Select
              label="Security Domain Category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              options={[
                { value: '', label: 'All Categories' },
                { value: 'AUTHENTICATION', label: 'AUTHENTICATION' },
                { value: 'AUTHORIZATION', label: 'AUTHORIZATION' },
                { value: 'CREDENTIAL', label: 'CREDENTIAL' },
                { value: 'DOCUMENT', label: 'DOCUMENT' },
                { value: 'QR', label: 'QR CAPABILITY' },
                { value: 'ZKP', label: 'ZKP PROOF' },
                { value: 'ABUSE', label: 'ABUSE / RATE LIMIT' },
                { value: 'ADMIN', label: 'ADMIN / RECOVERY' },
                { value: 'KEY', label: 'KEY LIFECYCLE' },
              ]}
            />

            <Input
              label="Event Type"
              value={eventType}
              onChange={(e) => setEventType(e.target.value)}
              placeholder="e.g. ACCESS_DENIED"
            />

            <Input
              label="Actor Principal"
              value={actor}
              onChange={(e) => setActor(e.target.value)}
              placeholder="e.g. issuer-1 or admin"
            />

            <Input
              label="Target ID"
              value={targetId}
              onChange={(e) => setTargetId(e.target.value)}
              placeholder="UUID or resource ID"
            />
          </div>

          <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Input
              label="Correlation ID"
              value={correlationId}
              onChange={(e) => setCorrelationId(e.target.value)}
              placeholder="MDC trace ID"
            />

            <Input
              label="From Timestamp"
              type="datetime-local"
              value={fromTime}
              onChange={(e) => setFromTime(e.target.value)}
            />

            <Input
              label="To Timestamp"
              type="datetime-local"
              value={toTime}
              onChange={(e) => setToTime(e.target.value)}
            />
          </div>

          <div className="mt-4 flex items-center justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleReset}
            >
              Reset Filters
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              isLoading={isLoadingAudit}
            >
              <Search className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
              Apply Filters
            </Button>
          </div>
        </form>

        {/* Audit Events Table */}
        <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
          <table className="min-w-full divide-y divide-slate-200 text-left text-xs dark:divide-slate-800">
            <thead className="bg-slate-50 text-slate-700 dark:bg-slate-900/60 dark:text-slate-300">
              <tr>
                <th scope="col" className="px-3 py-2.5 font-semibold">
                  Seq #
                </th>
                <th scope="col" className="px-3 py-2.5 font-semibold">
                  Timestamp
                </th>
                <th scope="col" className="px-3 py-2.5 font-semibold">
                  Event Category &amp; Type
                </th>
                <th scope="col" className="px-3 py-2.5 font-semibold">
                  Actor
                </th>
                <th scope="col" className="px-3 py-2.5 font-semibold">
                  Target &amp; Action
                </th>
                <th scope="col" className="px-3 py-2.5 font-semibold">
                  Outcome
                </th>
                <th
                  scope="col"
                  className="px-3 py-2.5 text-right font-semibold"
                >
                  Detail
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 bg-white dark:divide-slate-800 dark:bg-slate-950">
              {!queryResult || queryResult.items.length === 0 ? (
                <tr>
                  <td
                    colSpan={7}
                    className="px-4 py-8 text-center text-slate-500 dark:text-slate-400"
                  >
                    No security audit events match the current filter criteria.
                  </td>
                </tr>
              ) : (
                queryResult.items.map((ev) => (
                  <tr
                    key={ev.eventId}
                    className="hover:bg-slate-50/50 dark:hover:bg-slate-900/30"
                  >
                    <td className="px-3 py-2.5 font-mono font-medium text-slate-900 dark:text-slate-100">
                      #{ev.sequenceNumber}
                    </td>
                    <td className="px-3 py-2.5 text-slate-600 dark:text-slate-400">
                      {new Date(ev.timestamp).toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit',
                      })}
                      <div className="text-[10px] text-slate-400">
                        {new Date(ev.timestamp).toLocaleDateString()}
                      </div>
                    </td>
                    <td className="px-3 py-2.5">
                      <div className="font-semibold text-slate-800 dark:text-slate-200">
                        {ev.eventType}
                      </div>
                      <div className="text-[10px] text-slate-500">
                        {ev.eventCategory}
                      </div>
                    </td>
                    <td className="px-3 py-2.5">
                      <div className="font-medium text-slate-900 dark:text-slate-100">
                        {ev.actorSubject}
                      </div>
                      <div className="text-[10px] text-slate-500">
                        {ev.actorType}
                      </div>
                    </td>
                    <td className="px-3 py-2.5">
                      <div className="font-mono text-slate-800 dark:text-slate-200">
                        {ev.action}
                      </div>
                      <div className="text-[10px] text-slate-500">
                        {ev.targetType || 'SYSTEM'}:{' '}
                        {ev.targetId ? `${ev.targetId.slice(0, 12)}...` : 'N/A'}
                      </div>
                    </td>
                    <td className="px-3 py-2.5">{outcomeBadge(ev.outcome)}</td>
                    <td className="px-3 py-2.5 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setSelectedEvent(ev)}
                        aria-label={`View audit event #${ev.sequenceNumber} details`}
                      >
                        <Eye className="h-3.5 w-3.5" aria-hidden="true" />
                      </Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Server-Side Pagination Bar */}
        {queryResult && queryResult.totalPages > 1 && (
          <div className="flex flex-wrap items-center justify-between gap-4 pt-2">
            <div className="text-xs text-slate-500 dark:text-slate-400">
              Showing page <strong>{queryResult.page + 1}</strong> of{' '}
              <strong>{queryResult.totalPages}</strong> (
              <strong>{queryResult.totalElements}</strong> total events)
            </div>

            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  void fetchEvents(
                    queryResult.page - 1,
                    auditFilter.size || 20,
                  )
                }
                disabled={queryResult.page <= 0}
                aria-label="Previous audit page"
              >
                <ChevronLeft className="h-3.5 w-3.5" aria-hidden="true" />
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  void fetchEvents(
                    queryResult.page + 1,
                    auditFilter.size || 20,
                  )
                }
                disabled={queryResult.page >= queryResult.totalPages - 1}
                aria-label="Next audit page"
              >
                Next
                <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
              </Button>
            </div>
          </div>
        )}
      </CardContent>

      {/* Event Details Modal */}
      <AuditEventDetailModal
        event={selectedEvent}
        isOpen={selectedEvent !== null}
        onClose={() => setSelectedEvent(null)}
      />
    </Card>
  );
};
