import React, { useState } from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { TextArea } from '../../../components/common/TextArea';
import { Alert } from '../../../components/feedback/Alert';
import { Badge } from '../../../components/common/Badge';
import {
  parseQrPayload,
  type ParsedQrPayload,
} from '../../../lib/utils/qrPayload';
import { Search, CheckCircle2 } from 'lucide-react';

interface ManualReferenceCardProps {
  onVerify: (payload: ParsedQrPayload) => void;
  disabled?: boolean;
  isLoading?: boolean;
}

export const ManualReferenceCard: React.FC<ManualReferenceCardProps> = ({
  onVerify,
  disabled = false,
  isLoading = false,
}) => {
  const [inputVal, setInputVal] = useState('');
  const [error, setError] = useState<string | null>(null);

  const parsed = inputVal.trim() ? parseQrPayload(inputVal) : null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading || disabled) return;
    setError(null);

    const parseResult = parseQrPayload(inputVal);
    if (!parseResult.success) {
      setError(parseResult.error);
      return;
    }

    onVerify(parseResult.payload);
  };

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg text-slate-900">
          <Search className="h-5 w-5 text-indigo-600" aria-hidden="true" />
          Manual Verification Reference Entry
        </CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <p className="text-sm text-slate-600">
            Paste a verification capability token, canonical QR JSON payload, or
            direct verification URI to evaluate authenticity against the
            cryptographic registry.
          </p>

          {error && (
            <Alert variant="danger" title="Validation Error">
              {error}
            </Alert>
          )}

          <div>
            <label
              htmlFor="reference-input"
              className="mb-1 block text-sm font-medium text-slate-700"
            >
              Verification Reference or Payload
            </label>
            <TextArea
              id="reference-input"
              value={inputVal}
              onChange={(e) => {
                setInputVal(e.target.value);
                if (error) setError(null);
              }}
              placeholder='e.g. {"v":"QR_V1","m":"MODE_B_REGISTRY_CAPABILITY","tok":"..."} or https://.../api/public/verify/<token> or opaque token'
              rows={3}
              disabled={disabled || isLoading}
              className="font-mono text-xs"
            />
          </div>

          {/* Format preview banner when input is detected */}
          {parsed?.success && (
            <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-2.5 text-xs text-emerald-800">
              <CheckCircle2
                className="h-4 w-4 shrink-0 text-emerald-600"
                aria-hidden="true"
              />
              <span>
                Detected format: <strong>{parsed.payload.format}</strong>
                {parsed.payload.mode && ` (${parsed.payload.mode})`}
              </span>
              <Badge variant="success" className="ml-auto text-[10px]">
                Valid Syntax
              </Badge>
            </div>
          )}

          <div className="flex justify-end gap-3">
            <Button
              type="button"
              variant="outline"
              disabled={!inputVal || disabled || isLoading}
              onClick={() => {
                setInputVal('');
                setError(null);
              }}
            >
              Clear
            </Button>
            <Button
              type="submit"
              variant="primary"
              disabled={!inputVal.trim() || disabled || isLoading}
              className="flex items-center gap-2"
            >
              <Search className="h-4 w-4" aria-hidden="true" />
              {isLoading ? 'Verifying...' : 'Verify Reference'}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
};
