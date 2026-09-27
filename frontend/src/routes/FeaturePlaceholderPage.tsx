import type { LucideIcon } from 'lucide-react';
import { Badge } from '../components/ui/Badge';
import { Card } from '../components/ui/Card';
import { EmptyState } from '../components/feedback/EmptyState';
import { PageHeader } from '../components/layout/PageHeader';

export interface FeaturePlaceholderPageProps {
  title: string;
  description: string;
  detail: string;
  icon: LucideIcon;
  backendNote?: string;
}

export function FeaturePlaceholderPage({
  title,
  description,
  detail,
  icon: Icon,
  backendNote,
}: FeaturePlaceholderPageProps) {
  return (
    <>
      <PageHeader
        title={title}
        description={description}
        breadcrumbs={[{ label: 'Overview', to: '/' }, { label: title }]}
        actions={<Badge tone="info">Backend boundary</Badge>}
      />

      <Card className="mb-5 overflow-hidden">
        <div className="border-ink-200 bg-ink-50 flex flex-col gap-4 border-b p-5 sm:flex-row sm:items-center sm:p-6">
          <span className="text-accent-700 inline-flex size-12 shrink-0 items-center justify-center rounded-xl bg-white shadow-sm">
            <Icon className="size-6" aria-hidden="true" />
          </span>
          <div>
            <h2 className="text-ink-900 font-semibold">Contract reviewed</h2>
            <p className="text-ink-600 mt-1 text-sm leading-6">{detail}</p>
          </div>
        </div>
        {backendNote ? (
          <div className="text-ink-600 px-5 py-4 text-sm sm:px-6">
            <span className="text-ink-800 font-semibold">
              Verified backend boundary:
            </span>{' '}
            {backendNote}
          </div>
        ) : null}
      </Card>

      <EmptyState
        title={`${title} workflow is not connected yet`}
        description="This is an intentional backend boundary, not missing data. No operational control or result is presented without a supported API and authorization path."
      />
    </>
  );
}
