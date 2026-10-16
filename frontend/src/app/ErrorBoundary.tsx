import React, { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertCircle } from 'lucide-react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public override state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public override componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // In production, log only sanitized diagnostic telemetry; never log private keys or user PII
    if (import.meta.env.DEV) {
      console.error(
        'ErrorBoundary caught an unhandled render error:',
        error,
        errorInfo,
      );
    }
  }

  public override render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div
          role="alert"
          className="mx-auto my-12 max-w-lg rounded-lg border border-red-200 bg-white p-6 text-center shadow-sm"
        >
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
            <AlertCircle className="h-6 w-6" aria-hidden="true" />
          </div>
          <h2 className="mb-2 text-xl font-bold text-slate-900">
            Application Error
          </h2>
          <p className="mb-4 text-sm text-slate-600">
            An unexpected error occurred while rendering the verification
            interface.
          </p>
          <button
            type="button"
            onClick={() => {
              this.setState({ hasError: false, error: null });
              window.location.href = '/';
            }}
            className="inline-flex min-h-[44px] cursor-pointer items-center justify-center rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-indigo-700"
          >
            Reload Application
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
