import { useEffect, useRef } from 'react';
import { Link } from 'react-router';
import { ArrowUpRight, X } from 'lucide-react';
import type { Citation } from '../../api/types';
import { cn } from '../../lib/format';
import { IconButton } from '../ui/Button';

interface SourcesPanelProps {
  citations: Citation[];
  active: number | null;
  onClose: () => void;
}

/** Right-hand panel (desktop) / bottom sheet (mobile) showing the retrieved passages behind an answer. */
export function SourcesPanel({ citations, active, onClose }: SourcesPanelProps) {
  const activeRef = useRef<HTMLLIElement>(null);

  useEffect(() => {
    activeRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }, [active]);

  return (
    <aside
      aria-label="Sources"
      className="fixed inset-x-0 bottom-0 z-30 flex max-h-[70vh] animate-rise flex-col rounded-t-lg border-t border-line bg-surface shadow-[var(--shadow)] lg:static lg:max-h-none lg:w-[22rem] lg:rounded-none lg:border-l lg:border-t-0 lg:bg-paper lg:shadow-none"
    >
      <div className="flex h-14 shrink-0 items-center justify-between border-b border-line px-4">
        <div className="flex items-baseline gap-2">
          <h2 className="font-serif text-lg font-medium">Sources</h2>
          <span className="label-mono">{citations.length} passages</span>
        </div>
        <IconButton label="Close sources" size="sm" onClick={onClose}>
          <X className="size-4" />
        </IconButton>
      </div>
      <ol className="flex-1 space-y-3 overflow-y-auto p-4">
        {citations.map((c) => {
          const isActive = c.position === active;
          return (
            <li
              key={c.position}
              ref={isActive ? activeRef : undefined}
              className={cn(
                'rounded-md border bg-surface p-3 transition-colors',
                isActive ? 'border-accent shadow-[inset_2px_0_0_var(--accent)]' : 'border-line',
              )}
            >
              <div className="mb-2 flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-ink" title={c.fileName}>
                    <span className="mr-1.5 font-mono text-[11px] text-muted">[{c.position}]</span>
                    {c.fileName}
                  </p>
                  <p className="label-mono mt-0.5">
                    {c.pageNumber != null ? `Page ${c.pageNumber}` : 'No page info'}
                    {c.chunkIndex != null && ` · chunk ${c.chunkIndex}`}
                    {c.similarityScore != null && ` · ${(c.similarityScore * 100).toFixed(0)}% match`}
                  </p>
                </div>
                {c.documentId && (
                  <Link
                    to={`/library/${c.documentId}`}
                    className="shrink-0 rounded p-1 text-muted hover:bg-sunken hover:text-ink"
                    aria-label={`Open ${c.fileName} in library`}
                    title="Open document"
                  >
                    <ArrowUpRight className="size-4" />
                  </Link>
                )}
              </div>
              <blockquote className="border-l-2 border-line-strong pl-3 text-[13px] leading-relaxed whitespace-pre-wrap text-ink-soft">
                {c.snippet}
              </blockquote>
            </li>
          );
        })}
      </ol>
    </aside>
  );
}
