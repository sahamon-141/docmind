import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { documentsApi } from '../../api/endpoints';
import { errorMessage, isApiError } from '../../api/errors';
import type { DocumentMetadata, DocumentUploadResult } from '../../api/types';
import { queryKeys } from '../../hooks/queries';
import { fileExtension, formatBytes, MAX_UPLOAD_BYTES, newId, SUPPORTED_EXTENSIONS } from '../../lib/format';

export type UploadPhase = 'queued' | 'uploading' | 'processing' | 'done' | 'error';

export interface UploadItem {
  id: string;
  name: string;
  size: number;
  progress: number;
  phase: UploadPhase;
  error?: string;
  documentId?: string;
  chunks?: number | null;
}

/** Returns an error message if the file is rejected before upload (the server validates again). */
export function precheckFile(file: File): string | null {
  if (!SUPPORTED_EXTENSIONS.includes(fileExtension(file.name))) {
    return `Unsupported type. Use ${SUPPORTED_EXTENSIONS.filter((e) => e !== 'markdown' && e !== 'htm').join(', ')}.`;
  }
  if (file.size === 0) return 'The file is empty.';
  if (file.size > MAX_UPLOAD_BYTES) return `Larger than the ${formatBytes(MAX_UPLOAD_BYTES)} limit.`;
  return null;
}

/**
 * Uploads files one at a time with real progress (XHR). After the bytes are sent the server parses,
 * embeds and indexes synchronously, which is shown as the "processing" phase.
 */
export function useUploader(onUploaded?: (result: DocumentUploadResult) => void) {
  const queryClient = useQueryClient();
  const [items, setItems] = useState<UploadItem[]>([]);
  const controllers = useRef(new Map<string, AbortController>());
  const callbackRef = useRef(onUploaded);

  useEffect(() => {
    callbackRef.current = onUploaded;
  }, [onUploaded]);

  useEffect(() => {
    const active = controllers.current;
    return () => active.forEach((c) => c.abort());
  }, []);

  const patch = useCallback((id: string, update: Partial<UploadItem>) => {
    setItems((all) => all.map((i) => (i.id === id ? { ...i, ...update } : i)));
  }, []);

  const uploadOne = useCallback(
    async (file: File, id: string) => {
      const controller = new AbortController();
      controllers.current.set(id, controller);
      patch(id, { phase: 'uploading' });
      try {
        const result = await documentsApi.upload(file, {
          signal: controller.signal,
          onProgress: (fraction) => patch(id, { progress: fraction, phase: fraction >= 1 ? 'processing' : 'uploading' }),
        });
        patch(id, { phase: 'done', progress: 1, documentId: result.id ?? undefined, chunks: result.chunksCreated });
        callbackRef.current?.(result);
      } catch (e) {
        if (e instanceof DOMException && e.name === 'AbortError') {
          patch(id, { phase: 'error', error: 'Cancelled' });
        } else {
          const failedDoc = isApiError(e) ? (e.data as DocumentMetadata | undefined) : undefined;
          patch(id, { phase: 'error', error: errorMessage(e, 'Upload failed.'), documentId: failedDoc?.id });
        }
      } finally {
        controllers.current.delete(id);
        void queryClient.invalidateQueries({ queryKey: queryKeys.documents });
      }
    },
    [patch, queryClient],
  );

  const addFiles = useCallback(
    async (files: File[]) => {
      const accepted: { file: File; id: string }[] = [];
      const next: UploadItem[] = files.map((file) => {
        const id = newId();
        const error = precheckFile(file);
        if (!error) accepted.push({ file, id });
        return { id, name: file.name, size: file.size, progress: 0, phase: error ? 'error' : 'queued', error: error ?? undefined };
      });
      setItems((all) => [...next, ...all]);
      for (const { file, id } of accepted) {
        await uploadOne(file, id);
      }
    },
    [uploadOne],
  );

  const cancel = useCallback((id: string) => controllers.current.get(id)?.abort(), []);
  const clearFinished = useCallback(() => setItems((all) => all.filter((i) => i.phase === 'uploading' || i.phase === 'processing' || i.phase === 'queued')), []);

  return { items, addFiles, cancel, clearFinished, busy: items.some((i) => i.phase === 'uploading' || i.phase === 'processing' || i.phase === 'queued') };
}
