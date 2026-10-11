import { useNavigate } from 'react-router';
import { MessageSquare, Trash2, TriangleAlert, X } from 'lucide-react';
import { isApiError } from '../../api/errors';
import { useDocument } from '../../hooks/queries';
import { formatBytes, formatDateTime } from '../../lib/format';
import { Button, IconButton } from '../ui/Button';
import { Skeleton } from '../ui/Spinner';
import { DocumentStatusBadge } from './DocumentStatusBadge';
import { SemanticSearch } from './SemanticSearch';

interface DocumentDetailsProps {
  documentId: string;
  onClose: () => void;
  onDelete: (id: string, name: string) => void;
}

export function DocumentDetails({ documentId, onClose, onDelete }: DocumentDetailsProps) {
  const navigate = useNavigate();
  const { data: doc, isLoading, error } = useDocument(documentId);

  return (
    <aside
      aria-label="Document details"
      className="fixed inset-0 z-30 flex animate-fade-in flex-col bg-paper lg:static lg:inset-auto lg:w-[26rem] lg:shrink-0 lg:border-l lg:border-line"
    >
      <div className="flex h-14 shrink-0 items-center justify-between border-b border-line px-5">
        <span className="label-mono">Document</span>
        <IconButton label="Close details" size="sm" onClick={onClose}>
          <X className="size-4" />
        </IconButton>
      </div>

      <div className="flex-1 space-y-6 overflow-y-auto p-5">
        {isLoading ? (
          <div className="space-y-3">
            <Skeleton className="h-7 w-3/4" />
            <Skeleton className="h-24 w-full" />
          </div>
        ) : isApiError(error) && error.status === 404 ? (
          <p className="text-sm text-ink-soft">This document does not exist or was deleted.</p>
        ) : error || !doc ? (
          <p className="text-sm text-danger">Could not load the document.</p>
        ) : (
          <>
            <div className="space-y-2">
              <h2 className="font-serif text-2xl leading-tight font-medium tracking-tight break-words">{doc.filename}</h2>
              <DocumentStatusBadge status={doc.status} />
            </div>

            {doc.status === 'FAILED' && doc.errorMessage && (
              <p className="flex gap-2 rounded-md border border-danger/30 bg-danger-soft p-3 text-sm text-danger">
                <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
                {doc.errorMessage}
              </p>
            )}

            <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-md border border-line bg-line text-sm">
              {[
                ['Type', doc.contentType],
                ['Size', formatBytes(doc.fileSize)],
                ['Pages', doc.totalPages ?? '—'],
                ['Chunks', doc.totalChunks ?? '—'],
                ['Uploaded', formatDateTime(doc.createdAt)],
                ['Updated', doc.updatedAt ? formatDateTime(doc.updatedAt) : '—'],
              ].map(([label, value]) => (
                <div key={label as string} className="bg-surface p-3">
                  <dt className="label-mono">{label}</dt>
                  <dd className="mt-1 truncate text-ink" title={String(value)}>
                    {value}
                  </dd>
                </div>
              ))}
            </dl>

            <div className="flex flex-wrap gap-2">
              <Button
                icon={<MessageSquare className="size-4" />}
                disabled={doc.status !== 'INDEXED'}
                onClick={() => navigate('/chat', { state: { scope: doc.id } })}
              >
                Ask about this document
              </Button>
              <Button variant="secondary" icon={<Trash2 className="size-4" />} onClick={() => onDelete(doc.id, doc.filename)}>
                Delete
              </Button>
            </div>

            {doc.status === 'INDEXED' && (
              <section className="space-y-3 border-t border-line pt-5">
                <h3 className="label-mono">Search within this document</h3>
                <SemanticSearch documentId={doc.id} placeholder="Find a passage…" />
              </section>
            )}
          </>
        )}
      </div>
    </aside>
  );
}
