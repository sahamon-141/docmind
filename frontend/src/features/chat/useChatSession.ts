import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { streamChat } from '../../api/stream';
import { errorMessage, isApiError } from '../../api/errors';
import type { ChatExchange, Citation, ConversationDetail, ConversationSummary, Message } from '../../api/types';
import { queryKeys } from '../../hooks/queries';
import { newId } from '../../lib/format';
import { useToast } from '../../providers/toast-context';

export interface PendingExchange {
  /** null until the server has created/confirmed the conversation */
  conversationId: string | null;
  /** conversation that was open when the question was sent (null for a new chat) */
  origin: string | null;
  /** user message id being regenerated, if this is a retry */
  retryOf: string | null;
  question: string;
  userMessage: Message | null;
  sources: Citation[];
  text: string;
  startedAt: string;
}

export interface ChatFailure {
  message: string;
  conversationId: string | null;
}

interface Options {
  conversationId: string | undefined;
  onConversationCreated: (id: string) => void;
}

/**
 * Drives one streamed question/answer exchange. Server data stays in the React Query cache;
 * this hook only holds the in-flight exchange (optimistic question + streamed tokens).
 */
export function useChatSession({ conversationId, onConversationCreated }: Options) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [pending, setPending] = useState<PendingExchange | null>(null);
  const [failure, setFailure] = useState<ChatFailure | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const createdRef = useRef(onConversationCreated);

  useEffect(() => {
    createdRef.current = onConversationCreated;
  }, [onConversationCreated]);

  // close any open stream when the chat view unmounts
  useEffect(() => () => abortRef.current?.abort(), []);

  const commitExchange = useCallback(
    (conversation: ConversationSummary, userMessage: Message, assistantMessage: Message) => {
      queryClient.setQueryData<ConversationDetail>(queryKeys.conversation(conversation.id), (old) => {
        const base = old ?? { ...conversation, messages: [] };
        const kept = base.messages.filter((m) => m.id !== userMessage.id && m.replyToId !== userMessage.id);
        return { ...base, updatedAt: assistantMessage.createdAt, messages: [...kept, userMessage, assistantMessage] };
      });
      void queryClient.invalidateQueries({ queryKey: queryKeys.conversationsRoot });
    },
    [queryClient],
  );

  const send = useCallback(
    async (question: string, opts: { documentId?: string | null; retryOf?: string | null } = {}) => {
      abortRef.current?.abort(); // only one exchange streams at a time
      const controller = new AbortController();
      abortRef.current = controller;
      const retryOf = opts.retryOf ?? null;
      let conversation: ConversationSummary | null = null;
      let userMessage: Message | null = null;

      setFailure(null);
      setPending({
        conversationId: conversationId ?? null,
        origin: conversationId ?? null,
        retryOf,
        question,
        userMessage: null,
        sources: [],
        text: '',
        startedAt: new Date().toISOString(),
      });

      try {
        await streamChat(
          {
            question: retryOf ? undefined : question,
            conversationId,
            documentId: retryOf ? undefined : (opts.documentId ?? undefined),
            clientMessageId: retryOf ? undefined : newId(),
            retryOfMessageId: retryOf ?? undefined,
          },
          (event) => {
            switch (event.type) {
              case 'meta':
                conversation = event.conversation;
                userMessage = event.userMessage;
                setPending((p) => p && { ...p, conversationId: event.conversation.id, userMessage: event.userMessage });
                if (!conversationId) createdRef.current(event.conversation.id);
                void queryClient.invalidateQueries({ queryKey: queryKeys.conversationsRoot });
                break;
              case 'sources':
                setPending((p) => p && { ...p, sources: event.citations });
                break;
              case 'token':
                setPending((p) => p && { ...p, text: p.text + event.text });
                break;
              case 'done':
                if (conversation && userMessage) commitExchange(conversation, userMessage, event.assistantMessage);
                setPending(null);
                break;
              case 'error':
                // a persisted FAILED reply renders inline with its own Retry; only otherwise show a banner
                if (conversation && userMessage && event.assistantMessage) {
                  commitExchange(conversation, userMessage, event.assistantMessage);
                } else {
                  setFailure({ message: event.message, conversationId: conversation?.id ?? null });
                }
                setPending(null);
                break;
            }
          },
          controller.signal,
        );
      } catch (e) {
        if (controller.signal.aborted) return; // stopped by the user, a newer send, or unmount
        // 502 from the server still carries the persisted exchange (question kept, FAILED reply)
        const exchange = isApiError(e) ? (e.data as Partial<ChatExchange> | undefined) : undefined;
        if (exchange?.conversation && exchange.userMessage && exchange.assistantMessage) {
          commitExchange(exchange.conversation, exchange.userMessage, exchange.assistantMessage);
          if (!conversationId) createdRef.current(exchange.conversation.id);
        } else {
          setFailure({ message: errorMessage(e), conversationId: conversationId ?? null });
        }
        setPending(null);
      } finally {
        if (abortRef.current === controller) abortRef.current = null;
      }
    },
    [conversationId, commitExchange, queryClient],
  );

  /** Stops reading the stream. The server keeps generating and saves the answer; the view polls for it. */
  const stop = useCallback(() => {
    const id = pending?.conversationId;
    abortRef.current?.abort();
    abortRef.current = null;
    setPending(null);
    if (id) void queryClient.invalidateQueries({ queryKey: queryKeys.conversation(id) });
    toast.info('Stopped streaming. The full answer will appear here once it is saved.');
  }, [pending?.conversationId, queryClient, toast]);

  const visiblePending =
    pending && (pending.conversationId === (conversationId ?? null) || (pending.origin === null && !conversationId)) ? pending : null;
  const visibleFailure = failure && failure.conversationId === (conversationId ?? null) ? failure : null;

  return {
    pending: visiblePending,
    failure: visibleFailure,
    busy: pending !== null,
    send,
    stop,
    dismissFailure: () => setFailure(null),
  };
}
