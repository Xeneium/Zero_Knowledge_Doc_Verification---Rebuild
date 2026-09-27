import { ArrowLeft, Compass } from 'lucide-react';
import { Link } from 'react-router';
import { Card } from '../components/ui/Card';

export function NotFoundPage() {
  return (
    <div className="mx-auto flex min-h-[60vh] max-w-2xl items-center justify-center">
      <Card className="w-full px-6 py-12 text-center sm:px-10">
        <span className="bg-accent-50 text-accent-700 mx-auto inline-flex size-14 items-center justify-center rounded-xl">
          <Compass className="size-7" aria-hidden="true" />
        </span>
        <p className="text-accent-700 mt-5 text-sm font-bold tracking-widest uppercase">
          404 · Route not found
        </p>
        <h1 className="text-ink-950 mt-2 text-3xl font-bold tracking-tight">
          This path is outside the workspace
        </h1>
        <p className="text-ink-600 mx-auto mt-3 max-w-md">
          The page may have moved, or the address may be incomplete. Return to
          the overview to continue.
        </p>
        <Link
          to="/"
          className="bg-accent-600 hover:bg-accent-700 mt-7 inline-flex min-h-11 items-center gap-2 rounded-lg px-4 text-sm font-semibold text-white"
        >
          <ArrowLeft className="size-4" aria-hidden="true" /> Return to overview
        </Link>
      </Card>
    </div>
  );
}
