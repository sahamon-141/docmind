import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { API_BASE_URL } from '../api/config';
import { conversationsApi, documentsApi } from '../api/endpoints';
import type { ConversationDetail, DocumentMetadata } from '../api/types';

export const queryKeys = {
  documents: ['documents'] as const,
  document: (id: string) => ['documents', id] as const,
  conversations: (q: string) => ['conversations', q] as const,
  conversationsRoot: ['conversations'] as const,
  conversation: (id: string) => ['conversation', id] as const,
};

const isProcessing = (docs: DocumentMetadata[] | undefined) =>
  docs?.some((d) => d.status === 'PROCESSING' || d.status === 'UPLOADING') ?? false;

export function useDocuments() {
  return useQuery({
    queryKey: queryKeys.documents,
    queryFn: ({ signal }) => documentsApi.list(signal),
    // keep the library live while something is still being indexed
    refetchInterval: (query) => (isProcessing(query.state.data) ? 4000 : false),
  });
}

export function useDocument(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.document(id ?? ''),
    queryFn: ({ signal }) => documentsApi.get(id ?? '', signal),
    enabled: Boolean(id),
    retry: (count, error) => (error as { status?: number }).status !== 404 && count < 2,
  });
}

export function useDeleteDocument() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => documentsApi.remove(id),
    onSettled: () => client.invalidateQueries({ queryKey: queryKeys.documents }),
  });
}

export function useConversations(search: string) {
  return useQuery({
    queryKey: queryKeys.conversations(search),
    queryFn: ({ signal }) => conversationsApi.list(search, signal),
    placeholderData: (previous) => previous,
  });
}

// An answer is still being produced server-side (e.g. after the client stopped a stream).
function awaitingAnswer(data: ConversationDetail | undefined): boolean {
  const last = data?.messages.at(-1);
  return !!last && last.role === 'USER' && Date.now() - new Date(last.createdAt).getTime() < 120_000;
}

export function useConversation(id: string | undefined, polling: boolean) {
  return useQuery({
    queryKey: queryKeys.conversation(id ?? ''),
    queryFn: ({ signal }) => conversationsApi.get(id ?? '', signal),
    enabled: Boolean(id),
    retry: (count, error) => (error as { status?: number }).status !== 404 && count < 2,
    refetchInterval: (query) => (polling && awaitingAnswer(query.state.data) ? 2500 : false),
  });
}

export function useRenameConversation() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => conversationsApi.rename(id, title),
    onSuccess: (summary) => {
      client.setQueryData<ConversationDetail>(queryKeys.conversation(summary.id), (old) =>
        old ? { ...old, title: summary.title } : old,
      );
    },
    onSettled: () => client.invalidateQueries({ queryKey: queryKeys.conversationsRoot }),
  });
}

export function useDeleteConversation() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => conversationsApi.remove(id),
    onSuccess: (_, id) => client.removeQueries({ queryKey: queryKeys.conversation(id) }),
    onSettled: () => client.invalidateQueries({ queryKey: queryKeys.conversationsRoot }),
  });
}

/** Backend liveness for the sidebar status indicator (Spring Boot actuator health). */
export function useApiHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: async ({ signal }) => {
      const res = await fetch(`${API_BASE_URL}/actuator/health`, { signal });
      if (!res.ok) throw new Error(`health ${res.status}`);
      return ((await res.json()) as { status: string }).status;
    },
    refetchInterval: 60_000,
    retry: false,
  });
}
