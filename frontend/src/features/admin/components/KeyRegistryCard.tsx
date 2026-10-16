import React, { useState } from 'react';
import {
  Key,
  KeyRound,
  RefreshCw,
  Eye,
  SlidersHorizontal,
  RotateCw,
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
import { Alert } from '../../../components/feedback/Alert';
import { adminApi } from '../../../api/adminApi';
import { adminStore, useAdminKeys, useAdminStore } from '../../../state/adminStore';
import type { KeyMetadataResponse } from '../../../api/types';
import { RegisterKeyModal } from './RegisterKeyModal';
import { KeyStatusModal } from './KeyStatusModal';
import { KeyRotateModal } from './KeyRotateModal';
import { KeyPemModal } from './KeyPemModal';

export const KeyRegistryCard: React.FC = () => {
  const keys = useAdminKeys();
  const { isLoadingKeys, keysError } = useAdminStore();

  const [isRegisterModalOpen, setIsRegisterModalOpen] = useState(false);
  const [selectedKeyForStatus, setSelectedKeyForStatus] =
    useState<KeyMetadataResponse | null>(null);
  const [selectedKeyForRotate, setSelectedKeyForRotate] =
    useState<KeyMetadataResponse | null>(null);
  const [selectedKeyForPem, setSelectedKeyForPem] =
    useState<KeyMetadataResponse | null>(null);

  const fetchKeys = async () => {
    adminStore.setLoadingKeys(true);
    try {
      const data = await adminApi.getKeys();
      adminStore.setKeys(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch keys';
      adminStore.setKeysError(msg);
    } finally {
      adminStore.setLoadingKeys(false);
    }
  };

  const statusVariant = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return 'success' as const;
      case 'RETIRING':
        return 'warning' as const;
      case 'RETIRED':
        return 'neutral' as const;
      case 'REVOKED':
        return 'danger' as const;
      default:
        return 'neutral' as const;
    }
  };

  const purposeBadge = (purpose: string) => {
    switch (purpose) {
      case 'CREDENTIAL_SIGNING':
        return <Badge variant="info">Credential</Badge>;
      case 'DOCUMENT_SIGNING':
        return <Badge variant="neutral">Document</Badge>;
      case 'COMMITMENT_ATTESTATION_SIGNING':
        return <Badge variant="warning">Commitment / ZKP</Badge>;
      case 'JWT_SIGNING':
        return <Badge variant="success">JWT Bearer</Badge>;
      default:
        return <Badge variant="neutral">{purpose}</Badge>;
    }
  };

  return (
    <Card className="shadow-xs">
      <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Key
              className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
              aria-hidden="true"
            />
            <CardTitle>Cryptographic Trust Registry & Public Keys</CardTitle>
          </div>
          <CardDescription>
            Authoritative public-key trust anchors for digital signature
            verification and zero-knowledge commitment attestations.
          </CardDescription>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void fetchKeys()}
            isLoading={isLoadingKeys}
            aria-label="Refresh key registry list"
          >
            <RefreshCw className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
            Refresh
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsRegisterModalOpen(true)}
            aria-label="Register new public key anchor"
          >
            <KeyRound className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
            Register Key
          </Button>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {keysError && (
          <Alert variant="danger" title="Key Registry Error">
            {keysError}
          </Alert>
        )}

        <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
          <table className="min-w-full divide-y divide-slate-200 text-left text-xs dark:divide-slate-800">
            <thead className="bg-slate-50 text-slate-700 dark:bg-slate-900/60 dark:text-slate-300">
              <tr>
                <th scope="col" className="px-4 py-3 font-semibold">
                  Key ID &amp; Owner
                </th>
                <th scope="col" className="px-4 py-3 font-semibold">
                  Purpose
                </th>
                <th scope="col" className="px-4 py-3 font-semibold">
                  Status
                </th>
                <th scope="col" className="px-4 py-3 font-semibold">
                  Algorithm
                </th>
                <th scope="col" className="px-4 py-3 font-semibold">
                  Fingerprint
                </th>
                <th scope="col" className="px-4 py-3 text-right font-semibold">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 bg-white dark:divide-slate-800 dark:bg-slate-950">
              {keys.length === 0 ? (
                <tr>
                  <td
                    colSpan={6}
                    className="px-4 py-8 text-center text-slate-500 dark:text-slate-400"
                  >
                    No cryptographic keys loaded. Click &quot;Refresh&quot; to fetch
                    the current trust registry state.
                  </td>
                </tr>
              ) : (
                keys.map((k) => (
                  <tr
                    key={k.keyId}
                    className="hover:bg-slate-50/50 dark:hover:bg-slate-900/30"
                  >
                    <td className="px-4 py-3">
                      <div className="font-semibold text-slate-900 dark:text-slate-100">
                        {k.keyId}
                      </div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400">
                        {k.ownerId} ({k.ownerType})
                      </div>
                    </td>
                    <td className="px-4 py-3">{purposeBadge(k.purpose)}</td>
                    <td className="px-4 py-3">
                      <Badge variant={statusVariant(k.status)}>{k.status}</Badge>
                      {k.replacementKeyId && (
                        <div className="mt-0.5 text-[10px] text-slate-500">
                          Next: {k.replacementKeyId}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3 font-mono text-slate-600 dark:text-slate-300">
                      {k.algorithm}
                    </td>
                    <td className="px-4 py-3 font-mono text-[11px] text-slate-500">
                      {k.fingerprint ? `${k.fingerprint.slice(0, 16)}...` : 'N/A'}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setSelectedKeyForPem(k)}
                          title="View Public Key PEM"
                          aria-label={`View PEM for ${k.keyId}`}
                        >
                          <Eye className="h-3.5 w-3.5" aria-hidden="true" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setSelectedKeyForStatus(k)}
                          title="Update Status"
                          aria-label={`Update status for ${k.keyId}`}
                          disabled={k.status === 'REVOKED'}
                        >
                          <SlidersHorizontal
                            className="h-3.5 w-3.5"
                            aria-hidden="true"
                          />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setSelectedKeyForRotate(k)}
                          title="Rotate Key"
                          aria-label={`Rotate key ${k.keyId}`}
                          disabled={k.status === 'REVOKED' || k.status === 'RETIRED'}
                        >
                          <RotateCw className="h-3.5 w-3.5" aria-hidden="true" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </CardContent>

      {/* Modals */}
      <RegisterKeyModal
        isOpen={isRegisterModalOpen}
        onClose={() => setIsRegisterModalOpen(false)}
        onSuccess={() => void fetchKeys()}
      />

      <KeyStatusModal
        keyMetadata={selectedKeyForStatus}
        isOpen={selectedKeyForStatus !== null}
        onClose={() => setSelectedKeyForStatus(null)}
        onSuccess={() => void fetchKeys()}
      />

      <KeyRotateModal
        keyMetadata={selectedKeyForRotate}
        isOpen={selectedKeyForRotate !== null}
        onClose={() => setSelectedKeyForRotate(null)}
        onSuccess={() => void fetchKeys()}
      />

      <KeyPemModal
        keyMetadata={selectedKeyForPem}
        isOpen={selectedKeyForPem !== null}
        onClose={() => setSelectedKeyForPem(null)}
      />
    </Card>
  );
};
