import { X } from 'lucide-react';
import type { Ref } from 'react';
import { NavLink } from 'react-router';
import { navigationItems } from '../../app/navigation';
import { cn } from '../../lib/utils/cn';
import { Badge } from '../ui/Badge';
import { IconButton } from '../ui/IconButton';
import { BrandMark } from './BrandMark';

export interface SidebarProps {
  mobileOpen: boolean;
  onClose: () => void;
  closeButtonRef?: Ref<HTMLButtonElement>;
}

export function Sidebar({ mobileOpen, onClose, closeButtonRef }: SidebarProps) {
  return (
    <>
      {mobileOpen ? (
        <button
          type="button"
          className="bg-ink-950/60 fixed inset-0 z-40 backdrop-blur-[2px] lg:hidden"
          aria-label="Close navigation"
          onClick={onClose}
        />
      ) : null}
      <aside
        id="primary-navigation"
        className={cn(
          'bg-ink-950 fixed inset-y-0 left-0 z-50 flex w-72 flex-col text-white shadow-[var(--shadow-lg)] transition-transform duration-200 lg:translate-x-0 lg:shadow-none',
          mobileOpen
            ? 'visible translate-x-0'
            : 'invisible -translate-x-full lg:visible',
        )}
      >
        <div className="flex h-20 items-center gap-3 border-b border-white/10 px-5">
          <BrandMark className="shrink-0" />
          <div className="min-w-0 flex-1">
            <p className="truncate font-bold tracking-tight">Verity Zero</p>
            <p className="text-ink-300 truncate text-xs">
              Trust without disclosure
            </p>
          </div>
          <IconButton
            ref={closeButtonRef}
            label="Close navigation"
            onClick={onClose}
            className="text-ink-300 hover:bg-white/10 hover:text-white lg:hidden"
          >
            <X className="size-5" aria-hidden="true" />
          </IconButton>
        </div>

        <nav
          aria-label="Primary navigation"
          className="flex-1 overflow-y-auto px-3 py-5"
        >
          <p className="text-ink-400 mb-2 px-3 text-xs font-semibold tracking-widest uppercase">
            Workspace
          </p>
          <ul className="space-y-1">
            {navigationItems.map((item) => {
              const Icon = item.icon;
              return (
                <li key={item.path}>
                  <NavLink
                    to={item.path}
                    end={item.path === '/'}
                    onClick={onClose}
                    className={({ isActive }) =>
                      cn(
                        'flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors',
                        isActive
                          ? 'bg-accent-500 text-ink-950 shadow-sm'
                          : 'text-ink-300 hover:bg-white/8 hover:text-white',
                      )
                    }
                  >
                    <Icon className="size-5 shrink-0" aria-hidden="true" />
                    <span>{item.label}</span>
                  </NavLink>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="border-t border-white/10 p-4">
          <div className="rounded-lg bg-white/6 p-3">
            <Badge tone="accent">Phase 6</Badge>
            <p className="text-ink-300 mt-2 text-xs leading-5">
              Public verification is available. Operational boundaries reflect
              the current backend contract.
            </p>
          </div>
        </div>
      </aside>
    </>
  );
}
