import { Link } from 'react-router';
import { ArrowRight, Upload } from 'lucide-react';
import type { DocumentMetadata } from '../../api/types';

const SUGGESTIONS = [
  { label: 'Summarise', prompt: 'Summarise the key points of my documents in five bullet points.' },
  { label: 'Extract', prompt: 'List every date, deadline and number mentioned, with its source.' },
  { label: 'Compare', prompt: 'What are the main differences between my documents?' },
  { label: 'Explain', prompt: 'Explain the most technical section in plain language.' },
];

interface EmptyChatProps {
  documents: DocumentMetadata[];
  loadingDocuments: boolean;
  onPick: (prompt: string) => void;
}

export function EmptyChat({ documents, loadingDocuments, onPick }: EmptyChatProps) {
  const indexed = documents.filter((d) => d.status === 'INDEXED').length;
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col justify-center px-4 py-10 sm:px-6">
      <span className="label-mono mb-4">New conversation</span>
      <h1 className="font-serif text-4xl leading-[1.1] font-normal tracking-[-0.02em] text-ink sm:text-5xl">
        Ask your documents,
        <br />
        <em className="font-light text-accent-ink">get cited answers.</em>
      </h1>
      <p className="mt-5 max-w-xl text-[15px] leading-relaxed text-ink-soft">
        DocMind searches only the files you have uploaded, answers from the most relevant passages and shows exactly
        where each claim comes from.
      </p>

      {!loadingDocuments && indexed === 0 ? (
        <div className="mt-10 flex flex-col items-start gap-3 border-t border-line pt-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-ink-soft">You have no indexed documents yet. Upload one to get grounded answers.</p>
          <Link
            to="/library"
            className="inline-flex h-10 shrink-0 items-center gap-2 rounded-md bg-ink px-4 text-sm font-medium whitespace-nowrap text-paper hover:opacity-85"
          >
            <Upload className="size-4" /> Upload documents
          </Link>
        </div>
      ) : (
        <div className="mt-10 grid gap-px overflow-hidden rounded-lg border border-line bg-line sm:grid-cols-2">
          {SUGGESTIONS.map((s, i) => (
            <button
              key={s.label}
              type="button"
              onClick={() => onPick(s.prompt)}
              className="group flex flex-col gap-2 bg-paper p-4 text-left transition-colors hover:bg-surface"
            >
              <span className="label-mono">
                {s.label} <span className="text-muted/60">// 0{i + 1}</span>
              </span>
              <span className="flex items-start justify-between gap-3 text-sm text-ink">
                {s.prompt}
                <ArrowRight className="mt-0.5 size-4 shrink-0 text-muted transition-transform group-hover:translate-x-0.5 group-hover:text-ink" />
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
