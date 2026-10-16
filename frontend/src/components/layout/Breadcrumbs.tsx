import React from 'react';
import { NavLink } from 'react-router';
import { ChevronRight, Home } from 'lucide-react';
import { cn } from '../../lib/utils/cn';

export interface BreadcrumbItem {
  label: string;
  href?: string;
}

export interface BreadcrumbsProps extends React.HTMLAttributes<HTMLElement> {
  items: BreadcrumbItem[];
  showHome?: boolean;
}

export function Breadcrumbs({
  items,
  showHome = true,
  className,
  ...props
}: BreadcrumbsProps) {
  if (items.length === 0) return null;

  return (
    <nav
      aria-label="Breadcrumb"
      className={cn(
        'flex items-center py-1.5 text-xs text-slate-500 dark:text-slate-400',
        className,
      )}
      {...props}
    >
      <ol className="flex flex-wrap items-center gap-1.5">
        {showHome && (
          <li className="flex items-center">
            <NavLink
              to="/"
              className="inline-flex min-h-[36px] min-w-[36px] items-center justify-center rounded p-1 text-slate-500 hover:text-slate-700 hover:underline focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:text-slate-400 dark:hover:text-slate-200"
              aria-label="Home"
            >
              <Home className="h-3.5 w-3.5" aria-hidden="true" />
            </NavLink>
            <ChevronRight
              className="ml-1 h-3.5 w-3.5 shrink-0 text-slate-400"
              aria-hidden="true"
            />
          </li>
        )}

        {items.map((item, index) => {
          const isLast = index === items.length - 1;

          return (
            <li key={`${item.label}-${index}`} className="flex items-center">
              {isLast || !item.href ? (
                <span
                  aria-current="location"
                  className="font-medium text-slate-900 dark:text-slate-100"
                >
                  {item.label}
                </span>
              ) : (
                <>
                  <NavLink
                    to={item.href}
                    className="inline-flex min-h-[36px] items-center rounded p-1 text-slate-500 hover:text-slate-700 hover:underline focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:outline-none dark:text-slate-400 dark:hover:text-slate-200"
                  >
                    {item.label}
                  </NavLink>
                  <ChevronRight
                    className="ml-1 h-3.5 w-3.5 shrink-0 text-slate-400"
                    aria-hidden="true"
                  />
                </>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
