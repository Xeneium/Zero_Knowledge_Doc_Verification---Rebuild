import { Spinner } from '../ui/Spinner';

export function RouteLoading() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <Spinner label="Loading page…" />
    </div>
  );
}
