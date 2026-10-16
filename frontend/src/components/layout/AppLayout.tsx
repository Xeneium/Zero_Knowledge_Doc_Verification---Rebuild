import React from 'react';
import { Outlet } from 'react-router';
import { SkipLink } from './SkipLink';
import { Header } from './Header';
import { Footer } from './Footer';
import { ToastProvider } from '../feedback/Toast';

export const AppLayout: React.FC = () => {
  return (
    <ToastProvider>
      <div className="flex min-h-screen flex-col bg-slate-50 text-slate-900 transition-colors dark:bg-slate-950 dark:text-slate-100">
        <SkipLink />
        <Header />
        <main
          id="main-content"
          role="main"
          tabIndex={-1}
          className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 focus:outline-none sm:px-6 lg:px-8"
        >
          <Outlet />
        </main>
        <Footer />
      </div>
    </ToastProvider>
  );
};
