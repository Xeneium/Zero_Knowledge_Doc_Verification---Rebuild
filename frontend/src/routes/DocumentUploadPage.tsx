import React from 'react';
import { Link } from 'react-router';
import {
  UploadCloud,
  ShieldCheck,
  FileCheck,
  QrCode,
  Lock,
  ArrowLeft,
  Info,
} from 'lucide-react';
import { PageHeader } from '../components/layout/PageHeader';
import { Badge } from '../components/common/Badge';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '../components/common/Card';
import { Alert } from '../components/feedback/Alert';
import { DocumentUploadCard } from '../features/issuer/components/DocumentUploadCard';
import { useAuth } from '../state/authStore';

export const DocumentUploadPage: React.FC = () => {
  const { user } = useAuth();

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      {/* Page Header */}
      <PageHeader
        title="Document Registration & Integrity Anchoring"
        description="Upload authentic document artifacts to compute authoritative SHA-256 integrity digests, sign with platform RSA-PSS keys, and provision privacy-preserving public verification tokens."
        badge={
          <Badge variant="info">
            <UploadCloud className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
            ROLE_ISSUER ACTIVE
          </Badge>
        }
        actions={
          <div className="flex items-center gap-3">
            <span className="font-mono text-xs text-slate-600 dark:text-slate-400">
              {user?.subject || 'Authenticated Authority'}
            </span>
            <Link
              to="/issuer"
              className="inline-flex items-center text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
            >
              <ArrowLeft className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
              Issuer Console
            </Link>
          </div>
        }
      />

      {/* Explanatory Context & Zero-Knowledge Architecture Notice */}
      <Alert
        variant="info"
        title="Authoritative Registration vs. Verification Separation"
        icon={<Info className="h-5 w-5" />}
      >
        <span>
          <strong>No Embedded QR Required:</strong> You can upload any standard
          PDF, PNG, JPEG, or plain text document. The original file does not need
          to contain a QR code. Upon registration, the backend generates an
          unforgeable public capability token and a standalone QR code for
          verifiers.
        </span>
      </Alert>

      {/* How Document Registration Works */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="border-slate-200 p-4 dark:border-slate-800">
          <div className="mb-2 flex h-8 w-8 items-center justify-center rounded-md bg-indigo-50 text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400">
            <FileCheck className="h-4 w-4" aria-hidden="true" />
          </div>
          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            1. SHA-256 Digest
          </h4>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Spring Boot computes an authoritative SHA-256 cryptographic digest
            directly from the uploaded byte stream.
          </p>
        </Card>

        <Card className="border-slate-200 p-4 dark:border-slate-800">
          <div className="mb-2 flex h-8 w-8 items-center justify-center rounded-md bg-emerald-50 text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-400">
            <ShieldCheck className="h-4 w-4" aria-hidden="true" />
          </div>
          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            2. RSA-PSS Signature
          </h4>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            The digest is digitally signed with RSA-PSS using platform keys
            registered in the immutable KeyRegistry.
          </p>
        </Card>

        <Card className="border-slate-200 p-4 dark:border-slate-800">
          <div className="mb-2 flex h-8 w-8 items-center justify-center rounded-md bg-purple-50 text-purple-600 dark:bg-purple-950/50 dark:text-purple-400">
            <QrCode className="h-4 w-4" aria-hidden="true" />
          </div>
          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            3. Public Capability
          </h4>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            A 64-character capability token and QR code are issued for
            privacy-preserving public verification.
          </p>
        </Card>

        <Card className="border-slate-200 p-4 dark:border-slate-800">
          <div className="mb-2 flex h-8 w-8 items-center justify-center rounded-md bg-amber-50 text-amber-600 dark:bg-amber-950/50 dark:text-amber-400">
            <Lock className="h-4 w-4" aria-hidden="true" />
          </div>
          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            4. Minimal Disclosure
          </h4>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Verifiers check integrity without accessing confidential document
            contents or personal identifying attributes.
          </p>
        </Card>
      </div>

      {/* Main Document Upload Experience */}
      <DocumentUploadCard />

      {/* Constraints & Supported Formats Reference Card */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardHeader>
          <CardTitle className="text-sm">
            Technical Constraints &amp; Validation Policies
          </CardTitle>
          <CardDescription className="text-xs">
            Policies enforced on the client for user feedback and authoritatively
            enforced on the Spring Boot backend.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 text-xs sm:grid-cols-3">
            <div className="rounded-md border border-slate-100 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/40">
              <dt className="font-semibold text-slate-700 dark:text-slate-300">
                Accepted Formats
              </dt>
              <dd className="mt-1 text-slate-500 dark:text-slate-400">
                PDF (.pdf), PNG (.png), JPEG (.jpg, .jpeg), Plain Text (.txt)
              </dd>
            </div>
            <div className="rounded-md border border-slate-100 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/40">
              <dt className="font-semibold text-slate-700 dark:text-slate-300">
                Maximum File Size
              </dt>
              <dd className="mt-1 text-slate-500 dark:text-slate-400">
                25 MB bound (26,214,400 bytes)
              </dd>
            </div>
            <div className="rounded-md border border-slate-100 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/40">
              <dt className="font-semibold text-slate-700 dark:text-slate-300">
                Content Inspection
              </dt>
              <dd className="mt-1 text-slate-500 dark:text-slate-400">
                Magic byte header verification against MIME spoofing
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  );
};
