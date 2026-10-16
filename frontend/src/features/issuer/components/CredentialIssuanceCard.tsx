import React, { useState } from 'react';
import {
  Award,
  Plus,
  Trash2,
  ShieldCheck,
  Eye,
  ArrowRight,
  AlertCircle,
  Sparkles,
} from 'lucide-react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';
import { Select } from '../../../components/common/Select';
import { Alert } from '../../../components/feedback/Alert';
import { Dialog } from '../../../components/feedback/Dialog';
import { Badge } from '../../../components/common/Badge';
import { FormattedHash } from '../../../components/data/FormattedHash';
import { CopyToClipboard } from '../../../components/data/CopyToClipboard';
import { issuerApi } from '../../../api/issuerApi';
import { authStore } from '../../../state/authStore';
import { issuerStore } from '../../../state/issuerStore';
import type {
  AttributeRequestDto,
  IssueCredentialRequest,
  IssueCredentialResponse,
} from '../../../api/types';

export interface CredentialIssuanceCardProps {
  onSuccess?: (response: IssueCredentialResponse) => void;
  onNavigateToLifecycle?: (credentialId: string) => void;
}

const ATTRIBUTE_TYPES = [
  { value: 'STRING', label: 'String (Text)' },
  { value: 'INTEGER', label: 'Integer (Whole Number)' },
  { value: 'DATE', label: 'Date (ISO-8601)' },
  { value: 'BOOLEAN', label: 'Boolean (true/false)' },
];

export const CredentialIssuanceCard: React.FC<CredentialIssuanceCardProps> = ({
  onSuccess,
  onNavigateToLifecycle,
}) => {
  const user = authStore.getState().user;

  // Form state
  const defaultIssuer = user?.subject || 'urn:zkdoc:issuer:default';
  const [issuerId, setIssuerId] = useState(defaultIssuer);
  const [schemaId, setSchemaId] = useState('urn:zkdoc:schema:default:v1');
  const [issuerKeyId, setIssuerKeyId] = useState('default-key-1');
  const [expiresAt, setExpiresAt] = useState('');
  const [attributes, setAttributes] = useState<AttributeRequestDto[]>([
    { name: 'fullName', type: 'STRING', value: 'Jane Doe' },
    { name: 'role', type: 'STRING', value: 'Research Fellow' },
    { name: 'clearanceLevel', type: 'INTEGER', value: '3' },
  ]);

  // Workflow states
  const [isReviewOpen, setIsReviewOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [issuanceError, setIssuanceError] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [issuedResult, setIssuedResult] =
    useState<IssueCredentialResponse | null>(null);

  const handleAddAttribute = () => {
    setAttributes((prev) => [...prev, { name: '', type: 'STRING', value: '' }]);
  };

  const handleRemoveAttribute = (index: number) => {
    setAttributes((prev) => prev.filter((_, i) => i !== index));
  };

  const handleAttributeChange = (
    index: number,
    field: keyof AttributeRequestDto,
    val: string,
  ) => {
    setAttributes((prev) =>
      prev.map((attr, i) => (i === index ? { ...attr, [field]: val } : attr)),
    );
  };

  const validateForm = (): boolean => {
    const errors: string[] = [];
    if (!issuerId.trim()) {
      errors.push('Issuer Identity (issuerId) is required.');
    }
    if (attributes.length === 0) {
      errors.push('At least one credential attribute is required.');
    }
    attributes.forEach((attr, idx) => {
      if (!attr.name.trim()) {
        errors.push(`Attribute #${idx + 1} name must not be blank.`);
      }
      if (
        attr.value === null ||
        attr.value === undefined ||
        attr.value === ''
      ) {
        errors.push(`Attribute #${idx + 1} value must not be empty.`);
      }
    });

    setValidationErrors(errors);
    return errors.length === 0;
  };

  const handleOpenReview = (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;
    if (!validateForm()) return;
    setIssuanceError(null);
    setIsReviewOpen(true);
  };

  const handleExecuteIssuance = async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    setIssuanceError(null);

    const payload: IssueCredentialRequest = {
      issuerId: issuerId.trim(),
      schemaId: schemaId.trim() || undefined,
      issuerKeyId: issuerKeyId.trim() || undefined,
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : undefined,
      attributes: attributes.map((a) => ({
        name: a.name.trim(),
        type: a.type || 'STRING',
        value: a.value.trim(),
      })),
    };

    try {
      const response = await issuerApi.issueCredential(payload);
      setIssuedResult(response);
      setIsReviewOpen(false);
      issuerStore.addActivity({
        id: response.credentialId,
        type: 'credential',
        title:
          attributes.find((a) => a.name === 'fullName')?.value ||
          `Credential ${response.credentialId.slice(0, 8)}`,
        digest: response.digest,
        status: 'ACTIVE',
        issuerId: payload.issuerId,
      });
      if (onSuccess) {
        onSuccess(response);
      }
    } catch (err) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Failed to issue credential. Check backend authorization.';
      setIssuanceError(msg);
      setIsReviewOpen(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReset = () => {
    setIssuedResult(null);
    setIssuanceError(null);
    setValidationErrors([]);
    setAttributes([
      { name: 'fullName', type: 'STRING', value: '' },
      { name: 'qualification', type: 'STRING', value: '' },
    ]);
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-400">
              <Award className="h-5 w-5" />
            </div>
            <div>
              <CardTitle>Direct Credential Issuance</CardTitle>
              <CardDescription>
                Issue verifiable, privacy-preserving credentials with
                multi-attribute commitments anchored by the platform key
                authority.
              </CardDescription>
            </div>
          </div>
          <Badge variant="success">Object Auth Enforced</Badge>
        </div>
      </CardHeader>

      <CardContent className="space-y-6">
        {!issuedResult && (
          <form onSubmit={handleOpenReview} className="space-y-6">
            {/* Validation Errors Alert */}
            {validationErrors.length > 0 && (
              <Alert
                variant="danger"
                title="Please resolve validation errors before reviewing"
                icon={<AlertCircle className="h-5 w-5" />}
              >
                <ul className="list-disc space-y-1 pl-5 text-xs">
                  {validationErrors.map((err, i) => (
                    <li key={i}>{err}</li>
                  ))}
                </ul>
              </Alert>
            )}

            {/* Issuance Error Alert */}
            {issuanceError && (
              <Alert
                variant="danger"
                title="Issuance Failed"
                icon={<AlertCircle className="h-5 w-5" />}
              >
                {issuanceError}
              </Alert>
            )}

            {/* Credential Scope Metadata */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input
                label="Issuer Identity (issuerId)"
                value={issuerId}
                onChange={(e) => setIssuerId(e.target.value)}
                placeholder="urn:zkdoc:issuer:alice"
                helperText="Must match your authenticated principal to prevent impersonation."
                required
              />

              <Input
                label="Credential Schema ID"
                value={schemaId}
                onChange={(e) => setSchemaId(e.target.value)}
                placeholder="urn:zkdoc:schema:default:v1"
                helperText="Defines the attribute structural schema."
              />

              <Input
                label="Signing Key Identifier"
                value={issuerKeyId}
                onChange={(e) => setIssuerKeyId(e.target.value)}
                placeholder="default-key-1"
                helperText="Active key registered in the Key Registry."
              />

              <Input
                type="datetime-local"
                label="Expiration Date (Optional)"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
                helperText="Defaults to never expiring if left empty."
              />
            </div>

            {/* Dynamic Attributes Builder */}
            <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/40">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                    Credential Attributes ({attributes.length})
                  </h4>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Attributes will be committed with individual Pedersen
                    blinding factors on issuance.
                  </p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleAddAttribute}
                  aria-label="Add new attribute row"
                >
                  <Plus className="mr-1.5 h-3.5 w-3.5" />
                  Add Attribute
                </Button>
              </div>

              <div className="space-y-3 pt-2">
                {attributes.map((attr, index) => (
                  <div
                    key={index}
                    className="flex flex-col gap-2 rounded-md border border-slate-200 bg-white p-3 shadow-2xs sm:flex-row sm:items-center dark:border-slate-700 dark:bg-slate-800"
                  >
                    <div className="flex-1">
                      <Input
                        placeholder="Attribute Name (e.g. age)"
                        value={attr.name}
                        onChange={(e) =>
                          handleAttributeChange(index, 'name', e.target.value)
                        }
                        aria-label={`Attribute #${index + 1} Name`}
                        required
                      />
                    </div>
                    <div className="w-full sm:w-44">
                      <Select
                        options={ATTRIBUTE_TYPES}
                        value={attr.type}
                        onChange={(e) =>
                          handleAttributeChange(index, 'type', e.target.value)
                        }
                        aria-label={`Attribute #${index + 1} Type`}
                      />
                    </div>
                    <div className="flex-1">
                      <Input
                        placeholder="Attribute Value"
                        value={attr.value}
                        onChange={(e) =>
                          handleAttributeChange(index, 'value', e.target.value)
                        }
                        aria-label={`Attribute #${index + 1} Value`}
                        required
                      />
                    </div>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => handleRemoveAttribute(index)}
                      disabled={attributes.length <= 1}
                      className="text-red-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/30"
                      aria-label={`Remove attribute #${index + 1}`}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
              </div>
            </div>

            {/* Review & Submit Trigger */}
            <div className="flex items-center justify-end gap-3 pt-2">
              <Button type="submit" variant="primary">
                <Eye className="mr-2 h-4 w-4" />
                Review &amp; Issue Credential
              </Button>
            </div>
          </form>
        )}

        {/* Issuance Success Result View */}
        {issuedResult && (
          <div className="space-y-6">
            <Alert
              variant="success"
              title="Credential Successfully Issued & Signed"
              icon={<ShieldCheck className="h-5 w-5 text-emerald-600" />}
            >
              The credential has been cryptographically signed with RSA-PSS by
              the platform key authority. Its initial lifecycle state is{' '}
              <strong>ACTIVE</strong>.
            </Alert>

            <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-5 dark:border-slate-800 dark:bg-slate-900/60">
              <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                  Credential ID (UUID)
                </span>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-sm font-bold text-slate-900 dark:text-slate-100">
                    {issuedResult.credentialId}
                  </span>
                  <CopyToClipboard
                    value={issuedResult.credentialId}
                    label="Copy Credential ID"
                  />
                </div>
              </div>

              <div className="flex flex-col gap-1 border-t border-slate-200/60 pt-3 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
                <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                  SHA-256 Digest
                </span>
                <div className="flex items-center gap-2">
                  <FormattedHash value={issuedResult.digest} />
                </div>
              </div>

              <div className="flex flex-col gap-1 border-t border-slate-200/60 pt-3 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
                <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase dark:text-slate-400">
                  Initial Lifecycle State
                </span>
                <Badge variant="success">ACTIVE</Badge>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4 dark:border-slate-800">
              <Button type="button" variant="outline" onClick={handleReset}>
                Issue Another Credential
              </Button>

              {onNavigateToLifecycle && (
                <Button
                  type="button"
                  variant="primary"
                  onClick={() =>
                    onNavigateToLifecycle(issuedResult.credentialId)
                  }
                >
                  Manage Credential Lifecycle
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Button>
              )}
            </div>
          </div>
        )}

        {/* Review Dialog */}
        <Dialog
          isOpen={isReviewOpen}
          onClose={() => !isSubmitting && setIsReviewOpen(false)}
          title={
            <div className="flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-emerald-600" />
              <span>Confirm Credential Issuance</span>
            </div>
          }
          footer={
            <>
              <Button
                variant="outline"
                onClick={() => setIsReviewOpen(false)}
                disabled={isSubmitting}
              >
                Back to Edit
              </Button>
              <Button
                variant="primary"
                onClick={() => {
                  void handleExecuteIssuance();
                }}
                isLoading={isSubmitting}
              >
                {isSubmitting
                  ? 'Signing & Anchoring...'
                  : 'Confirm & Authorize'}
              </Button>
            </>
          }
        >
          <div className="space-y-4 text-sm text-slate-700 dark:text-slate-300">
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Please review the statements to be signed. Once issued, credential
              integrity is anchored cryptographically and cannot be modified.
            </p>

            <div className="space-y-2 rounded-md border border-slate-200 bg-slate-50 p-3 text-xs dark:border-slate-700 dark:bg-slate-800/60">
              <div>
                <strong>Issuer:</strong> {issuerId}
              </div>
              <div>
                <strong>Schema:</strong> {schemaId}
              </div>
              <div>
                <strong>Key ID:</strong> {issuerKeyId}
              </div>
              {expiresAt && (
                <div>
                  <strong>Expires:</strong>{' '}
                  {new Date(expiresAt).toLocaleString()}
                </div>
              )}
            </div>

            <div>
              <h5 className="mb-2 font-semibold text-slate-900 dark:text-slate-100">
                Attributes to Anchor:
              </h5>
              <div className="overflow-hidden rounded-md border border-slate-200 dark:border-slate-700">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                    <tr>
                      <th className="p-2">Name</th>
                      <th className="p-2">Type</th>
                      <th className="p-2">Value</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
                    {attributes.map((a, i) => (
                      <tr key={i}>
                        <td className="p-2 font-mono font-medium">{a.name}</td>
                        <td className="p-2">{a.type}</td>
                        <td className="p-2 font-mono">{a.value}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </Dialog>
      </CardContent>
    </Card>
  );
};
