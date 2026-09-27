import { useEffect, useRef, useState } from 'react';
import { Outlet } from 'react-router';
import { AppHeader } from './AppHeader';
import { Sidebar } from './Sidebar';

export function AppShell() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const wasMobileOpen = useRef(false);

  useEffect(() => {
    if (!mobileOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMobileOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [mobileOpen]);

  useEffect(() => {
    const animationFrame = window.requestAnimationFrame(() => {
      if (mobileOpen) closeButtonRef.current?.focus();
      else if (wasMobileOpen.current) menuButtonRef.current?.focus();
      wasMobileOpen.current = mobileOpen;
    });
    return () => window.cancelAnimationFrame(animationFrame);
  }, [mobileOpen]);

  return (
    <div className="bg-background min-h-screen">
      <a
        href="#main-content"
        className="text-ink-950 fixed top-3 left-3 z-[60] -translate-y-20 rounded-lg bg-white px-4 py-2 font-semibold shadow-md focus:translate-y-0"
      >
        Skip to main content
      </a>
      <Sidebar
        mobileOpen={mobileOpen}
        onClose={() => setMobileOpen(false)}
        closeButtonRef={closeButtonRef}
      />
      <div
        className="flex min-h-screen flex-col lg:pl-72"
        inert={mobileOpen ? true : undefined}
      >
        <AppHeader
          navigationOpen={mobileOpen}
          onOpenNavigation={() => setMobileOpen(true)}
          menuButtonRef={menuButtonRef}
        />
        <main
          id="main-content"
          tabIndex={-1}
          className="flex-1 px-4 py-6 sm:px-6 sm:py-8 lg:px-8"
        >
          <div className="mx-auto w-full max-w-7xl">
            <Outlet />
          </div>
        </main>
        <footer className="border-ink-200 text-ink-500 border-t bg-white px-4 py-4 text-center text-xs sm:px-6 lg:px-8">
          Public verification reveals signature validity and active status only
          · Document contents stay undisclosed.
        </footer>
      </div>
    </div>
  );
}
