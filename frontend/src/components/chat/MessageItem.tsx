import { useState } from 'react';
import { Check, Copy, FileText, RotateCcw, TriangleAlert } from 'lucide-react';
import type { Citation, Message } from '../../api/types';
import { cn, formatDuration, formatTime } from '../../lib/format';
import { Button, IconButton } from '../ui/Button';
import { Markdown } from './Markdown';

export function UserMessage({ content, createdAt, pending }: { content: string; createdAt?: string; pending?: boolean }) {
  return (
    <article className="flex animate-rise flex-col items-end gap-1.5" aria-label="Your message">
      <div className="label-mono flex items-center gap-2">
        You{createdAt && <time dateTime={createdAt}>· {formatTime(createdAt)}</time>}
        {pending && <span className="text-muted/70">· sending</span>}
      </div>
      <div className="max-w-[85%] whitespace-pre-wrap rounded-lg border border-line bg-surface px-4 py-3 text-[15px] leading-relaxed text-ink">
        {content}
      </div>
    </article>
  );
}

interface AssistantMessageProps {
  message?: Message;
  streamingText?: string;
  streamingSources?: Citation[];
  onCite: (citations: Citation[], position: number) => void;
  onRetry?: () => void;
  retrying?: boolean;
}

export function AssistantMessage({ message, streamingText, streamingSources, onCite, onRetry, retrying }: AssistantMessageProps) {
  const streaming = !message;
  const content = message?.content ?? streamingText ?? '';
  const citations = message?.citations ?? streamingSources ?? [];
  const failed = message?.status === 'FAILED';

  return (
    <article className="group flex animate-rise flex-col gap-2" aria-label="DocMind answer" aria-busy={streaming || undefined}>
      <div className="label-mono flex items-center gap-2">
        <span className="text-ink">DocMind</span>
        {message && <time dateTime={message.createdAt}>· {formatTime(message.createdAt)}</time>}
        {message?.responseTimeMs != null && !failed && <span>· {formatDuration(message.responseTimeMs)}</span>}
        {streaming && <span className="text-accent-ink">· {content ? 'writing' : 'searching your documents'}</span>}
      </div>

      {failed ? (
        <div className="flex flex-col gap-3 rounded-md border border-danger/30 bg-danger-soft px-4 py-3 text-sm">
          <p className="flex items-start gap-2 text-danger">
            <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
            <span>{content || 'The answer could not be generated.'}</span>
          </p>
          {onRetry && (
            <div>
              <Button size="sm" variant="secondary" icon={<RotateCcw className="size-3.5" />} onClick={onRetry} loading={retrying}>
                Retry
              </Button>
            </div>
          )}
        </div>
      ) : content ? (
        <div className="relative">
          <Markdown content={content} citationCount={citations.length} onCite={(p) => onCite(citations, p)} />
          {streaming && <span className="ml-0.5 inline-block h-4 w-[2px] translate-y-0.5 animate-blink bg-ink" aria-hidden />}
        </div>
      ) : (
        <ThinkingIndicator />
      )}

      {citations.length > 0 && !failed && <CitationChips citations={citations} onSelect={(p) => onCite(citations, p)} />}

      {message && !failed && <MessageActions content={content} />}
    </article>
  );
}

function ThinkingIndicator() {
  return (
    <div className="flex items-center gap-1.5 py-2" role="status" aria-label="Generating answer">
      {[0, 1, 2].map((i) => (
        <span key={i} className="size-1.5 animate-pulse rounded-full bg-muted" style={{ animationDelay: `${i * 160}ms` }} />
      ))}
    </div>
  );
}

function CitationChips({ citations, onSelect }: { citations: Citation[]; onSelect: (position: number) => void }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5 pt-1">
      <span className="label-mono mr-1">Sources</span>
      {citations.map((c) => (
        <button
          key={c.position}
          type="button"
          onClick={() => onSelect(c.position)}
          className="inline-flex max-w-[16rem] items-center gap-1.5 rounded border border-line bg-surface px-2 py-1 text-xs text-ink-soft transition-colors hover:border-line-strong hover:text-ink"
          title={c.fileName}
        >
          <span className="font-mono text-[10px] text-muted">{c.position}</span>
          <FileText className="size-3 shrink-0 text-muted" aria-hidden />
          <span className="truncate">{c.fileName}</span>
          {c.pageNumber != null && <span className="shrink-0 font-mono text-[10px] text-muted">p.{c.pageNumber}</span>}
        </button>
      ))}
    </div>
  );
}

function MessageActions({ content }: { content: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  };
  return (
    <div className={cn('flex gap-1 opacity-100 transition-opacity sm:opacity-0 sm:group-hover:opacity-100 sm:group-focus-within:opacity-100')}>
      <IconButton label={copied ? 'Copied' : 'Copy answer'} size="sm" onClick={copy}>
        {copied ? <Check className="size-3.5 text-forest" /> : <Copy className="size-3.5" />}
      </IconButton>
    </div>
  );
}
