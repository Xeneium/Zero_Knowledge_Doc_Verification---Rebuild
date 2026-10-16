import React, { useState, useEffect } from 'react';
import {
  ShieldAlert,
  Activity,
  Key,
  Award,
  Cpu,
} from 'lucide-react';
import { PageHeader } from '../components/layout/PageHeader';
import { Badge } from '../components/common/Badge';
import {
  SystemOverviewCard,
  KeyRegistryCard,
  CredentialOversightCard,
  PredicateInspectionCard,
} from '../features/admin';
import { adminApi } from '../api/adminApi';
import { adminStore } from '../state/adminStore';

type AdminTab = 'overview' | 'keys' | 'credentials' | 'predicates';

export const AdminDashboardPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<AdminTab>('overview');

  useEffect(() => {
    // Initial fetch of recovery status and keys on mount
    void adminApi
      .getRecoveryStatus()
      .then((data) => adminStore.setRecoveryReport(data))
      .catch(() => {});

    void adminApi
      .getKeys()
      .then((data) => adminStore.setKeys(data))
      .catch(() => {});
  }, []);

  const tabClass = (tab: AdminTab) =>
    `flex items-center gap-2 px-4 py-2.5 text-xs font-semibold rounded-md transition-colors ${
      activeTab === tab
        ? 'bg-indigo-600 text-white shadow-xs'
        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
    }`;

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <PageHeader
        title="Platform Administration & Trust Governance"
        description="Manage cryptographic key anchors, platform health reconciliation, credential lifecycle oversight, and zero-knowledge predicate parameters."
        badge={
          <Badge variant="warning">
            <ShieldAlert className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
            ADMIN PRIVILEGE REQUIRED
          </Badge>
        }
      />

      {/* Tab Navigation */}
      <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-3 dark:border-slate-800">
        <button
          type="button"
          onClick={() => setActiveTab('overview')}
          className={tabClass('overview')}
          aria-selected={activeTab === 'overview'}
          role="tab"
        >
          <Activity className="h-4 w-4" aria-hidden="true" />
          System Overview &amp; Recovery
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('keys')}
          className={tabClass('keys')}
          aria-selected={activeTab === 'keys'}
          role="tab"
        >
          <Key className="h-4 w-4" aria-hidden="true" />
          Trust Registry &amp; Keys
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('credentials')}
          className={tabClass('credentials')}
          aria-selected={activeTab === 'credentials'}
          role="tab"
        >
          <Award className="h-4 w-4" aria-hidden="true" />
          Credential Oversight
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('predicates')}
          className={tabClass('predicates')}
          aria-selected={activeTab === 'predicates'}
          role="tab"
        >
          <Cpu className="h-4 w-4" aria-hidden="true" />
          Predicate-Proof Operations
        </button>
      </div>

      {/* Tab Content Panels */}
      <div className="space-y-6">
        {activeTab === 'overview' && <SystemOverviewCard />}
        {activeTab === 'keys' && <KeyRegistryCard />}
        {activeTab === 'credentials' && <CredentialOversightCard />}
        {activeTab === 'predicates' && <PredicateInspectionCard />}
      </div>
    </div>
  );
};
