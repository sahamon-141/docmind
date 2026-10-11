import { Monitor, Moon, Sun } from 'lucide-react';
import { cn } from '../../lib/format';
import { useTheme, type ThemePreference } from '../../providers/theme-context';

const OPTIONS: { value: ThemePreference; label: string; Icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', Icon: Sun },
  { value: 'dark', label: 'Dark', Icon: Moon },
  { value: 'system', label: 'System', Icon: Monitor },
];

/** Segmented theme control. `compact` shows icons only. */
export function ThemeToggle({ compact = false }: { compact?: boolean }) {
  const { preference, setPreference } = useTheme();
  return (
    <div role="radiogroup" aria-label="Theme" className="inline-flex rounded-md border border-line p-0.5">
      {OPTIONS.map(({ value, label, Icon }) => {
        const active = preference === value;
        return (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={active}
            aria-label={label}
            title={label}
            onClick={() => setPreference(value)}
            className={cn(
              'inline-flex items-center gap-1.5 rounded-[4px] font-mono text-[10.5px] tracking-[0.04em] uppercase transition-colors',
              compact ? 'size-7 justify-center' : 'h-7 px-2.5',
              active ? 'bg-ink text-paper' : 'text-muted hover:text-ink',
            )}
          >
            <Icon className="size-3.5" aria-hidden />
            {!compact && label}
          </button>
        );
      })}
    </div>
  );
}
