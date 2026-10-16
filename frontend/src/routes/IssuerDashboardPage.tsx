import React from 'react';
import { useSearchParams } from 'react-router';
import {
  UploadCloud,
  Award,
  Activity,
  FileCheck2,
  LayoutDashboard,
  ArrowRight,
} from 'lucide-react';
import { PageHeader } from '../components/layout/PageHeader';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '../components/common/Card';
import { Badge } from '../components/common/Badge';
import { Alert } from '../components/feedback/Alert';
import { useAuth } from '../state/authStore';
import {
  DocumentUploadCard,
  CredentialIssuanceCard,
  CredentialLifecycleCard,
  DocumentVerifyQrCard,
  IssuerActivityTable,
} from '../features/issuer';

type IssuerTab = 'overview' | 'upload' | 'issue' | 'lifecycle' | 'verify-qr';

export const IssuerDashboardPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();

  const tabParam = searchParams.get('tab') as IssuerTab;
  const activeTab: IssuerTab = [
    'overview',
    'upload',
    'issue',
    'lifecycle',
    'verify-qr',
  ].includes(tabParam)
    ? tabParam
    : 'overview';

  const idParam = searchParams.get('id') || '';
  const selectedCredentialId = activeTab === 'lifecycle' ? idParam : '';
  const selectedDocumentId = activeTab === 'verify-qr' ? idParam : '';

  const handleTabChange = (tab: IssuerTab, id?: string) => {
    const newParams = new URLSearchParams(searchParams);
    newParams.set('tab', tab);
    if (id) {
      newParams.set('id', id);
    } else {
      newParams.delete('id');
    }
    setSearchParams(newParams);
  };

  const handleSelectItem = (id: string, type: 'document' | 'credential') => {
    if (type === 'credential') {
      handleTabChange('lifecycle', id);
    } else {
      handleTabChange('verify-qr', id);
    }
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      {/* Page Header */}
      <PageHeader
        title="Issuer Operations Workspace"
        description="Secure document uploading, multi-attribute credential issuance, and lifecycle management."
        badge={<Badge variant="success">ROLE_ISSUER ACTIVE</Badge>}
        actions={
          <span className="font-mono text-xs text-slate-600 dark:text-slate-400">
            {user?.subject || 'Authenticated Authority'}
          </span>
        }
      />

      {/* Zero Trust & Minimal Disclosure Banner */}
      <Alert
        variant="info"
        title="Cryptographic Integrity & Zero Mock Architecture"
      >
        All documents uploaded receive an authoritative SHA-256 integrity hash
        signed with RSA-PSS by the platform key authority. The backend strictly
        validates file magic bytes and rejects unauthorized issuer
        impersonation.
      </Alert>

      {/* Accessible Navigation Tablist */}
      <div
        className="flex overflow-x-auto border-b border-slate-200 dark:border-slate-800"
        role="tablist"
        aria-label="Issuer Operations Navigation"
      >
        <button
          role="tab"
          aria-selected={activeTab === 'overview'}
          aria-controls="tab-panel-overview"
          id="tab-overview"
          onClick={() => handleTabChange('overview')}
          className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors ${
            activeTab === 'overview'
              ? 'border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300'
          }`}
        >
          <LayoutDashboard className="h-4 w-4" />
          Overview
        </button>

        <button
          role="tab"
          aria-selected={activeTab === 'upload'}
          aria-controls="tab-panel-upload"
          id="tab-upload"
          onClick={() => handleTabChange('upload')}
          className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors ${
            activeTab === 'upload'
              ? 'border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300'
          }`}
        >
          <UploadCloud className="h-4 w-4" />
          Document Upload
        </button>

        <button
          role="tab"
          aria-selected={activeTab === 'issue'}
          aria-controls="tab-panel-issue"
          id="tab-issue"
          onClick={() => handleTabChange('issue')}
          className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors ${
            activeTab === 'issue'
              ? 'border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300'
          }`}
        >
          <Award className="h-4 w-4" />
          Issue Credential
        </button>

        <button
          role="tab"
          aria-selected={activeTab === 'lifecycle'}
          aria-controls="tab-panel-lifecycle"
          id="tab-lifecycle"
          onClick={() => handleTabChange('lifecycle')}
          className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors ${
            activeTab === 'lifecycle'
              ? 'border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300'
          }`}
        >
          <Activity className="h-4 w-4" />
          Lifecycle &amp; State
        </button>

        <button
          role="tab"
          aria-selected={activeTab === 'verify-qr'}
          aria-controls="tab-panel-verify-qr"
          id="tab-verify-qr"
          onClick={() => handleTabChange('verify-qr')}
          className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors ${
            activeTab === 'verify-qr'
              ? 'border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300'
          }`}
        >
          <FileCheck2 className="h-4 w-4" />
          Verification &amp; QR
        </button>
      </div>

      {/* Tab Panels */}
      {/* 1. Overview */}
      {activeTab === 'overview' && (
        <div
          id="tab-panel-overview"
          role="tabpanel"
          aria-labelledby="tab-overview"
          className="space-y-6"
        >
          {/* Action Launchpad Cards */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card
              className="cursor-pointer transition-shadow hover:shadow-md"
              onClick={() => handleTabChange('upload')}
            >
              <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400">
                <UploadCloud className="h-5 w-5" />
              </div>
              <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                Secure Upload
              </h4>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Upload authentic documents up to 25 MB with SHA-256
                fingerprinting.
              </p>
              <div className="mt-4 flex items-center text-xs font-medium text-indigo-600 dark:text-indigo-400">
                Start Upload <ArrowRight className="ml-1 h-3.5 w-3.5" />
              </div>
            </Card>

            <Card
              className="cursor-pointer transition-shadow hover:shadow-md"
              onClick={() => handleTabChange('issue')}
            >
              <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-400">
                <Award className="h-5 w-5" />
              </div>
              <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                Issue Credential
              </h4>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Issue signed credentials with multi-attribute Pedersen
                commitments.
              </p>
              <div className="mt-4 flex items-center text-xs font-medium text-emerald-600 dark:text-emerald-400">
                Issue New <ArrowRight className="ml-1 h-3.5 w-3.5" />
              </div>
            </Card>

            <Card
              className="cursor-pointer transition-shadow hover:shadow-md"
              onClick={() => handleTabChange('lifecycle')}
            >
              <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/50 dark:text-sky-400">
                <Activity className="h-5 w-5" />
              </div>
              <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                Lifecycle &amp; State
              </h4>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Manage states: Suspend, Resume, Revoke, and view audit history.
              </p>
              <div className="mt-4 flex items-center text-xs font-medium text-sky-600 dark:text-sky-400">
                Manage Lifecycle <ArrowRight className="ml-1 h-3.5 w-3.5" />
              </div>
            </Card>

            <Card
              className="cursor-pointer transition-shadow hover:shadow-md"
              onClick={() => handleTabChange('verify-qr')}
            >
              <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-purple-50 text-purple-600 dark:bg-purple-950/50 dark:text-purple-400">
                <FileCheck2 className="h-5 w-5" />
              </div>
              <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                Verify &amp; QR
              </h4>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Verify server copy, uploaded copies, and mint capability QR
                codes.
              </p>
              <div className="mt-4 flex items-center text-xs font-medium text-purple-600 dark:text-purple-400">
                Verify Document <ArrowRight className="ml-1 h-3.5 w-3.5" />
              </div>
            </Card>
          </div>

          {/* Session Activity Ledger */}
          <Card>
            <CardHeader>
              <CardTitle>Session Activity Ledger</CardTitle>
              <CardDescription>
                Authoritative record of documents uploaded and credentials
                issued during this browser session.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <IssuerActivityTable onSelectItem={handleSelectItem} />
            </CardContent>
          </Card>
        </div>
      )}

      {/* 2. Document Upload */}
      {activeTab === 'upload' && (
        <div
          id="tab-panel-upload"
          role="tabpanel"
          aria-labelledby="tab-upload"
          className="space-y-6"
        >
          <DocumentUploadCard
            onSuccess={(res) => {
              handleTabChange('verify-qr', res.id);
            }}
          />
        </div>
      )}

      {/* 3. Issue Credential */}
      {activeTab === 'issue' && (
        <div
          id="tab-panel-issue"
          role="tabpanel"
          aria-labelledby="tab-issue"
          className="space-y-6"
        >
          <CredentialIssuanceCard
            onNavigateToLifecycle={(id) => {
              handleTabChange('lifecycle', id);
            }}
          />
        </div>
      )}

      {/* 4. Lifecycle & State Management */}
      {activeTab === 'lifecycle' && (
        <div
          id="tab-panel-lifecycle"
          role="tabpanel"
          aria-labelledby="tab-lifecycle"
          className="space-y-6"
        >
          <CredentialLifecycleCard initialCredentialId={selectedCredentialId} />
        </div>
      )}

      {/* 5. Document Verification & QR */}
      {activeTab === 'verify-qr' && (
        <div
          id="tab-panel-verify-qr"
          role="tabpanel"
          aria-labelledby="tab-verify-qr"
          className="space-y-6"
        >
          <DocumentVerifyQrCard initialDocumentId={selectedDocumentId} />
        </div>
      )}
    </div>
  );
};
