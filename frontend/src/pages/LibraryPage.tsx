import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { FileText, FolderOpen, RotateCw, Trash2 } from 'lucide-react';
import { errorMessage } from '../api/errors';
import type { DocumentMetadata } from '../api/types';
import { DocumentDetails } from '../components/documents/DocumentDetails';
import { DocumentStatusBadge } from '../components/documents/DocumentStatusBadge';
import { SemanticSearch } from '../components/documents/SemanticSearch';
import { UploadDropzone } from '../components/documents/UploadDropzone';
import { Button, IconButton } from '../components/ui/Button';
import { ConfirmDialog } from '../components/ui/Dialog';
import { EmptyState } from '../components/ui/Misc';
import { Skeleton } from '../components/ui/Spinner';
import { useUploader } from '../features/documents/useUploader';
import { useDeleteDocument, useDocuments } from '../hooks/queries';
import { cn, fileExtension, formatBytes, formatDate } from '../lib/format';
import { useToast } from '../providers/toast-context';

export default function LibraryPage() {
  const { documentId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const documents = useDocuments();
  const remove = useDeleteDocument();
  const uploader = useUploader((r) => toast.success(`${r.filename} indexed`));
  const [deleting, setDeleting] = useState<{ id: string; name: string } | null>(null);

  const docs = documents.data ?? [];
  const indexed = docs.filter((d) => d.status === 'INDEXED').length;

  const confirmDelete = () => {
    if (!deleting) return;
    const target = deleting;
    remove.mutate(target.id, {
      onSuccess: () => {
        toast.success(`${target.name} deleted`);
        if (documentId === target.id) navigate('/library');
      },
      onError: (e) => toast.error(errorMessage(e)),
      onSettled: () => setDeleting(null),
    });
  };

  return (
    <div className="flex min-h-0 flex-1">
      <div className="min-w-0 flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-5xl space-y-10 px-4 py-8 sm:px-8 sm:py-10">
          <header className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <span className="label-mono">
                Library <span className="text-muted/60">// 02</span>
              </span>
              <h1 className="mt-2 font-serif text-4xl font-normal tracking-[-0.02em]">
                Your <em className="font-light text-accent-ink">documents</em>
              </h1>
              <p className="mt-2 text-sm text-ink-soft">
                {documents.isLoading ? 'Loading…' : `${docs.length} uploaded · ${indexed} indexed and searchable`}
              </p>
            </div>
          </header>

          <UploadDropzone
            onFiles={(files) => void uploader.addFiles(files)}
            items={uploader.items}
            onCancel={uploader.cancel}
            onClear={uploader.clearFinished}
          />

          {indexed > 0 && (
            <section className="space-y-3" aria-labelledby="semantic-heading">
              <h2 id="semantic-heading" className="label-mono">
                Semantic search
              </h2>
              <SemanticSearch />
            </section>
          )}

          <section aria-labelledby="documents-heading" className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 id="documents-heading" className="label-mono">
                All documents
              </h2>
              <IconButton label="Refresh" size="sm" onClick={() => void documents.refetch()} disabled={documents.isFetching}>
                <RotateCw className={cn('size-3.5', documents.isFetching && 'animate-spin')} />
              </IconButton>
            </div>

            {documents.isLoading ? (
              <div className="space-y-2">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="h-14 w-full" />
                ))}
              </div>
            ) : documents.isError ? (
              <EmptyState
                title="Could not load your documents"
                description={errorMessage(documents.error)}
                action={<Button variant="secondary" onClick={() => void documents.refetch()}>Try again</Button>}
              />
            ) : docs.length === 0 ? (
              <EmptyState
                className="rounded-lg border border-line"
                icon={<FolderOpen className="size-8" />}
                title="No documents yet"
                description="Upload a PDF, Word file or text document above. It will be parsed, split into passages and indexed for search."
              />
            ) : (
              <DocumentTable
                documents={docs}
                selectedId={documentId}
                onOpen={(id) => navigate(`/library/${id}`)}
                onDelete={(d) => setDeleting({ id: d.id, name: d.filename })}
              />
            )}
          </section>
        </div>
      </div>

      {documentId && (
        <DocumentDetails documentId={documentId} onClose={() => navigate('/library')} onDelete={(id, name) => setDeleting({ id, name })} />
      )}

      <ConfirmDialog
        open={deleting !== null}
        title="Delete document?"
        description={
          <>
            <span className="font-medium text-ink">{deleting?.name}</span> and all of its indexed passages will be removed. Past
            answers keep their citation snapshots.
          </>
        }
        confirmLabel="Delete"
        loading={remove.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
}

interface DocumentTableProps {
  documents: DocumentMetadata[];
  selectedId?: string;
  onOpen: (id: string) => void;
  onDelete: (doc: DocumentMetadata) => void;
}

function DocumentTable({ documents, selectedId, onOpen, onDelete }: DocumentTableProps) {
  return (
    <div className="overflow-hidden rounded-lg border border-line">
      <div className="hidden grid-cols-[minmax(0,1fr)_6rem_5rem_6rem_7rem_2.5rem] gap-4 border-b border-line bg-sunken/50 px-4 py-2 md:grid">
        {['Name', 'Size', 'Chunks', 'Uploaded', 'Status', ''].map((h) => (
          <span key={h} className="label-mono">
            {h}
          </span>
        ))}
      </div>
      <ul className="divide-y divide-line bg-surface">
        {documents.map((d) => (
          <li
            key={d.id}
            className={cn(
              'group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1 px-4 py-3 transition-colors md:grid-cols-[minmax(0,1fr)_6rem_5rem_6rem_7rem_2.5rem]',
              selectedId === d.id ? 'bg-accent-soft' : 'hover:bg-sunken/40',
            )}
          >
            <button type="button" onClick={() => onOpen(d.id)} className="flex min-w-0 items-center gap-3 text-left">
              <span className="flex size-8 shrink-0 items-center justify-center rounded border border-line bg-paper font-mono text-[9px] text-muted uppercase">
                {fileExtension(d.filename).slice(0, 4) || <FileText className="size-3.5" />}
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium text-ink group-hover:underline">{d.filename}</span>
                <span className="block truncate text-xs text-muted md:hidden">
                  {formatBytes(d.fileSize)} · {formatDate(d.createdAt)}
                </span>
              </span>
            </button>
            <span className="hidden font-mono text-xs text-ink-soft md:block">{formatBytes(d.fileSize)}</span>
            <span className="hidden font-mono text-xs text-ink-soft md:block">{d.totalChunks ?? '—'}</span>
            <span className="hidden font-mono text-xs text-ink-soft md:block">{formatDate(d.createdAt)}</span>
            <span className="justify-self-end md:justify-self-start">
              <DocumentStatusBadge status={d.status} />
            </span>
            <span className="hidden md:block">
              <IconButton label={`Delete ${d.filename}`} size="sm" onClick={() => onDelete(d)}>
                <Trash2 className="size-3.5" />
              </IconButton>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
