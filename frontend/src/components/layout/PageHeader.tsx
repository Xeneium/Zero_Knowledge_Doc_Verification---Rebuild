import type { ReactNode } from 'react';
import { Breadcrumbs, type BreadcrumbItem } from '../navigation/Breadcrumbs';

export interface PageHeaderProps {
  title: string;
  description?: string;
  breadcrumbs?: BreadcrumbItem[];
  actions?: ReactNode;
}

export function PageHeader({
  title,
  description,
  breadcrumbs,
  actions,
}: PageHeaderProps) {
  return (
    <header className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        {breadcrumbs?.length ? <Breadcrumbs items={breadcrumbs} /> : null}
        <h1 className="text-ink-950 mt-2 text-2xl font-bold tracking-tight sm:text-3xl">
          {title}
        </h1>
        {description ? (
          <p className="text-ink-600 mt-2 max-w-3xl text-sm sm:text-base">
            {description}
          </p>
        ) : null}
      </div>
      {actions ? <div className="shrink-0">{actions}</div> : null}
    </header>
  );
}
