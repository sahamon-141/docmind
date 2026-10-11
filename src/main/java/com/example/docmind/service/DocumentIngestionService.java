package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.entity.DocumentMetadata;
import com.example.docmind.exception.DocumentProcessingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Splits parsed text into chunks, tags each chunk with owner/document metadata and writes the embeddings to pgvector.
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final AppProperties appProperties;

    public List<Document> split(DocumentMetadata metadata, Long ownerId, List<Document> parsedDocs) {
        AppProperties.RagProperties rag = appProperties.getRag();
        // Text Chunking , using tokenTextSplitter
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(rag.getChunkSize()) // target tokens per chunk
                .withMinChunkSizeChars(rag.getMinChunkSizeChars()) // don't cut at punctuation before this many characters
                .withMinChunkLengthToEmbed(rag.getMinChunkLengthToEmbed()) // drop chunks shorter than this
                .withMaxNumChunks(rag.getMaxNumChunks()) // safety limit per document
                .withKeepSeparator(true)
                .build();
        List<Document> chunks = splitter.apply(parsedDocs);

        // Metadata is rebuilt from scratch so only known, safe keys end up in the shared vector table.
        List<Document> enriched = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> meta = new HashMap<>();
            meta.put(VectorMetadata.OWNER_ID, String.valueOf(ownerId));
            meta.put(VectorMetadata.DOCUMENT_ID, metadata.getId().toString());
            meta.put(VectorMetadata.FILE_NAME, metadata.getFilename());
            meta.put(VectorMetadata.CONTENT_TYPE, metadata.getContentType());
            meta.put(VectorMetadata.CHUNK_INDEX, i);
            Object page = chunk.getMetadata().getOrDefault("page_number", chunk.getMetadata().get(VectorMetadata.PAGE_NUMBER));
            if (page instanceof Number n) {
                meta.put(VectorMetadata.PAGE_NUMBER, n.intValue());
            }
            enriched.add(new Document(chunk.getText(), meta));
        }
        return enriched;
    }

    // Embeds and stores the chunks. On failure any partially written chunks are removed again.
    public void store(DocumentMetadata metadata, Long ownerId, List<Document> chunks) {
        log.info("Writing {} vector chunks for document {}", chunks.size(), metadata.getId());
        try {
            vectorStore.add(chunks);
        } catch (RuntimeException ex) {
            log.error("Embedding/indexing failed for document {}: {}", metadata.getId(), ex.getMessage());
            try {
                vectorStore.delete(VectorMetadata.ownedDocument(ownerId, metadata.getId()));
            } catch (RuntimeException cleanup) {
                log.warn("Could not clean up partial vectors for document {}", metadata.getId());
            }
            throw new DocumentProcessingException("Indexing failed: the embedding service is unavailable or rejected the request.");
        }
    }
}
