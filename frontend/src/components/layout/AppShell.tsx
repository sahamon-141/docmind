import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router';
import { Menu as MenuIcon } from 'lucide-react';
import { IconButton } from '../ui/Button';
import { Logo } from '../ui/Misc';
import { Sidebar } from './Sidebar';

const COLLAPSE_KEY = 'docmind.sidebar.collapsed';

function readCollapsed() {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1';
  } catch {
    return false;
  }
}

/** Authenticated workspace: persistent sidebar on desktop, slide-in drawer below the lg breakpoint. */
export function AppShell() {
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const location = useLocation();

  // close the drawer whenever the route changes
  const [lastPath, setLastPath] = useState(location.pathname);
  if (lastPath !== location.pathname) {
    setLastPath(location.pathname);
    setDrawerOpen(false);
  }

  useEffect(() => {
    if (!drawerOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setDrawerOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [drawerOpen]);

  const toggleCollapsed = () =>
    setCollapsed((c) => {
      try {
        localStorage.setItem(COLLAPSE_KEY, c ? '0' : '1');
      } catch {
        // preference is optional
      }
      return !c;
    });

  return (
    <div className="flex h-full bg-paper">
      <a
        href="#main"
        className="sr-only z-50 rounded bg-ink px-3 py-2 text-paper focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>

      <div className="hidden h-full shrink-0 transition-[width] duration-200 lg:block">
        <Sidebar collapsed={collapsed} onToggleCollapsed={toggleCollapsed} />
      </div>

      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <button
            type="button"
            className="absolute inset-0 animate-fade-in bg-black/40"
            aria-label="Close menu"
            onClick={() => setDrawerOpen(false)}
          />
          <div className="relative h-full w-72 max-w-[85vw] animate-rise bg-paper shadow-[var(--shadow)]">
            <Sidebar collapsed={false} onToggleCollapsed={toggleCollapsed} onClose={() => setDrawerOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex h-12 shrink-0 items-center gap-2 border-b border-line px-3 lg:hidden">
          <IconButton label="Open menu" onClick={() => setDrawerOpen(true)} aria-expanded={drawerOpen}>
            <MenuIcon className="size-4" />
          </IconButton>
          <Logo className="text-lg" />
        </div>
        <main id="main" className="flex min-h-0 flex-1 flex-col">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
