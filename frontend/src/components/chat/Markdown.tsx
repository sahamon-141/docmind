import { memo, useMemo } from 'react';
import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';

const CITE_PREFIX = '#cite-';

interface MarkdownProps {
  content: string;
  /** citation positions that actually exist for this message; other [n] stay plain text */
  citationCount: number;
  onCite?: (position: number) => void;
}

/**
 * Renders assistant Markdown. Raw HTML is not rendered (react-markdown default) and link URLs are
 * sanitised. Bracketed citation markers like [2] become buttons, but only for sources that were
 * really retrieved, so a model-invented [9] is never presented as a citation.
 */
export const Markdown = memo(function Markdown({ content, citationCount, onCite }: MarkdownProps) {
  const prepared = useMemo(
    () =>
      content.replace(/\[(\d{1,2})\](?!\()/g, (match, n: string) => {
        const position = Number(n);
        return position >= 1 && position <= citationCount ? `[${n}](${CITE_PREFIX}${n})` : match;
      }),
    [content, citationCount],
  );

  const components = useMemo<Components>(
    () => ({
      a({ href, children }) {
        if (href?.startsWith(CITE_PREFIX)) {
          const position = Number(href.slice(CITE_PREFIX.length));
          return (
            <button
              type="button"
              onClick={() => onCite?.(position)}
              className="mx-0.5 inline-flex h-[1.15rem] min-w-[1.15rem] -translate-y-px items-center justify-center rounded border border-line-strong bg-surface px-1 align-middle font-mono text-[10px] text-ink-soft transition-colors hover:border-accent hover:text-accent-ink"
              aria-label={`Show source ${position}`}
            >
              {position}
            </button>
          );
        }
        return (
          <a href={href} target="_blank" rel="noopener noreferrer">
            {children}
          </a>
        );
      },
    }),
    [onCite],
  );

  return (
    <div className="prose-answer">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {prepared}
      </ReactMarkdown>
    </div>
  );
});
