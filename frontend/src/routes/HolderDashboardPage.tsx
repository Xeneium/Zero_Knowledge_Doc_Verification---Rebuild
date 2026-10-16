import React from 'react';
import { useSearchParams } from 'react-router';
import { PageHeader } from '../components/layout/PageHeader';
import { Badge } from '../components/common/Badge';
import { Alert } from '../components/feedback/Alert';
import { useAuth } from '../state/authStore';
import {
  HolderCredentialsListCard,
  ProvisionCredentialCard,
  PredicateProofGeneratorCard,
  HolderCapabilityQrCard,
} from '../features/holder';
import { Wallet, PlusCircle, FileKey, QrCode } from 'lucide-react';

type HolderTab = 'wallet' | 'provision' | 'zkp-proof' | 'capability-qr';

export const HolderDashboardPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();

  const tabParam = searchParams.get('tab') as HolderTab;
  const activeTab: HolderTab = [
    'wallet',
    'provision',
    'zkp-proof',
    'capability-qr',
  ].includes(tabParam)
    ? tabParam
    : 'wallet';

  const idParam = searchParams.get('id') || '';

  const handleTabChange = (tab: HolderTab, id?: string) => {
    const newParams = new URLSearchParams(searchParams);
    newParams.set('tab', tab);
    if (id) {
      newParams.set('id', id);
    } else {
      newParams.delete('id');
    }
    setSearchParams(newParams);
  };

  const tabs: {
    id: HolderTab;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
  }[] = [
    { id: 'wallet', label: 'My Credentials', icon: Wallet },
    { id: 'provision', label: 'Provision Credential', icon: PlusCircle },
    { id: 'zkp-proof', label: 'Zero-Knowledge Proofs', icon: FileKey },
    { id: 'capability-qr', label: 'Capability QR Codes', icon: QrCode },
  ];

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      {/* Page Header */}
      <PageHeader
        title="Holder Credential Wallet &amp; Privacy Engine"
        description="Access and manage credentials, generate zero-knowledge predicate proofs, and mint offline verification capabilities."
        badge={<Badge variant="success">ROLE_HOLDER ACTIVE</Badge>}
        actions={
          <span className="font-mono text-xs text-slate-600 dark:text-slate-400">
            {user?.subject || 'Authenticated Holder'}
          </span>
        }
      />

      {/* Minimal Disclosure Banner */}
      <Alert
        variant="info"
        title="Zero-Knowledge Privacy &amp; Cryptographic Witness Protection"
      >
        Your credentials use Pedersen commitments and Bulletproof zero-knowledge
        range proofs. Raw attributes, blinding factors, and private witness
        secrets remain strictly inside the cryptographic enclave and are never
        exposed to browser memory.
      </Alert>

      {/* Accessible Navigation Tablist */}
      <div
        className="flex overflow-x-auto border-b border-slate-200 dark:border-slate-800"
        role="tablist"
        aria-label="Holder Operations Navigation"
      >
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              role="tab"
              id={`tab-${tab.id}`}
              aria-selected={isActive}
              aria-controls={`tab-panel-${tab.id}`}
              onClick={() => handleTabChange(tab.id)}
              className={`flex min-h-[44px] items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none ${
                isActive
                  ? 'border-indigo-600 font-semibold text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                  : 'border-transparent text-slate-600 hover:border-slate-300 hover:text-slate-900 dark:text-slate-400 dark:hover:border-slate-700 dark:hover:text-slate-200'
              }`}
            >
              <Icon className="h-4 w-4" />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Tab Panels */}
      {/* 1. Wallet / My Credentials */}
      {activeTab === 'wallet' && (
        <div
          id="tab-panel-wallet"
          role="tabpanel"
          aria-labelledby="tab-wallet"
          className="space-y-6"
        >
          <HolderCredentialsListCard
            onNavigateToProvision={() => handleTabChange('provision')}
            onNavigateToProof={(holderCredId) =>
              handleTabChange('zkp-proof', holderCredId)
            }
            onNavigateToCapability={(credId) =>
              handleTabChange('capability-qr', credId)
            }
          />
        </div>
      )}

      {/* 2. Provision Credential */}
      {activeTab === 'provision' && (
        <div
          id="tab-panel-provision"
          role="tabpanel"
          aria-labelledby="tab-provision"
          className="space-y-6"
        >
          <ProvisionCredentialCard
            onSuccess={() => {
              handleTabChange('wallet');
            }}
          />
        </div>
      )}

      {/* 3. Zero-Knowledge Predicate Proofs */}
      {activeTab === 'zkp-proof' && (
        <div
          id="tab-panel-zkp-proof"
          role="tabpanel"
          aria-labelledby="tab-zkp-proof"
          className="space-y-6"
        >
          <PredicateProofGeneratorCard initialHolderCredentialId={idParam} />
        </div>
      )}

      {/* 4. Verification Capability & QR Codes */}
      {activeTab === 'capability-qr' && (
        <div
          id="tab-panel-capability-qr"
          role="tabpanel"
          aria-labelledby="tab-capability-qr"
          className="space-y-6"
        >
          <HolderCapabilityQrCard initialCredentialId={idParam} />
        </div>
      )}
    </div>
  );
};
