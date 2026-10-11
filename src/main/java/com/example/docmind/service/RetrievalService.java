package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.dto.CitationDto;
import com.example.docmind.dto.SearchRequestDto;
import com.example.docmind.dto.SearchResultDto;
import com.example.docmind.exception.ApiException;
import com.example.docmind.exception.ResourceNotFoundException;
import com.example.docmind.repository.DocumentMetaDataRepo;
import com.example.docmind.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Similarity search that is always restricted to the caller's chunks. The owner filter is part of the
 * SQL query PgVectorStore runs (metadata jsonpath), so other users' vectors are never fetched at all.
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {
    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;
    private final DocumentMetaDataRepo documentRepo;
    private final AppProperties appProperties;

    public List<Document> retrieve(Long ownerId, String query, UUID documentId, Integer topK, Double minSimilarity) {
        AppProperties.RagProperties rag = appProperties.getRag();
        int effectiveTopK = topK != null && topK > 0 ? Math.min(topK, rag.getMaxTopK()) : rag.getTopK();
        double threshold = minSimilarity != null ? minSimilarity : rag.getSimilarityThreshold();

        if (documentId != null && !documentRepo.existsByIdAndOwnerId(documentId, ownerId)) {
            throw new ResourceNotFoundException("Document not found");
        }
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(effectiveTopK)
                .similarityThreshold(threshold)
                .filterExpression(documentId == null
                        ? VectorMetadata.ownedBy(ownerId)
                        : VectorMetadata.ownedDocument(ownerId, documentId))
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        log.debug("Retrieved {} chunks for user {} (document scope: {})", documents.size(), ownerId, documentId);
        return documents;
    }

    public SearchResultDto search(AuthUser user, SearchRequestDto request) {
        List<Document> matches;
        try {
            matches = retrieve(user.id(), request.getQuery(), request.getDocumentId(), request.getTopK(), request.getMinSimilarity());
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Similarity search failed for user {}", user.id(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SEARCH_UNAVAILABLE", "Search is temporarily unavailable", null, e);
        }
        return SearchResultDto.builder()
                .query(request.getQuery())
                .totalMatches(matches.size())
                .matches(toCitations(matches))
                .build();
    }

    public List<CitationDto> toCitations(List<Document> documents) {
        return IntStream.range(0, documents.size())
                .mapToObj(i -> toCitation(documents.get(i), i + 1))
                .toList();
    }

    private CitationDto toCitation(Document document, int position) {
        Map<String, Object> meta = document.getMetadata();
        UUID docId = null;
        try {
            Object raw = meta.get(VectorMetadata.DOCUMENT_ID);
            if (raw != null) docId = UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ignored) {
            // malformed legacy metadata: leave the reference empty
        }
        String text = document.getText() == null ? "" : document.getText().strip();
        int limit = appProperties.getRag().getSnippetLength();
        return CitationDto.builder()
                .position(position)
                .documentId(docId)
                .fileName(String.valueOf(meta.getOrDefault(VectorMetadata.FILE_NAME, "Unknown document")))
                .chunkIndex(asInteger(meta.get(VectorMetadata.CHUNK_INDEX)))
                .pageNumber(asInteger(meta.get(VectorMetadata.PAGE_NUMBER)))
                .snippet(text.length() > limit ? text.substring(0, limit) + "…" : text)
                .similarityScore(document.getScore())
                .build();
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }
}
