import { ChevronDown } from 'lucide-react';
import type { ReactNode } from 'react';

export interface DropdownMenuProps {
  label: string;
  children: ReactNode;
}

export function DropdownMenu({ label, children }: DropdownMenuProps) {
  return (
    <details className="group relative">
      <summary className="text-ink-700 hover:bg-ink-100 flex min-h-11 cursor-pointer list-none items-center gap-2 rounded-lg px-3 text-sm font-semibold">
        {label}
        <ChevronDown
          className="size-4 transition-transform group-open:rotate-180"
          aria-hidden="true"
        />
      </summary>
      <div className="border-ink-200 absolute right-0 z-40 mt-2 min-w-48 rounded-lg border bg-white p-1 shadow-[var(--shadow-md)]">
        {children}
      </div>
    </details>
  );
}
