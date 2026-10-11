export function cn(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(' ');
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return '—';
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`;
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function formatDuration(ms: number | null | undefined): string | null {
  if (ms == null) return null;
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}

export type DateGroup = 'Today' | 'Yesterday' | 'Previous 7 days' | 'Previous 30 days' | 'Older';

const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

export function dateGroup(iso: string, now: Date = new Date()): DateGroup {
  const days = Math.floor((startOfDay(now) - startOfDay(new Date(iso))) / 86_400_000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  if (days < 7) return 'Previous 7 days';
  if (days < 30) return 'Previous 30 days';
  return 'Older';
}

/** Groups items by date bucket, preserving input order (expected newest first). */
export function groupByDate<T>(items: T[], getDate: (item: T) => string, now: Date = new Date()) {
  const order: DateGroup[] = ['Today', 'Yesterday', 'Previous 7 days', 'Previous 30 days', 'Older'];
  const groups = new Map<DateGroup, T[]>();
  for (const item of items) {
    const key = dateGroup(getDate(item), now);
    groups.set(key, [...(groups.get(key) ?? []), item]);
  }
  return order.flatMap((label) => {
    const items = groups.get(label);
    return items ? [{ label, items }] : [];
  });
}

export function fileExtension(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
}

export const SUPPORTED_EXTENSIONS = ['pdf', 'txt', 'md', 'markdown', 'csv', 'docx', 'doc', 'pptx', 'odt', 'rtf', 'html', 'htm'];
export const MAX_UPLOAD_BYTES = 25 * 1024 * 1024;

export function newId(): string {
  return crypto.randomUUID();
}
