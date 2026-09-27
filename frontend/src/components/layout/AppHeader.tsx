import { Bell, Menu, UserRound } from 'lucide-react';
import type { Ref } from 'react';
import { useMatches } from 'react-router';
import { IconButton } from '../ui/IconButton';
import { Tooltip } from '../ui/Tooltip';

interface RouteHandle {
  title?: string;
}

export function AppHeader({
  navigationOpen,
  onOpenNavigation,
  menuButtonRef,
}: {
  navigationOpen: boolean;
  onOpenNavigation: () => void;
  menuButtonRef?: Ref<HTMLButtonElement>;
}) {
  const matches = useMatches();
  const current = [...matches]
    .reverse()
    .find((match) => (match.handle as RouteHandle | undefined)?.title);
  const title =
    (current?.handle as RouteHandle | undefined)?.title ||
    'Verification workspace';

  return (
    <header className="border-ink-200 sticky top-0 z-30 flex h-16 items-center justify-between gap-4 border-b bg-white/95 px-4 backdrop-blur sm:px-6 lg:px-8">
      <div className="flex min-w-0 items-center gap-3">
        <IconButton
          ref={menuButtonRef}
          label="Open navigation"
          aria-controls="primary-navigation"
          aria-expanded={navigationOpen}
          onClick={onOpenNavigation}
          className="-ml-2 lg:hidden"
        >
          <Menu className="size-5" aria-hidden="true" />
        </IconButton>
        <p className="text-ink-800 truncate text-sm font-semibold sm:text-base">
          {title}
        </p>
      </div>

      <div className="flex items-center gap-1 sm:gap-2">
        <Tooltip content="Notifications will be available after authentication is connected.">
          <IconButton label="Notifications unavailable" disabled>
            <Bell className="size-5" aria-hidden="true" />
          </IconButton>
        </Tooltip>
        <div
          className="bg-ink-200 hidden h-7 w-px sm:block"
          aria-hidden="true"
        />
        <div
          className="text-ink-600 flex min-h-11 items-center gap-2 rounded-lg px-2"
          aria-label="Account not connected"
        >
          <span className="bg-ink-100 inline-flex size-8 items-center justify-center rounded-full">
            <UserRound className="size-4" aria-hidden="true" />
          </span>
          <span className="hidden text-sm font-medium sm:inline">
            Account not connected
          </span>
        </div>
      </div>
    </header>
  );
}
