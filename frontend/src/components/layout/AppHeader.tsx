import { Bell, KeyRound, Menu, UserRound } from 'lucide-react';
import { useState, type Ref } from 'react';
import { useMatches } from 'react-router';
import { IconButton } from '../ui/IconButton';
import { Tooltip } from '../ui/Tooltip';
import { Badge } from '../ui/Badge';
import { useAuth } from '../../features/auth/AuthContext';
import { SessionModal } from '../../features/auth/SessionModal';

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
  const [sessionModalOpen, setSessionModalOpen] = useState(false);
  const { session, isAuthenticated } = useAuth();
  const matches = useMatches();
  const current = [...matches]
    .reverse()
    .find((match) => (match.handle as RouteHandle | undefined)?.title);
  const title =
    (current?.handle as RouteHandle | undefined)?.title ||
    'Verification workspace';

  return (
    <>
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
          <Tooltip content="Notifications will be available in future releases.">
            <IconButton label="Notifications unavailable" disabled>
              <Bell className="size-5" aria-hidden="true" />
            </IconButton>
          </Tooltip>
          <div
            className="bg-ink-200 hidden h-7 w-px sm:block"
            aria-hidden="true"
          />

          {isAuthenticated && session ? (
            <button
              type="button"
              onClick={() => setSessionModalOpen(true)}
              className="flex min-h-11 items-center gap-2 rounded-lg border border-ink-200 bg-white px-2.5 py-1 text-left shadow-xs transition hover:border-accent-500 hover:bg-accent-50/50 focus:outline-none focus:ring-2 focus:ring-accent-500"
              aria-label="Manage active session"
            >
              <span className="bg-accent-100 text-accent-700 inline-flex size-7 items-center justify-center rounded-full">
                <KeyRound className="size-3.5" aria-hidden="true" />
              </span>
              <div className="flex flex-col text-xs">
                <span className="font-semibold text-ink-900 max-w-[120px] truncate">
                  {session.sub}
                </span>
                <span className="text-ink-500 font-mono text-[10px]">
                  {session.roles[0] || 'USER'}
                </span>
              </div>
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setSessionModalOpen(true)}
              className="text-ink-600 hover:text-ink-900 flex min-h-11 items-center gap-2 rounded-lg px-2 text-sm font-medium transition hover:bg-ink-100/60 focus:outline-none focus:ring-2 focus:ring-accent-500"
              aria-label="Connect security session"
            >
              <span className="bg-ink-100 inline-flex size-8 items-center justify-center rounded-full">
                <UserRound className="size-4" aria-hidden="true" />
              </span>
              <span className="hidden sm:inline">Connect session</span>
            </button>
          )}
        </div>
      </header>

      <SessionModal
        open={sessionModalOpen}
        onClose={() => setSessionModalOpen(false)}
      />
    </>
  );
}
