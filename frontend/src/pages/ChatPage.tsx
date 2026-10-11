import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { BookOpen, FileText, MessageSquareOff, TriangleAlert, X } from 'lucide-react';
import type { Citation, Message } from '../api/types';
import { isApiError } from '../api/errors';
import { AssistantMessage, UserMessage } from '../components/chat/MessageItem';
import { Composer } from '../components/chat/Composer';
import { EmptyChat } from '../components/chat/EmptyChat';
import { SourcesPanel } from '../components/chat/SourcesPanel';
import { Button } from '../components/ui/Button';
import { EmptyState } from '../components/ui/Misc';
import { Skeleton } from '../components/ui/Spinner';
import { useChatSession } from '../features/chat/useChatSession';
import { useUploader } from '../features/documents/useUploader';
import { useConversation, useDocuments } from '../hooks/queries';
import { useToast } from '../providers/toast-context';

interface SourcesState {
  key: string;
  citations: Citation[];
  active: number | null;
}

export default function ChatPage() {
  const { conversationId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const key = conversationId ?? 'new';

  const documentsQuery = useDocuments();
  const documents = useMemo(() => documentsQuery.data ?? [], [documentsQuery.data]);

  const onConversationCreated = useCallback((id: string) => navigate(`/chat/${id}`, { replace: true }), [navigate]);
  const session = useChatSession({ conversationId, onConversationCreated });
  const conversation = useConversation(conversationId, !session.busy);

  const [drafts, setDrafts] = useState<Record<string, string>>({});
  // "Ask about this document" from the library arrives as router state
  const location = useLocation();
  const requestedScope = (location.state as { scope?: string } | null)?.scope;
  const [scopes, setScopes] = useState<Record<string, string | null>>(() => {
    const initial: Record<string, string | null> = {};
    if (requestedScope) initial.new = requestedScope;
    return initial;
  });
  const [sources, setSources] = useState<SourcesState | null>(null);
  const [lastQuestion, setLastQuestion] = useState<string | null>(null);

  const messages = conversation.data?.messages;
  const draft = drafts[key] ?? '';
  const setDraft = (text: string) => setDrafts((d) => ({ ...d, [key]: text }));

  // default scope: whatever the last question in this conversation was scoped to
  const lastScoped = messages?.findLast((m) => m.role === 'USER')?.documentId ?? null;
  const rawScope = key in scopes ? (scopes[key] ?? null) : lastScoped;
  const scope = rawScope && documents.some((d) => d.id === rawScope && d.status === 'INDEXED') ? rawScope : null;
  const scopedDoc = documents.find((d) => d.id === scope);

  const uploader = useUploader((result) => {
    toast.success(`${result.filename} indexed (${result.chunksCreated ?? 0} chunks).`);
    if (result.id) setScopes((s) => ({ ...s, [key]: result.id }));
  });
  const lastUpload = uploader.items[0];
  useEffect(() => {
    if (lastUpload?.phase === 'error' && lastUpload.error !== 'Cancelled') toast.error(`${lastUpload.name}: ${lastUpload.error}`);
  }, [lastUpload?.phase, lastUpload?.error, lastUpload?.name, toast]);

  const send = (text: string) => {
    setLastQuestion(text);
    setDraft('');
    void session.send(text, { documentId: scope });
  };

  // ---------------------------------------------------------------- visible message list
  const pending = session.pending;
  const visible: Message[] = (messages ?? []).filter((m) => !(pending?.retryOf && m.replyToId === pending.retryOf));
  const pendingUserId = pending?.userMessage?.id;
  const showPendingQuestion = pending && !pending.retryOf && !(pendingUserId && visible.some((m) => m.id === pendingUserId));

  // ---------------------------------------------------------------- stick to bottom while streaming
  const scrollRef = useRef<HTMLDivElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const pinnedRef = useRef(true);
  useEffect(() => {
    if (pinnedRef.current) endRef.current?.scrollIntoView({ block: 'end' });
  }, [visible.length, pending?.text, pending?.question]);
  useEffect(() => {
    pinnedRef.current = true;
  }, [key]);

  const openSources = (citations: Citation[], position: number | null) => setSources({ key, citations, active: position });
  const latestCitations = [...visible].reverse().find((m) => m.role === 'ASSISTANT' && m.citations.length)?.citations;
  const panel = sources?.key === key ? sources : null;

  const notFound = isApiError(conversation.error) && conversation.error.status === 404;
  const isEmpty = !conversationId && !pending;

  return (
    <div className="flex min-h-0 flex-1">
      <section className="flex min-w-0 flex-1 flex-col" aria-label="Conversation">
        <header className="flex h-14 shrink-0 items-center gap-3 border-b border-line px-4 sm:px-6">
          <div className="min-w-0 flex-1">
            <h1 className="truncate font-serif text-lg font-medium tracking-tight">
              {conversationId ? (conversation.data?.title ?? (conversation.isLoading ? '…' : 'Conversation')) : 'New conversation'}
            </h1>
          </div>
          {scopedDoc && (
            <span className="hidden max-w-[14rem] items-center gap-1.5 truncate rounded border border-line px-2 py-1 text-xs text-ink-soft sm:inline-flex">
              <FileText className="size-3 shrink-0" aria-hidden />
              <span className="truncate">{scopedDoc.filename}</span>
            </span>
          )}
          {latestCitations && (
            <Button
              size="sm"
              variant="ghost"
              icon={<BookOpen className="size-4" />}
              onClick={() => (panel ? setSources(null) : openSources(latestCitations, null))}
              aria-pressed={Boolean(panel)}
            >
              <span className="hidden sm:inline">Sources</span>
            </Button>
          )}
        </header>

        <div
          ref={scrollRef}
          onScroll={(e) => {
            const el = e.currentTarget;
            pinnedRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
          }}
          className="flex min-h-0 flex-1 flex-col overflow-y-auto"
        >
          {notFound ? (
            <EmptyState
              className="my-auto"
              icon={<MessageSquareOff className="size-8" />}
              title="Conversation not found"
              description="It may have been deleted, or it belongs to another account."
              action={
                <Link to="/chat" className="text-sm font-medium underline underline-offset-4">
                  Start a new conversation
                </Link>
              }
            />
          ) : isEmpty ? (
            <EmptyChat documents={documents} loadingDocuments={documentsQuery.isLoading} onPick={send} />
          ) : conversation.isLoading && !pending ? (
            <div className="mx-auto w-full max-w-3xl space-y-8 px-4 py-8 sm:px-6" aria-label="Loading conversation">
              <Skeleton className="ml-auto h-12 w-2/3" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="ml-auto h-10 w-1/2" />
            </div>
          ) : conversation.isError && !pending ? (
            <EmptyState
              className="my-auto"
              icon={<TriangleAlert className="size-8" />}
              title="Could not load this conversation"
              action={<Button variant="secondary" onClick={() => void conversation.refetch()}>Try again</Button>}
            />
          ) : (
            <ol className="mx-auto flex w-full max-w-3xl flex-col gap-8 px-4 py-8 sm:px-6">
              {visible.map((m) => (
                <li key={m.id}>
                  {m.role === 'USER' ? (
                    <UserMessage content={m.content} createdAt={m.createdAt} />
                  ) : (
                    <AssistantMessage
                      message={m}
                      onCite={openSources}
                      onRetry={m.status === 'FAILED' && m.replyToId ? () => void session.send('', { retryOf: m.replyToId }) : undefined}
                      retrying={session.busy}
                    />
                  )}
                </li>
              ))}
              {showPendingQuestion && (
                <li>
                  <UserMessage content={pending.question} pending={!pending.userMessage} />
                </li>
              )}
              {pending && (
                <li>
                  <AssistantMessage streamingText={pending.text} streamingSources={pending.sources} onCite={openSources} />
                </li>
              )}
            </ol>
          )}
          {!notFound && !isEmpty && <div ref={endRef} aria-hidden />}
        </div>

        {session.failure && (
          <div className="mx-auto w-full max-w-3xl px-4 sm:px-6">
            <div role="alert" className="mb-3 flex items-start gap-3 rounded-md border border-danger/30 bg-danger-soft px-3 py-2.5 text-sm text-danger">
              <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
              <p className="flex-1">{session.failure.message}</p>
              {!session.failure.conversationId && lastQuestion && (
                <button type="button" className="font-medium underline underline-offset-2" onClick={() => send(lastQuestion)}>
                  Try again
                </button>
              )}
              <button type="button" onClick={session.dismissFailure} aria-label="Dismiss error" className="rounded p-0.5 hover:bg-danger/10">
                <X className="size-4" />
              </button>
            </div>
          </div>
        )}

        {!notFound && (
          <Composer
            onSend={send}
            onStop={session.stop}
            onAttach={(file) => void uploader.addFiles([file])}
            busy={session.busy}
            uploading={uploader.busy}
            documents={documents}
            scope={scope}
            onScopeChange={(id) => setScopes((s) => ({ ...s, [key]: id }))}
            draft={draft}
            onDraftChange={setDraft}
          />
        )}
      </section>

      {panel && <SourcesPanel citations={panel.citations} active={panel.active} onClose={() => setSources(null)} />}
    </div>
  );
}
