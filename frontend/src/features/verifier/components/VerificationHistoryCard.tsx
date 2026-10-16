import React from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Badge } from '../../../components/common/Badge';
import { FormattedHash } from '../../../components/data/FormattedHash';
import {
  useVerificationHistory,
  verifierStore,
  type UnifiedVerificationResult,
} from '../../../state/verifierStore';
import { History, Trash2, ArrowUpRight } from 'lucide-react';

interface VerificationHistoryCardProps {
  onSelectResult: (result: UnifiedVerificationResult) => void;
}

export const VerificationHistoryCard: React.FC<
  VerificationHistoryCardProps
> = ({ onSelectResult }) => {
  const history = useVerificationHistory();

  if (history.length === 0) {
    return null;
  }

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-base text-slate-900">
            <History className="h-4 w-4 text-slate-500" />
            Session Verification History ({history.length})
          </CardTitle>
          <Button
            variant="outline"
            size="sm"
            onClick={() => verifierStore.clearHistory()}
            className="flex items-center gap-1.5 text-xs text-rose-600 hover:bg-rose-50 hover:text-rose-700"
          >
            <Trash2 className="h-3.5 w-3.5" />
            Clear Log
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        <div className="divide-y divide-slate-100 overflow-hidden rounded-lg border border-slate-200 bg-white">
          {history.map((item, idx) => {
            const isCap = item.kind === 'PUBLIC_CAPABILITY';
            const isDoc = item.kind === 'PUBLIC_DOCUMENT_TOKEN';
            const isUpload = item.kind === 'DOCUMENT_UPLOAD';
            const isCred = item.kind === 'CREDENTIAL_SIGNATURE';
            const isZkp = item.kind === 'PREDICATE_PROOF';

            let isValid = false;
            let target = '';

            if (isCap) {
              isValid = item.response.verified;
              target = item.token;
            } else if (isDoc) {
              isValid =
                item.response.signatureValid && item.response.documentActive;
              target = item.token;
            } else if (isUpload) {
              isValid = item.response.verified;
              target = item.fileName;
            } else if (isCred) {
              isValid = item.response.verified;
              target = item.credentialId;
            } else if (isZkp) {
              isValid = item.response.verified;
              target = `${item.attributeName} >= ${item.threshold}`;
            }

            return (
              <div
                key={`${item.kind}-${target}-${idx}`}
                className="flex items-center justify-between p-3 transition-colors hover:bg-slate-50/80"
              >
                <div className="flex items-center gap-3">
                  <Badge variant={isValid ? 'success' : 'danger'}>
                    {isValid ? 'VALID' : 'INVALID'}
                  </Badge>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-xs font-semibold text-slate-800">
                        {isUpload ? (
                          target
                        ) : isZkp ? (
                          target
                        ) : (
                          <FormattedHash
                            value={target}
                            prefixLength={6}
                            suffixLength={6}
                          />
                        )}
                      </span>
                      <Badge variant="neutral" className="text-[10px]">
                        {item.kind.replace('_', ' ')}
                      </Badge>
                    </div>
                    <span className="text-[11px] text-slate-400">
                      {new Date(item.timestamp).toLocaleTimeString()}
                    </span>
                  </div>
                </div>

                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => onSelectResult(item)}
                  className="flex items-center gap-1 text-xs"
                >
                  Inspect
                  <ArrowUpRight className="h-3.5 w-3.5" />
                </Button>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
};
