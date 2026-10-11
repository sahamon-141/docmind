import { useId, useState, type DragEvent } from 'react';
import { CircleAlert, CircleCheck, FileUp, X } from 'lucide-react';
import type { UploadItem } from '../../features/documents/useUploader';
import { cn, formatBytes, MAX_UPLOAD_BYTES } from '../../lib/format';
import { Spinner } from '../ui/Spinner';

interface UploadDropzoneProps {
  onFiles: (files: File[]) => void;
  items: UploadItem[];
  onCancel: (id: string) => void;
  onClear: () => void;
}

export function UploadDropzone({ onFiles, items, onCancel, onClear }: UploadDropzoneProps) {
  const inputId = useId();
  const [dragging, setDragging] = useState(false);

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const files = Array.from(e.dataTransfer.files);
    if (files.length) onFiles(files);
  };

  const finished = items.some((i) => i.phase === 'done' || i.phase === 'error');

  return (
    <div className="space-y-3">
      <label
        htmlFor={inputId}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border border-dashed px-6 py-10 text-center transition-colors',
          dragging ? 'border-accent bg-accent-soft' : 'border-line-strong hover:border-ink/40 hover:bg-surface/50',
        )}
      >
        <FileUp className={cn('size-6', dragging ? 'text-accent-ink' : 'text-muted')} aria-hidden />
        <span className="text-sm font-medium text-ink">
          Drop files here or <span className="underline underline-offset-4">browse</span>
        </span>
        <span className="label-mono">PDF · DOCX · PPTX · TXT · MD · CSV · HTML · RTF · ODT — up to {formatBytes(MAX_UPLOAD_BYTES)}</span>
        <input
          id={inputId}
          type="file"
          multiple
          className="sr-only"
          accept=".pdf,.txt,.md,.markdown,.csv,.docx,.doc,.pptx,.odt,.rtf,.html,.htm"
          onChange={(e) => {
            const files = Array.from(e.target.files ?? []);
            if (files.length) onFiles(files);
            e.target.value = '';
          }}
        />
      </label>

      {items.length > 0 && (
        <div className="rounded-lg border border-line bg-surface">
          <div className="flex items-center justify-between border-b border-line px-4 py-2">
            <span className="label-mono">Uploads</span>
            {finished && (
              <button type="button" onClick={onClear} className="text-xs text-muted hover:text-ink">
                Clear finished
              </button>
            )}
          </div>
          <ul className="divide-y divide-line" aria-live="polite">
            {items.map((item) => (
              <li key={item.id} className="flex items-center gap-3 px-4 py-2.5">
                <UploadIcon phase={item.phase} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-ink">{item.name}</p>
                  <p className={cn('text-xs', item.phase === 'error' ? 'text-danger' : 'text-muted')}>
                    {item.phase === 'queued' && 'Waiting…'}
                    {item.phase === 'uploading' && `Uploading · ${Math.round(item.progress * 100)}% of ${formatBytes(item.size)}`}
                    {item.phase === 'processing' && 'Parsing, chunking and embedding…'}
                    {item.phase === 'done' && `Indexed · ${item.chunks ?? 0} chunks`}
                    {item.phase === 'error' && item.error}
                  </p>
                  {(item.phase === 'uploading' || item.phase === 'processing') && (
                    <div className="mt-1.5 h-0.5 overflow-hidden rounded bg-sunken" role="progressbar" aria-valuenow={Math.round(item.progress * 100)} aria-valuemin={0} aria-valuemax={100} aria-label={`Upload progress for ${item.name}`}>
                      <div
                        className={cn('h-full bg-ink transition-[width] duration-200', item.phase === 'processing' && 'animate-pulse')}
                        style={{ width: `${Math.max(4, item.progress * 100)}%` }}
                      />
                    </div>
                  )}
                </div>
                {item.phase === 'uploading' && (
                  <button type="button" onClick={() => onCancel(item.id)} className="rounded p-1 text-muted hover:text-ink" aria-label={`Cancel ${item.name}`}>
                    <X className="size-4" />
                  </button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function UploadIcon({ phase }: { phase: UploadItem['phase'] }) {
  if (phase === 'done') return <CircleCheck className="size-4 shrink-0 text-forest" aria-label="Indexed" />;
  if (phase === 'error') return <CircleAlert className="size-4 shrink-0 text-danger" aria-label="Failed" />;
  return <Spinner className="shrink-0 text-muted" />;
}
