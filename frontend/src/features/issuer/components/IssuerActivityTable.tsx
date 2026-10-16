import React, { useState } from 'react';
import { FileText, Award, Trash2, ExternalLink, Clock } from 'lucide-react';
import { Button } from '../../../components/common/Button';
import { Badge } from '../../../components/common/Badge';
import { FormattedHash } from '../../../components/data/FormattedHash';
import { CopyToClipboard } from '../../../components/data/CopyToClipboard';
import { EmptyState } from '../../../components/feedback/EmptyState';
import { issuerStore, useIssuerActivities } from '../../../state/issuerStore';

export interface IssuerActivityTableProps {
  onSelectItem?: (id: string, type: 'document' | 'credential') => void;
}

export const IssuerActivityTable: React.FC<IssuerActivityTableProps> = ({
  onSelectItem,
}) => {
  const activities = useIssuerActivities();
  const clearActivity = () => issuerStore.clearActivity();

  const [filterType, setFilterType] = useState<
    'all' | 'document' | 'credential'
  >('all');

  const filteredActivities = activities.filter((item) => {
    if (filterType === 'all') return true;
    return item.type === filterType;
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <Button
            type="button"
            size="sm"
            variant={filterType === 'all' ? 'primary' : 'outline'}
            onClick={() => setFilterType('all')}
          >
            All Activity ({activities.length})
          </Button>
          <Button
            type="button"
            size="sm"
            variant={filterType === 'document' ? 'primary' : 'outline'}
            onClick={() => setFilterType('document')}
          >
            Documents ({activities.filter((a) => a.type === 'document').length})
          </Button>
          <Button
            type="button"
            size="sm"
            variant={filterType === 'credential' ? 'primary' : 'outline'}
            onClick={() => setFilterType('credential')}
          >
            Credentials (
            {activities.filter((a) => a.type === 'credential').length})
          </Button>
        </div>

        {activities.length > 0 && (
          <Button
            type="button"
            size="sm"
            variant="ghost"
            onClick={clearActivity}
            className="text-slate-500 hover:text-red-600"
          >
            <Trash2 className="mr-1.5 h-3.5 w-3.5" />
            Clear Session Log
          </Button>
        )}
      </div>

      {filteredActivities.length === 0 ? (
        <EmptyState
          title="No Recent Issuer Activity in this Session"
          description="Documents uploaded and credentials issued during this session will be listed here for quick inspection and lifecycle management."
          icon={
            <Clock className="h-8 w-8 text-slate-400 dark:text-slate-500" />
          }
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
          <table className="w-full text-left text-xs">
            <thead className="border-b border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-300">
              <tr>
                <th className="p-3">Type</th>
                <th className="p-3">Identifier / Title</th>
                <th className="p-3">SHA-256 Digest</th>
                <th className="p-3">Status</th>
                <th className="p-3">Timestamp</th>
                <th className="p-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
              {filteredActivities.map((item) => (
                <tr
                  key={item.id}
                  className="transition-colors hover:bg-slate-50/50 dark:hover:bg-slate-800/40"
                >
                  <td className="p-3">
                    {item.type === 'document' ? (
                      <span className="inline-flex items-center gap-1 rounded bg-indigo-50 px-2 py-0.5 font-medium text-indigo-700 dark:bg-indigo-950/40 dark:text-indigo-300">
                        <FileText className="h-3 w-3" />
                        Document
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded bg-emerald-50 px-2 py-0.5 font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300">
                        <Award className="h-3 w-3" />
                        Credential
                      </span>
                    )}
                  </td>

                  <td className="p-3">
                    <div className="flex flex-col">
                      <span className="font-semibold text-slate-900 dark:text-slate-100">
                        {item.title}
                      </span>
                      <div className="flex items-center gap-1 font-mono text-[11px] text-slate-500">
                        <span>{item.id}</span>
                        <CopyToClipboard value={item.id} label="Copy ID" />
                      </div>
                    </div>
                  </td>

                  <td className="p-3 font-mono">
                    <FormattedHash value={item.digest} />
                  </td>

                  <td className="p-3">
                    <Badge
                      variant={
                        item.status === 'ACTIVE' ||
                        item.status === 'STORED & SIGNED'
                          ? 'success'
                          : item.status === 'SUSPENDED'
                            ? 'warning'
                            : item.status === 'REVOKED'
                              ? 'danger'
                              : 'neutral'
                      }
                    >
                      {item.status || 'RECORDED'}
                    </Badge>
                  </td>

                  <td className="p-3 text-slate-500 dark:text-slate-400">
                    {new Date(item.timestamp).toLocaleTimeString([], {
                      hour: '2-digit',
                      minute: '2-digit',
                      second: '2-digit',
                    })}
                  </td>

                  <td className="p-3 text-right">
                    {onSelectItem && (
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => onSelectItem(item.id, item.type)}
                      >
                        Inspect
                        <ExternalLink className="ml-1 h-3.5 w-3.5" />
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
