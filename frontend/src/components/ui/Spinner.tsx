import { LoaderCircle } from 'lucide-react';
import { cn } from '../../lib/format';

export function Spinner({ className, label }: { className?: string; label?: string }) {
  return (
    <LoaderCircle
      className={cn('size-4 animate-spin', className)}
      aria-hidden={label ? undefined : true}
      aria-label={label}
      role={label ? 'status' : undefined}
    />
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('animate-pulse rounded bg-sunken', className)} />;
}

export function FullPageLoader({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex h-full items-center justify-center gap-3 text-muted" role="status">
      <Spinner />
      <span className="label-mono">{label}</span>
    </div>
  );
}
