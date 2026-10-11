import { useState, type FormEvent } from 'react';
import { NavLink, useNavigate, useParams } from 'react-router';
import { Ellipsis, Pencil, Trash2 } from 'lucide-react';
import type { ConversationSummary } from '../../api/types';
import { errorMessage } from '../../api/errors';
import { useDeleteConversation, useRenameConversation } from '../../hooks/queries';
import { cn, groupByDate } from '../../lib/format';
import { useToast } from '../../providers/toast-context';
import { ConfirmDialog } from '../ui/Dialog';
import { Menu } from '../ui/Menu';
import { Skeleton } from '../ui/Spinner';

interface ConversationListProps {
  conversations: ConversationSummary[] | undefined;
  loading: boolean;
  error: boolean;
  searching: boolean;
  onNavigate?: () => void;
}

export function ConversationList({ conversations, loading, error, searching, onNavigate }: ConversationListProps) {
  const { conversationId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const rename = useRenameConversation();
  const remove = useDeleteConversation();
  const [editing, setEditing] = useState<{ id: string; title: string } | null>(null);
  const [deleting, setDeleting] = useState<ConversationSummary | null>(null);

  if (loading) {
    return (
      <div className="space-y-2 px-3" aria-label="Loading conversations">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-7 w-full" />
        ))}
      </div>
    );
  }
  if (error) return <p className="px-4 text-xs text-danger">Could not load conversations.</p>;
  if (!conversations?.length) {
    return <p className="px-4 text-xs text-muted">{searching ? 'No conversations match.' : 'No conversations yet.'}</p>;
  }

  const submitRename = (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    const { id, title: raw } = editing;
    const title = raw.trim();
    setEditing(null); // leave edit mode now so the blur that follows does not submit twice
    const current = conversations.find((c) => c.id === id)?.title;
    if (!title || title === current) return;
    rename.mutate(
      { id, title },
      {
        onSuccess: () => toast.success('Conversation renamed'),
        onError: (err) => toast.error(errorMessage(err)),
      },
    );
  };

  const confirmDelete = () => {
    if (!deleting) return;
    const target = deleting;
    remove.mutate(target.id, {
      onSuccess: () => {
        toast.success('Conversation deleted');
        if (conversationId === target.id) navigate('/chat');
      },
      onError: (err) => toast.error(errorMessage(err)),
      onSettled: () => setDeleting(null),
    });
  };

  return (
    <>
      <nav aria-label="Conversation history" className="space-y-5">
        {groupByDate(conversations, (c) => c.updatedAt).map((group) => (
          <section key={group.label}>
            <h3 className="label-mono mb-1.5 px-4">{group.label}</h3>
            <ul className="space-y-px px-2">
              {group.items.map((c) => (
                <li key={c.id} className="group relative">
                  {editing?.id === c.id ? (
                    <form onSubmit={submitRename} className="px-1">
                      <label className="sr-only" htmlFor={`rename-${c.id}`}>
                        Conversation title
                      </label>
                      <input
                        id={`rename-${c.id}`}
                        autoFocus
                        value={editing.title}
                        maxLength={200}
                        onChange={(e) => setEditing({ id: c.id, title: e.target.value })}
                        onBlur={submitRename}
                        onKeyDown={(e) => e.key === 'Escape' && setEditing(null)}
                        className="h-8 w-full rounded border border-ink/40 bg-surface px-2 text-[13px] focus:outline-none"
                      />
                    </form>
                  ) : (
                    <>
                      <NavLink
                        to={`/chat/${c.id}`}
                        onClick={onNavigate}
                        className={({ isActive }) =>
                          cn(
                            'relative block truncate rounded py-1.5 pr-8 pl-3 text-[13px] transition-colors',
                            isActive
                              ? 'bg-surface text-ink shadow-[inset_2px_0_0_var(--accent)]'
                              : 'text-ink-soft hover:bg-surface/60 hover:text-ink',
                          )
                        }
                        title={c.title}
                      >
                        {c.title}
                      </NavLink>
                      <div className="absolute inset-y-0 right-1 flex items-center">
                        <Menu
                          items={[
                            { label: 'Rename', icon: <Pencil className="size-3.5" />, onSelect: () => setEditing({ id: c.id, title: c.title }) },
                            { label: 'Delete', icon: <Trash2 className="size-3.5" />, danger: true, onSelect: () => setDeleting(c) },
                          ]}
                          trigger={(props) => (
                            <button
                              type="button"
                              {...props}
                              aria-label={`Actions for ${c.title}`}
                              className="rounded p-1 text-muted opacity-100 transition-opacity hover:bg-sunken hover:text-ink focus-visible:opacity-100 aria-expanded:opacity-100 sm:opacity-0 sm:group-hover:opacity-100"
                            >
                              <Ellipsis className="size-3.5" />
                            </button>
                          )}
                        />
                      </div>
                    </>
                  )}
                </li>
              ))}
            </ul>
          </section>
        ))}
      </nav>
      <ConfirmDialog
        open={deleting !== null}
        title="Delete conversation?"
        description={
          <>
            <span className="font-medium text-ink">{deleting?.title}</span> and all of its messages will be permanently deleted.
          </>
        }
        confirmLabel="Delete"
        loading={remove.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(null)}
      />
    </>
  );
}
