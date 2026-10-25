import { LogOut, Settings } from 'lucide-react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const onLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen">
      <header className="no-print border-b border-slate-200 bg-brand text-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/" className="flex items-center gap-3">
            <svg width="28" height="28" viewBox="0 0 64 64" aria-hidden="true">
              <rect width="64" height="64" rx="14" fill="#fff" fillOpacity="0.15" />
              <path d="M24 26h16M24 33h16M24 40h9" stroke="#fff" strokeWidth="4" strokeLinecap="round" />
            </svg>
            <span className="leading-tight">
              <span className="block text-lg font-bold tracking-tight">LedgerMate</span>
              <span className="hidden text-xs text-blue-100 sm:block">Your personal money audit</span>
            </span>
          </Link>
          <div className="flex items-center gap-2 text-sm">
            <span className="hidden max-w-[14rem] truncate text-blue-100 md:inline">{user?.displayName || user?.email}</span>
            <Link to="/settings" className="inline-flex items-center gap-1.5 rounded-lg border border-white/30 px-3 py-1.5 font-medium hover:bg-white/10">
              <Settings size={16} aria-hidden="true" />
              <span className="hidden sm:inline">Settings</span>
              <span className="sr-only sm:hidden">Settings</span>
            </Link>
            <button type="button" onClick={onLogout} className="inline-flex items-center gap-1.5 rounded-lg border border-white/30 px-3 py-1.5 font-medium hover:bg-white/10">
              <LogOut size={16} aria-hidden="true" />
              <span className="hidden sm:inline">Log out</span>
              <span className="sr-only sm:hidden">Log out</span>
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
