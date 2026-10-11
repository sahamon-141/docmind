import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { cn } from '../../lib/format';

export function Logo({ className, to = '/chat' }: { className?: string; to?: string }) {
  return (
    <Link to={to} className={cn('inline-flex items-baseline font-serif text-[22px] font-medium tracking-tight text-ink', className)}>
      DocMind<span className="text-accent">.</span>
    </Link>
  );
}

type Tone = 'neutral' | 'positive' | 'warning' | 'negative';

const DOT: Record<Tone, string> = {
  neutral: 'bg-muted',
  positive: 'bg-forest',
  warning: 'bg-accent',
  negative: 'bg-danger',
};

/** Dot + mono label status indicator. */
export function StatusDot({ tone, label, pulse }: { tone: Tone; label: string; pulse?: boolean }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="relative flex size-1.5">
        {pulse && <span className={cn('absolute inline-flex size-full animate-ping rounded-full opacity-60', DOT[tone])} />}
        <span className={cn('relative inline-flex size-1.5 rounded-full', DOT[tone])} />
      </span>
      <span className="font-mono text-[10.5px] uppercase tracking-[0.08em] text-ink-soft">{label}</span>
    </span>
  );
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon?: ReactNode;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex flex-col items-center gap-3 px-6 py-14 text-center', className)}>
      {icon && <div className="text-muted">{icon}</div>}
      <h3 className="font-serif text-xl font-medium tracking-tight">{title}</h3>
      {description && <p className="max-w-sm text-sm text-ink-soft">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function Kbd({ children }: { children: ReactNode }) {
  return (
    <kbd className="rounded border border-line-strong bg-surface px-1 py-px font-mono text-[10px] text-muted">{children}</kbd>
  );
}

export function SectionLabel({ index, children }: { index?: string; children: ReactNode }) {
  return (
    <span className="label-mono">
      {children}
      {index && <span className="text-muted/70"> // {index}</span>}
    </span>
  );
}
