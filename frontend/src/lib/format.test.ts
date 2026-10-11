import { describe, expect, it } from 'vitest';
import { formatBytes, groupByDate } from './format';
import { validateRegistration } from './validation';

describe('groupByDate', () => {
  const now = new Date(2026, 5, 15, 12, 0);
  const at = (daysAgo: number) => new Date(2026, 5, 15 - daysAgo, 9, 0).toISOString();

  it('buckets conversations into ordered date groups', () => {
    const items = [at(0), at(1), at(3), at(12), at(90)].map((d, i) => ({ id: i, d }));
    const groups = groupByDate(items, (x) => x.d, now);
    expect(groups.map((g) => g.label)).toEqual(['Today', 'Yesterday', 'Previous 7 days', 'Previous 30 days', 'Older']);
    expect(groups[0]?.items.map((x) => x.id)).toEqual([0]);
  });

  it('omits empty groups', () => {
    expect(groupByDate([{ d: at(0) }], (x) => x.d, now).map((g) => g.label)).toEqual(['Today']);
  });
});

describe('formatBytes', () => {
  it('formats sizes', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
    expect(formatBytes(null)).toBe('—');
  });
});

describe('validateRegistration', () => {
  it('mirrors the backend constraints', () => {
    expect(validateRegistration({ username: 'a b', email: 'nope', password: 'short', confirm: 'x' })).toEqual({
      username: "Only letters, digits, '.', '_' and '-'.",
      email: 'Enter a valid email address.',
      password: 'Use at least 8 characters.',
      confirm: 'Passwords do not match.',
    });
    expect(
      validateRegistration({ username: 'ada.l', email: 'ada@example.com', password: 'long-enough', confirm: 'long-enough' }),
    ).toEqual({});
  });
});
