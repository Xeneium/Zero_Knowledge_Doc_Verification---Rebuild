import React from 'react';
import { Link } from 'react-router';
import {
  ShieldCheck,
  FileCheck,
  Lock,
  EyeOff,
  ArrowRight,
  Shield,
  FileSearch,
} from 'lucide-react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '../components/common/Card';
import { Badge } from '../components/common/Badge';
import { Button } from '../components/common/Button';

export const HomePage: React.FC = () => {
  return (
    <div className="space-y-10">
      {/* Hero Section */}
      <section className="relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-xs transition-colors sm:p-12 dark:border-slate-800 dark:bg-slate-900">
        <div className="mx-auto max-w-4xl space-y-4">
          <Badge variant="info" className="mb-2">
            Production Design System Active
          </Badge>

          <h1 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-5xl dark:text-slate-100">
            Zero-Knowledge Document Verification
          </h1>

          <p className="mx-auto max-w-2xl text-base leading-relaxed text-slate-600 sm:text-lg dark:text-slate-300">
            Mathematically verify document integrity, cryptographic provenance,
            and attribute predicates without disclosing sensitive private data
            or underlying raw documents.
          </p>

          <div className="flex flex-wrap items-center justify-center gap-4 pt-4">
            <Link to="/verify">
              <Button size="lg" className="flex items-center gap-2">
                <FileSearch className="h-5 w-5" aria-hidden="true" />
                <span>Verify Credential</span>
              </Button>
            </Link>

            <Link to="/predicate-verification">
              <Button
                size="lg"
                variant="outline"
                className="flex items-center gap-2"
              >
                <ShieldCheck
                  className="h-5 w-5 text-indigo-600 dark:text-indigo-400"
                  aria-hidden="true"
                />
                <span>Evaluate ZK Predicate</span>
              </Button>
            </Link>
          </div>
        </div>
      </section>

      {/* Core Architectural Pillars */}
      <div>
        <div className="mb-6">
          <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">
            Cryptographic Architecture
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Engineered with strict zero-knowledge principles and authoritative
            backend verification.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          <Card>
            <CardHeader>
              <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
                <EyeOff className="h-5 w-5" aria-hidden="true" />
              </div>
              <CardTitle>Zero-Knowledge Privacy</CardTitle>
              <CardDescription>
                Selective attribute disclosure without plaintext leak
              </CardDescription>
            </CardHeader>
            <CardContent className="text-sm leading-relaxed text-slate-600 dark:text-slate-300">
              Prove statements such as{' '}
              <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
                Age &gt;= 21
              </code>{' '}
              or{' '}
              <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">
                Income &gt;= 50000
              </code>{' '}
              using Zero-Knowledge proofs without ever revealing the private
              underlying attribute value.
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-green-50 text-green-600 dark:bg-green-950/60 dark:text-green-400">
                <FileCheck className="h-5 w-5" aria-hidden="true" />
              </div>
              <CardTitle>Tamper-Evident Digest</CardTitle>
              <CardDescription>
                SHA-256 fingerprinting &amp; RSA-PSS signatures
              </CardDescription>
            </CardHeader>
            <CardContent className="text-sm leading-relaxed text-slate-600 dark:text-slate-300">
              Source documents are cryptographically fingerprinted using SHA-256
              and bound to digital signatures issued under institutional keys.
              Any bit-level tampering immediately invalidates verification.
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 text-amber-600 dark:bg-amber-950/60 dark:text-amber-400">
                <Lock className="h-5 w-5" aria-hidden="true" />
              </div>
              <CardTitle>Audited Key Registry</CardTitle>
              <CardDescription>
                Rotatable signing keys &amp; immutable audit logs
              </CardDescription>
            </CardHeader>
            <CardContent className="text-sm leading-relaxed text-slate-600 dark:text-slate-300">
              All verification operations resolve against cryptographic keys
              registered in the backend key registry with purpose segregation,
              tamper-evident audit trails, and automated key rotation support.
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Role-Based Workspaces Overview */}
      <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs transition-colors dark:border-slate-800 dark:bg-slate-900">
        <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100">
          Platform Workspace Overview
        </h2>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          The platform provides role-governed workflows for each participant in
          the credential lifecycle:
        </p>

        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Link
            to="/verify"
            className="group rounded-lg border border-slate-200 p-4 transition-all hover:border-indigo-300 hover:shadow-xs dark:border-slate-800 dark:hover:border-indigo-700"
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-900 dark:text-slate-100">
                Public Verifier
              </span>
              <ArrowRight className="h-4 w-4 text-slate-400 transition-transform group-hover:translate-x-1 group-hover:text-indigo-600" />
            </div>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
              Verify credentials and ZK proofs anonymously without
              authentication.
            </p>
          </Link>

          <Link
            to="/credentials"
            className="group rounded-lg border border-slate-200 p-4 transition-all hover:border-indigo-300 hover:shadow-xs dark:border-slate-800 dark:hover:border-indigo-700"
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-900 dark:text-slate-100">
                Holder Wallet
              </span>
              <ArrowRight className="h-4 w-4 text-slate-400 transition-transform group-hover:translate-x-1 group-hover:text-indigo-600" />
            </div>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
              Manage credentials, inspect commitments, and compute ZK predicate
              proofs.
            </p>
          </Link>

          <Link
            to="/issuer"
            className="group rounded-lg border border-slate-200 p-4 transition-all hover:border-indigo-300 hover:shadow-xs dark:border-slate-800 dark:hover:border-indigo-700"
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-900 dark:text-slate-100">
                Issuer Workspace
              </span>
              <ArrowRight className="h-4 w-4 text-slate-400 transition-transform group-hover:translate-x-1 group-hover:text-indigo-600" />
            </div>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
              Upload source documents, sign multi-attribute credentials, and
              manage revocation.
            </p>
          </Link>

          <Link
            to="/administration"
            className="group rounded-lg border border-slate-200 p-4 transition-all hover:border-indigo-300 hover:shadow-xs dark:border-slate-800 dark:hover:border-indigo-700"
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-900 dark:text-slate-100">
                Administration
              </span>
              <ArrowRight className="h-4 w-4 text-slate-400 transition-transform group-hover:translate-x-1 group-hover:text-indigo-600" />
            </div>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
              Rotate cryptographic keys, inspect node recovery, and review audit
              chains.
            </p>
          </Link>
        </div>
      </section>

      {/* Security & Verification Integrity Notice */}
      <section className="rounded-xl border border-indigo-100 bg-indigo-50/50 p-6 dark:border-indigo-950 dark:bg-indigo-950/20">
        <div className="flex items-start gap-4">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300">
            <Shield className="h-5 w-5" aria-hidden="true" />
          </div>
          <div>
            <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">
              Cryptographic Integrity Guarantee
            </h3>
            <p className="mt-1 text-sm leading-relaxed text-slate-600 dark:text-slate-300">
              The platform never displays speculative or simulated verification
              status. Every verification decision is evaluated against live
              RSA-PSS cryptographic signatures, SHA-256 document digests, and
              mathematical predicate assertions validated by the Spring Boot
              backend.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
};
