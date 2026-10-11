import type { ReactNode } from 'react';
import { Logo } from '../components/ui/Misc';
import { ThemeToggle } from '../components/layout/ThemeToggle';

const POINTS = [
  ['Grounded', 'Answers come from passages in your own files, each one cited.'],
  ['Private', 'Every document, vector and conversation is scoped to your account.'],
  ['Persistent', 'Conversations are saved, searchable and ready to resume.'],
];

export function AuthLayout({ title, subtitle, children }: { title: ReactNode; subtitle: ReactNode; children: ReactNode }) {
  return (
    <div className="grid min-h-full lg:grid-cols-[1.1fr_1fr]">
      <aside className="hidden flex-col justify-between border-r border-line px-12 py-10 lg:flex">
        <Logo to="/login" />
        <div>
          <span className="label-mono">AI document workspace</span>
          <p className="mt-4 font-serif text-5xl leading-[1.08] font-normal tracking-[-0.02em]">
            Read less.
            <br />
            <em className="font-light text-accent-ink">Know more.</em>
          </p>
          <dl className="mt-12 max-w-md divide-y divide-line border-y border-line">
            {POINTS.map(([label, text], i) => (
              <div key={label} className="grid grid-cols-[7.5rem_1fr] gap-4 py-3.5">
                <dt className="label-mono pt-0.5">
                  {label} <span className="text-muted/60">// 0{i + 1}</span>
                </dt>
                <dd className="text-sm text-ink-soft">{text}</dd>
              </div>
            ))}
          </dl>
        </div>
        <ThemeToggle />
      </aside>

      <main className="flex flex-col px-5 py-8 sm:px-10">
        <div className="flex items-center justify-between lg:hidden">
          <Logo to="/login" />
          <ThemeToggle compact />
        </div>
        <div className="m-auto w-full max-w-sm py-10">
          <h1 className="font-serif text-3xl font-normal tracking-[-0.01em]">{title}</h1>
          <p className="mt-2 text-sm text-ink-soft">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </main>
    </div>
  );
}
