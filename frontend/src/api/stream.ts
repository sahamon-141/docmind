import { openStream } from './client';
import type { ChatRequest, Citation, ConversationSummary, Message } from './types';

export type StreamEvent =
  | { type: 'meta'; conversation: ConversationSummary; userMessage: Message }
  | { type: 'sources'; citations: Citation[] }
  | { type: 'token'; text: string }
  | { type: 'done'; assistantMessage: Message }
  | { type: 'error'; message: string; assistantMessage?: Message };

/**
 * Splits buffered text/event-stream data into complete events plus the unfinished remainder.
 * Exported for tests.
 */
export function parseSseBuffer(buffer: string): { events: { event: string; data: string }[]; rest: string } {
  const blocks = buffer.replace(/\r\n/g, '\n').split('\n\n');
  const rest = blocks.pop() ?? '';
  const events: { event: string; data: string }[] = [];
  for (const block of blocks) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (data.length) events.push({ event, data: data.join('\n') });
  }
  return { events, rest };
}

function toEvent(event: string, data: string): StreamEvent | null {
  const payload = JSON.parse(data) as Record<string, unknown>;
  switch (event) {
    case 'meta':
      return { type: 'meta', ...(payload as { conversation: ConversationSummary; userMessage: Message }) };
    case 'sources':
      return { type: 'sources', citations: payload as unknown as Citation[] };
    case 'token':
      return { type: 'token', text: String(payload.text ?? '') };
    case 'done':
      return { type: 'done', assistantMessage: payload.assistantMessage as Message };
    case 'error':
      return { type: 'error', message: String(payload.message ?? 'Generation failed'), assistantMessage: payload.assistantMessage as Message | undefined };
    default:
      return null;
  }
}

/**
 * Streams an answer. EventSource cannot POST or send an Authorization header, so the stream is
 * read from fetch(). Aborting the signal closes the connection; the server still finishes and
 * persists the answer, which the caller can reload.
 */
export async function streamChat(body: ChatRequest, onEvent: (e: StreamEvent) => void, signal: AbortSignal): Promise<void> {
  const res = await openStream('/chat/stream', body, signal);
  if (!res.body) throw new Error('Streaming is not supported by this browser');
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      const { events, rest } = parseSseBuffer(buffer + decoder.decode(value, { stream: true }));
      buffer = rest;
      for (const { event, data } of events) {
        const parsed = toEvent(event, data);
        if (parsed) onEvent(parsed);
      }
    }
  } finally {
    reader.releaseLock();
  }
}
