import React from 'react';
import { Link } from 'react-router';
import { AlertCircle } from 'lucide-react';

export const NotFoundPage: React.FC = () => {
  return (
    <div className="mx-auto max-w-md py-12 text-center">
      <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
        <AlertCircle className="h-6 w-6" aria-hidden="true" />
      </div>
      <h1 className="mb-2 text-2xl font-bold text-slate-900">Page Not Found</h1>
      <p className="mb-6 text-sm text-slate-600">
        The requested verification route does not exist or has been moved.
      </p>
      <Link
        to="/"
        className="inline-flex min-h-[44px] items-center justify-center rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-indigo-700 focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:ring-offset-2 focus-visible:outline-none"
      >
        Return to Overview
      </Link>
    </div>
  );
};
