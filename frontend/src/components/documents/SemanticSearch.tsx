import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Search } from 'lucide-react';
import { documentsApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import { Button } from '../ui/Button';

interface SemanticSearchProps {
  documentId?: string;
  placeholder?: string;
}

/** Vector search over the signed-in user's own chunks (optionally one document). */
export function SemanticSearch({ documentId, placeholder = 'Search the meaning of your documents…' }: SemanticSearchProps) {
  const [query, setQuery] = useState('');
  const search = useMutation({
    mutationFn: (q: string) => documentsApi.search({ query: q, documentId, topK: 8 }),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (query.trim()) search.mutate(query.trim());
  };

  return (
    <div className="space-y-3">
      <form onSubmit={submit} className="flex gap-2" role="search">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" aria-hidden />
          <label htmlFor={`semantic-${documentId ?? 'all'}`} className="sr-only">
            Semantic search
          </label>
          <input
            id={`semantic-${documentId ?? 'all'}`}
            value={query}
            maxLength={2000}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={placeholder}
            className="h-10 w-full rounded-md border border-line-strong bg-surface pr-3 pl-9 text-sm text-ink placeholder:text-muted focus:border-ink/40 focus:outline-none"
          />
        </div>
        <Button type="submit" variant="secondary" loading={search.isPending} disabled={!query.trim()}>
          Search
        </Button>
      </form>

      {search.isError && <p className="text-sm text-danger">{errorMessage(search.error)}</p>}

      {search.data && (
        <div className="space-y-2" aria-live="polite">
          <p className="label-mono">
            {search.data.totalMatches} {search.data.totalMatches === 1 ? 'match' : 'matches'}
          </p>
          {search.data.matches.length === 0 ? (
            <p className="text-sm text-ink-soft">No passages matched. Try different wording, or check that the document is indexed.</p>
          ) : (
            <ol className="space-y-2">
              {search.data.matches.map((m) => (
                <li key={`${m.documentId}-${m.chunkIndex}-${m.position}`} className="rounded-md border border-line bg-surface p-3">
                  <div className="mb-1.5 flex items-center justify-between gap-3">
                    {m.documentId ? (
                      <Link to={`/library/${m.documentId}`} className="truncate text-sm font-medium text-ink hover:underline">
                        {m.fileName}
                      </Link>
                    ) : (
                      <span className="truncate text-sm font-medium">{m.fileName}</span>
                    )}
                    <span className="label-mono shrink-0">
                      {m.pageNumber != null && `p.${m.pageNumber} · `}
                      {m.similarityScore != null ? `${(m.similarityScore * 100).toFixed(0)}%` : ''}
                    </span>
                  </div>
                  <p className="line-clamp-4 text-[13px] leading-relaxed whitespace-pre-wrap text-ink-soft">{m.snippet}</p>
                </li>
              ))}
            </ol>
          )}
        </div>
      )}
    </div>
  );
}
