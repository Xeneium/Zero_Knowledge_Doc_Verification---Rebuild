import { Inbox } from 'lucide-react';
import type { ReactNode } from 'react';
import { Card } from '../ui/Card';

export interface EmptyStateProps {
  title: string;
  description: string;
  action?: ReactNode;
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <Card className="flex flex-col items-center px-6 py-12 text-center">
      <span className="bg-ink-100 text-ink-600 mb-4 inline-flex size-12 items-center justify-center rounded-xl">
        <Inbox className="size-6" aria-hidden="true" />
      </span>
      <h2 className="text-ink-900 text-lg font-semibold">{title}</h2>
      <p className="text-ink-600 mt-2 max-w-md text-sm">{description}</p>
      {action ? <div className="mt-5">{action}</div> : null}
    </Card>
  );
}
