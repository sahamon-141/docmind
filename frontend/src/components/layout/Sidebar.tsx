import { useId, useState } from 'react';
import { NavLink, useNavigate } from 'react-router';
import { ChevronsUpDown, Library, LogOut, MessageSquare, PanelLeftClose, PanelLeftOpen, Plus, Search, Settings, X } from 'lucide-react';
import { useApiHealth, useConversations, useDocuments } from '../../hooks/queries';
import { useDebounced } from '../../hooks/useDebounced';
import { cn } from '../../lib/format';
import { useAuth } from '../../providers/auth-context';
import { useToast } from '../../providers/toast-context';
import { IconButton } from '../ui/Button';
import { Menu } from '../ui/Menu';
import { Logo, StatusDot } from '../ui/Misc';
import { ConversationList } from './ConversationList';
import { ThemeToggle } from './ThemeToggle';

const NAV = [
  { to: '/chat', label: 'Chat', index: '01', Icon: MessageSquare, end: false },
  { to: '/library', label: 'Library', index: '02', Icon: Library, end: false },
  { to: '/settings', label: 'Settings', index: '03', Icon: Settings, end: false },
];

interface SidebarProps {
  collapsed: boolean;
  onToggleCollapsed: () => void;
  /** set when rendered inside the mobile drawer */
  onClose?: () => void;
}

export function Sidebar({ collapsed, onToggleCollapsed, onClose }: SidebarProps) {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const toast = useToast();
  const searchId = useId();
  const [search, setSearch] = useState('');
  const debounced = useDebounced(search, 250);
  const conversations = useConversations(debounced);
  const documents = useDocuments();
  const health = useApiHealth();
  const indexed = documents.data?.filter((d) => d.status === 'INDEXED').length;

  const signOut = async () => {
    try {
      await logout();
      toast.success('Signed out');
    } catch {
      toast.error('Signed out locally, but the server could not be reached.');
    }
    navigate('/login', { replace: true });
  };

  if (collapsed && !onClose) {
    return (
      <aside className="flex h-full w-16 flex-col items-center gap-2 border-r border-line py-4" aria-label="Sidebar">
        <IconButton label="Expand sidebar" onClick={onToggleCollapsed}>
          <PanelLeftOpen className="size-4" />
        </IconButton>
        <IconButton label="New chat" onClick={() => navigate('/chat')}>
          <Plus className="size-4" />
        </IconButton>
        <div className="my-2 h-px w-8 bg-line" />
        {NAV.map(({ to, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            aria-label={label}
            title={label}
            className={({ isActive }) =>
              cn('flex size-9 items-center justify-center rounded-md transition-colors', isActive ? 'bg-surface text-ink' : 'text-muted hover:text-ink')
            }
          >
            <Icon className="size-4" />
          </NavLink>
        ))}
        <div className="mt-auto">
          <ThemeToggle compact />
        </div>
      </aside>
    );
  }

  return (
    <aside className="flex h-full w-72 flex-col border-r border-line" aria-label="Sidebar">
      <div className="flex items-start justify-between px-4 pt-5 pb-4">
        <div>
          <Logo />
          <p className="label-mono mt-1">Document intelligence</p>
        </div>
        {onClose ? (
          <IconButton label="Close menu" onClick={onClose}>
            <X className="size-4" />
          </IconButton>
        ) : (
          <IconButton label="Collapse sidebar" onClick={onToggleCollapsed}>
            <PanelLeftClose className="size-4" />
          </IconButton>
        )}
      </div>

      <div className="space-y-2 px-3">
        <button
          type="button"
          onClick={() => {
            navigate('/chat');
            onClose?.();
          }}
          className="flex h-9 w-full items-center gap-2 rounded-md border border-line-strong bg-surface px-3 text-sm font-medium text-ink transition-colors hover:border-ink/40"
        >
          <Plus className="size-4" /> New chat
        </button>
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-3.5 -translate-y-1/2 text-muted" aria-hidden />
          <label htmlFor={searchId} className="sr-only">
            Search conversations
          </label>
          <input
            id={searchId}
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search conversations"
            className="h-9 w-full rounded-md border border-line bg-transparent pr-3 pl-8 text-[13px] text-ink placeholder:text-muted hover:border-line-strong focus:border-ink/40 focus:outline-none"
          />
        </div>
      </div>

      <nav aria-label="Primary" className="mt-5 space-y-px px-2">
        {NAV.map(({ to, label, index, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onClose}
            className={({ isActive }) =>
              cn(
                'flex items-center rounded py-1.5 pl-3 font-mono text-[12.5px] transition-colors',
                isActive ? 'text-ink shadow-[inset_2px_0_0_var(--accent)]' : 'text-muted hover:text-ink',
              )
            }
          >
            {label} <span className="ml-1.5 text-muted/70">// {index}</span>
          </NavLink>
        ))}
      </nav>

      <div className="mt-6 min-h-0 flex-1 overflow-y-auto pb-4">
        <h2 className="label-mono mb-3 px-4">{search ? 'Search results' : 'History'}</h2>
        <ConversationList
          conversations={conversations.data?.content}
          loading={conversations.isLoading}
          error={conversations.isError}
          searching={Boolean(debounced.trim())}
          onNavigate={onClose}
        />
      </div>

      <div className="space-y-3 border-t border-line px-4 py-4">
        <dl className="grid grid-cols-[auto_1fr] items-center gap-x-4 gap-y-1.5">
          <dt className="label-mono">Status</dt>
          <dd className="justify-self-end">
            {health.isLoading ? (
              <StatusDot tone="neutral" label="Checking" />
            ) : health.data === 'UP' ? (
              <StatusDot tone="positive" label="Online" pulse />
            ) : (
              <StatusDot tone="negative" label="Offline" />
            )}
          </dd>
          <dt className="label-mono">Indexed</dt>
          <dd className="justify-self-end font-mono text-[10.5px] text-ink-soft uppercase">
            {indexed ?? '—'} {indexed === 1 ? 'doc' : 'docs'}
          </dd>
        </dl>
        <div className="flex items-center justify-between gap-2">
          <Menu
            side="top"
            align="left"
            className="min-w-0 flex-1"
            items={[
              { label: 'Settings', icon: <Settings className="size-3.5" />, onSelect: () => navigate('/settings') },
              { label: 'Sign out', icon: <LogOut className="size-3.5" />, onSelect: () => void signOut() },
            ]}
            trigger={(props) => (
              <button
                type="button"
                {...props}
                className="flex w-full min-w-0 items-center gap-2.5 rounded-md p-1 text-left transition-colors hover:bg-surface"
              >
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-ink font-serif text-sm text-paper">
                  {user?.username.charAt(0).toUpperCase()}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[13px] font-medium text-ink">{user?.username}</span>
                  <span className="block truncate text-[11px] text-muted">{user?.email}</span>
                </span>
                <ChevronsUpDown className="size-3.5 shrink-0 text-muted" aria-hidden />
              </button>
            )}
          />
        </div>
        <ThemeToggle />
      </div>
    </aside>
  );
}
