import { useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { ArrowUp, Paperclip, Square } from 'lucide-react';
import type { DocumentMetadata } from '../../api/types';
import { cn } from '../../lib/format';
import { Spinner } from '../ui/Spinner';
import { Kbd } from '../ui/Misc';

const MAX_LENGTH = 4000;

interface ComposerProps {
  onSend: (text: string) => void;
  onStop: () => void;
  onAttach: (file: File) => void;
  busy: boolean;
  uploading: boolean;
  documents: DocumentMetadata[];
  scope: string | null;
  onScopeChange: (documentId: string | null) => void;
  draft: string;
  onDraftChange: (text: string) => void;
}

export function Composer({
  onSend,
  onStop,
  onAttach,
  busy,
  uploading,
  documents,
  scope,
  onScopeChange,
  draft,
  onDraftChange,
}: ComposerProps) {
  const fileInput = useRef<HTMLInputElement>(null);
  const textareaId = useId();
  const [composing, setComposing] = useState(false);
  const indexed = documents.filter((d) => d.status === 'INDEXED');
  const rows = Math.min(8, Math.max(1, draft.split('\n').length));
  const canSend = draft.trim().length > 0 && !busy;

  const submit = (e?: FormEvent) => {
    e?.preventDefault();
    if (canSend) onSend(draft.trim());
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter sends, Shift+Enter inserts a newline; ignore Enter while an IME is composing
    if (e.key === 'Enter' && !e.shiftKey && !composing && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <form onSubmit={submit} className="mx-auto w-full max-w-3xl px-4 pb-4 sm:px-6">
      <div className="rounded-lg border border-line-strong bg-surface shadow-[var(--shadow)] transition-colors focus-within:border-ink/40">
        <label htmlFor={textareaId} className="sr-only">
          Ask a question about your documents
        </label>
        <textarea
          id={textareaId}
          value={draft}
          onChange={(e) => onDraftChange(e.target.value.slice(0, MAX_LENGTH))}
          onKeyDown={onKeyDown}
          onCompositionStart={() => setComposing(true)}
          onCompositionEnd={() => setComposing(false)}
          rows={rows}
          placeholder={indexed.length ? 'Ask anything about your documents…' : 'Ask a question, or attach a document first…'}
          className="block max-h-60 w-full resize-none bg-transparent px-4 pt-3.5 pb-2 text-[15px] leading-relaxed text-ink placeholder:text-muted focus:outline-none"
        />
        <div className="flex items-center gap-2 px-2.5 pb-2.5">
          <input
            ref={fileInput}
            type="file"
            className="hidden"
            accept=".pdf,.txt,.md,.markdown,.csv,.docx,.doc,.pptx,.odt,.rtf,.html,.htm"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) onAttach(file);
              e.target.value = '';
            }}
          />
          <button
            type="button"
            onClick={() => fileInput.current?.click()}
            disabled={uploading}
            className="inline-flex size-8 items-center justify-center rounded-md text-muted transition-colors hover:bg-sunken hover:text-ink disabled:opacity-50"
            aria-label="Upload a document"
            title="Upload a document"
          >
            {uploading ? <Spinner /> : <Paperclip className="size-4" />}
          </button>

          <label className="sr-only" htmlFor={`${textareaId}-scope`}>
            Search scope
          </label>
          <select
            id={`${textareaId}-scope`}
            value={scope ?? ''}
            onChange={(e) => onScopeChange(e.target.value || null)}
            className="h-8 max-w-[11rem] truncate rounded-md border border-line bg-paper px-2 font-mono text-[11px] text-ink-soft hover:border-line-strong focus:outline-none focus-visible:border-ink/40 sm:max-w-[16rem]"
          >
            <option value="">All documents ({indexed.length})</option>
            {indexed.map((d) => (
              <option key={d.id} value={d.id}>
                {d.filename}
              </option>
            ))}
          </select>

          <span className="ml-auto hidden items-center gap-1 text-[11px] text-muted md:flex">
            <Kbd>Enter</Kbd> send · <Kbd>Shift</Kbd>+<Kbd>Enter</Kbd> newline
          </span>

          {busy ? (
            <button
              type="button"
              onClick={onStop}
              className="ml-auto inline-flex size-8 items-center justify-center rounded-md bg-ink text-paper transition-opacity hover:opacity-85 md:ml-0"
              aria-label="Stop generating"
              title="Stop generating"
            >
              <Square className="size-3 fill-current" />
            </button>
          ) : (
            <button
              type="submit"
              disabled={!canSend}
              className={cn(
                'ml-auto inline-flex size-8 items-center justify-center rounded-md transition-colors md:ml-0',
                canSend ? 'bg-ink text-paper hover:opacity-85' : 'bg-sunken text-muted',
              )}
              aria-label="Send message"
              title="Send"
            >
              <ArrowUp className="size-4" />
            </button>
          )}
        </div>
      </div>
      <p className="mt-2 text-center text-[11px] text-muted">
        Answers are generated from your documents and can be wrong. Check the cited sources.
      </p>
    </form>
  );
}
