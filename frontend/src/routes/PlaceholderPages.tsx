import React from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../components/common/Card';
import { Alert } from '../components/common/Alert';
import { Badge } from '../components/common/Badge';

interface PlaceholderProps {
  title: string;
  targetPhase: string;
  description: string;
  apiEndpoints: string[];
}

const PlaceholderPage: React.FC<PlaceholderProps> = ({
  title,
  targetPhase,
  description,
  apiEndpoints,
}) => (
  <div className="mx-auto max-w-3xl space-y-6">
    <div className="flex items-center justify-between">
      <h1 className="text-2xl font-bold text-slate-900">{title}</h1>
      <Badge variant="warning">Scheduled for Phase {targetPhase}</Badge>
    </div>

    <Alert variant="info" title="Foundation Route Active">
      This route is registered in Spring Boot's{' '}
      <code>SpaForwardController</code> and verified by{' '}
      <code>FrontendIntegrationTest</code>. Full interactive workflow
      implementation is scheduled for <strong>Phase {targetPhase}</strong>.
    </Alert>

    <Card>
      <CardHeader>
        <CardTitle>Planned Implementation Scope</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4 text-sm text-slate-600">
        <p>{description}</p>
        <div>
          <h4 className="mb-2 text-xs font-semibold tracking-wider text-slate-800 uppercase">
            Target Backend REST Endpoints:
          </h4>
          <ul className="list-disc space-y-1 pl-5 font-mono text-xs text-slate-700">
            {apiEndpoints.map((endpoint) => (
              <li key={endpoint}>{endpoint}</li>
            ))}
          </ul>
        </div>
      </CardContent>
    </Card>
  </div>
);

export const VerifyPlaceholderPage: React.FC = () => (
  <PlaceholderPage
    title="Public Credential Verification"
    targetPhase="F8"
    description="Implements QR camera scanning via html5-qrcode, canonical JSON parsing, and verification of digital signatures against the key registry."
    apiEndpoints={[
      'POST /api/verifier/credentials/verify',
      'GET /api/verifier/credentials/{id}',
    ]}
  />
);

export const PredicatePlaceholderPage: React.FC = () => (
  <PlaceholderPage
    title="Zero-Knowledge Predicate Evaluation"
    targetPhase="F9"
    description="Implements mathematical verification of attribute threshold statements using strict 6-field ZKP payloads without revealing underlying private values."
    apiEndpoints={['POST /api/v2/zkp/credential-predicates/verify']}
  />
);

export const HolderPlaceholderPage: React.FC = () => (
  <PlaceholderPage
    title="Holder Credential Wallet"
    targetPhase="F7"
    description="Provides authenticated credential holders with attribute inspection, lifecycle status monitoring, and ZKP proof generation with cryptographically secure hex nonces."
    apiEndpoints={[
      'GET /api/holder/credentials',
      'POST /api/holder/credentials/proofs/generate',
    ]}
  />
);

export const IssuerPlaceholderPage: React.FC = () => (
  <PlaceholderPage
    title="Issuer Credential Workspace"
    targetPhase="F6"
    description="Enables credential issuers to upload source documents with SHA-256 fingerprinting, issue signed multi-attribute credentials, and execute cryptographic revocations."
    apiEndpoints={[
      'POST /api/issuer/documents/upload',
      'POST /api/issuer/credentials/issue',
      'POST /api/issuer/credentials/{id}/revoke',
    ]}
  />
);

export const AuditPlaceholderPage: React.FC = () => (
  <PlaceholderPage
    title="Platform Immutable Audit Logs"
    targetPhase="F9"
    description="Allows administrators and authorized issuers to inspect, filter, and cryptographically verify the SHA-256 audit event chain."
    apiEndpoints={['GET /api/audit/logs']}
  />
);

export const AdminPlaceholderPage: React.FC = () => (
  <PlaceholderPage
    title="Platform Administration &amp; Recovery"
    targetPhase="F9"
    description="Manages cryptographic key rotation, key registry lifecycle status, and node recovery state reconciliation."
    apiEndpoints={[
      'GET /api/keys/status',
      'POST /api/admin/keys/rotate',
      'POST /api/admin/recovery/reconcile',
      'GET /api/admin/recovery/status',
    ]}
  />
);
