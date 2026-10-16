import React from 'react';
import { ShieldCheck } from 'lucide-react';
import { PageHeader } from '../components/layout/PageHeader';
import { Badge } from '../components/common/Badge';
import { AuditIntegrityCard, AuditEventsListCard } from '../features/audit';

export const AuditLogsPage: React.FC = () => {
  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <PageHeader
        title="Platform Security Audit Logs & Hash Chain"
        description="Continuous cryptographic SHA-256 audit trail providing tamper-evident logging for authentication, credential issuance, and administration."
        badge={
          <Badge variant="info">
            <ShieldCheck className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
            AUTHORITATIVE AUDIT TRAIL
          </Badge>
        }
      />

      <div className="space-y-6">
        <AuditIntegrityCard />
        <AuditEventsListCard />
      </div>
    </div>
  );
};
