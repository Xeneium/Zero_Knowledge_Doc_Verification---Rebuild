import React from 'react';
import { FileText, Link, Shield, Clock } from 'lucide-react';
import { Dialog } from '../../../components/feedback/Dialog';
import { Button } from '../../../components/common/Button';
import { Badge } from '../../../components/common/Badge';
import type { SecurityAuditEventResponse } from '../../../api/types';

interface AuditEventDetailModalProps {
  event: SecurityAuditEventResponse | null;
  isOpen: boolean;
  onClose: () => void;
}

export const AuditEventDetailModal: React.FC<AuditEventDetailModalProps> = ({
  event,
  isOpen,
  onClose,
}) => {
  if (!event) return null;

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

  let formattedMetadata = event.metadataJson;
  if (event.metadataJson) {
    try {
      const parsed: unknown = JSON.parse(event.metadataJson);
      formattedMetadata = JSON.stringify(parsed, null, 2);
    } catch {
      // Keep as is
    }
  }

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={
        <div className="flex items-center gap-2">
          <FileText
            className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
            aria-hidden="true"
          />
          <span>Security Audit Event #{event.sequenceNumber}</span>
        </div>
      }
      footer={
        <Button variant="outline" onClick={onClose}>
          Close
        </Button>
      }
    >
      <div className="space-y-4 text-xs">
        {/* Core Event Header */}
        <div className="grid grid-cols-2 gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/50">
          <div>
            <span className="font-semibold text-slate-700 dark:text-slate-300">
              Event Type:
            </span>
            <div className="mt-0.5 font-mono text-slate-900 dark:text-slate-100">
              {event.eventType}
            </div>
          </div>
          <div>
            <span className="font-semibold text-slate-700 dark:text-slate-300">
              Category:
            </span>
            <div className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
              {event.eventCategory}
            </div>
          </div>
          <div>
            <span className="font-semibold text-slate-700 dark:text-slate-300">
              Outcome:
            </span>
            <div className="mt-0.5">{outcomeBadge(event.outcome)}</div>
          </div>
          <div>
            <span className="font-semibold text-slate-700 dark:text-slate-300">
              Timestamp:
            </span>
            <div className="mt-0.5 flex items-center gap-1 text-slate-600 dark:text-slate-400">
              <Clock className="h-3 w-3" aria-hidden="true" />
              <span>{new Date(event.timestamp).toLocaleString()}</span>
            </div>
          </div>
        </div>

        {/* Actor & Target */}
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-md border border-slate-200 p-2.5 dark:border-slate-800">
            <span className="font-semibold text-slate-700 dark:text-slate-300">
              Actor Principal:
            </span>
            <div className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
              {event.actorSubject} ({event.actorType})
            </div>
          </div>
          <div className="rounded-md border border-slate-200 p-2.5 dark:border-slate-800">
            <span className="font-semibold text-slate-700 dark:text-slate-300">
              Target Entity:
            </span>
            <div className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
              {event.targetType || 'SYSTEM'}: {event.targetId || 'N/A'}
            </div>
          </div>
        </div>

        {/* Action & Reason */}
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-md border border-slate-200 p-2.5 dark:border-slate-800">
            <span className="font-semibold text-slate-700 dark:text-slate-300">
              Action Code:
            </span>
            <div className="mt-0.5 font-mono text-slate-900 dark:text-slate-100">
              {event.action}
            </div>
          </div>
          <div className="rounded-md border border-slate-200 p-2.5 dark:border-slate-800">
            <span className="font-semibold text-slate-700 dark:text-slate-300">
              Reason / Status:
            </span>
            <div className="mt-0.5 font-mono text-slate-900 dark:text-slate-100">
              {event.reasonCode || 'N/A'}
            </div>
          </div>
        </div>

        {/* Tracking & Privacy Pseudonyms */}
        <div className="rounded-md border border-slate-200 p-2.5 dark:border-slate-800">
          <div className="grid grid-cols-2 gap-2 text-[11px]">
            <div>
              <span className="font-semibold text-slate-600 dark:text-slate-400">
                Correlation ID:
              </span>
              <div className="font-mono text-slate-800 dark:text-slate-200">
                {event.correlationId || 'N/A'}
              </div>
            </div>
            <div>
              <span className="font-semibold text-slate-600 dark:text-slate-400">
                Client IP Hash (Pseudonymized):
              </span>
              <div className="font-mono text-slate-800 dark:text-slate-200">
                {event.clientIpHash || 'N/A'}
              </div>
            </div>
          </div>
        </div>

        {/* Cryptographic SHA-256 Hash Chain Linkage */}
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/50">
          <div className="flex items-center gap-1.5 font-semibold text-slate-800 dark:text-slate-200">
            <Shield className="h-3.5 w-3.5 text-indigo-600" aria-hidden="true" />
            <span>Cryptographic Chain Linkage</span>
          </div>

          <div className="mt-2 space-y-2 font-mono text-[11px]">
            <div>
              <span className="text-slate-500">Current Event SHA-256 Hash:</span>
              <div className="text-emerald-700 break-all select-all dark:text-emerald-400">
                {event.eventHash}
              </div>
            </div>
            <div className="flex items-center gap-1 text-slate-400">
              <Link className="h-3 w-3" aria-hidden="true" />
              <span>Linked to Previous Hash</span>
            </div>
            <div>
              <span className="text-slate-500">Previous Event SHA-256 Hash:</span>
              <div className="text-slate-700 break-all select-all dark:text-slate-300">
                {event.previousEventHash}
              </div>
            </div>
          </div>
        </div>

        {/* Event Metadata JSON */}
        {formattedMetadata && (
          <div>
            <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
              Event Context Metadata
            </label>
            <pre className="mt-1 max-h-40 overflow-x-auto rounded-md border border-slate-200 bg-slate-950 p-2.5 font-mono text-[11px] text-slate-300 select-all dark:border-slate-800">
              {formattedMetadata}
            </pre>
          </div>
        )}
      </div>
    </Dialog>
  );
};
