import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router';

export interface BreadcrumbItem {
  label: string;
  to?: string;
}

export function Breadcrumbs({ items }: { items: BreadcrumbItem[] }) {
  return (
    <nav aria-label="Breadcrumb">
      <ol className="text-ink-600 flex flex-wrap items-center gap-1 text-sm">
        {items.map((item, index) => (
          <li
            key={`${item.label}-${index}`}
            className="flex items-center gap-1"
          >
            {index > 0 ? (
              <ChevronRight
                className="text-ink-400 size-4"
                aria-hidden="true"
              />
            ) : null}
            {item.to ? (
              <Link to={item.to} className="hover:text-ink-900 hover:underline">
                {item.label}
              </Link>
            ) : (
              <span aria-current="page">{item.label}</span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
