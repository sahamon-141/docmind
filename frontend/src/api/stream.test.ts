import { describe, expect, it } from 'vitest';
import { parseSseBuffer } from './stream';

describe('parseSseBuffer', () => {
  it('parses complete events and keeps the unfinished remainder', () => {
    const { events, rest } = parseSseBuffer('event:meta\ndata:{"a":1}\n\nevent:token\ndata:{"text":"Hel');
    expect(events).toEqual([{ event: 'meta', data: '{"a":1}' }]);
    expect(rest).toBe('event:token\ndata:{"text":"Hel');

    const next = parseSseBuffer(rest + 'lo"}\n\n');
    expect(next.events).toEqual([{ event: 'token', data: '{"text":"Hello"}' }]);
    expect(next.rest).toBe('');
  });

  it('handles CRLF line endings and multi-line data', () => {
    const { events } = parseSseBuffer('event:done\r\ndata: line1\r\ndata: line2\r\n\r\n');
    expect(events).toEqual([{ event: 'done', data: 'line1\nline2' }]);
  });

  it('ignores comments and keep-alive blocks without data', () => {
    expect(parseSseBuffer(':keepalive\n\n').events).toEqual([]);
  });
});
