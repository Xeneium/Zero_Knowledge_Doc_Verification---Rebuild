import { Component, type ReactNode } from 'react';
import { ErrorState } from '../components/feedback/ErrorState';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

export class ErrorBoundary extends Component<
  ErrorBoundaryProps,
  ErrorBoundaryState
> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="bg-background flex min-h-screen items-center justify-center p-4">
          <div className="w-full max-w-2xl">
            <ErrorState
              title="The interface could not be displayed"
              description="An unexpected interface error occurred. No operation was completed. Reload the workspace to try again."
              onRetry={() => window.location.assign('/')}
            />
          </div>
        </main>
      );
    }

    return this.props.children;
  }
}
