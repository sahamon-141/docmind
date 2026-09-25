package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.dto.*;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private final VectorStore vectorStore;
    private final AppProperties appProperties;
    private final ChatClient chatClient;

    // to ask anything related to document
    public Flux<String> streamQuestionAndAnswer(ChatRequestDto responseDto){
        log.info("Streaming query: '{}'",responseDto.getQuestion());
        List<Document> relevantDocuments = retrieveRelevantDocuments(
                responseDto.getQuestion(),
                responseDto.getDocumentId(),
                responseDto.getTopK(),
                responseDto.getMinSimilarity()
        );
        String contextText = buildContextString(relevantDocuments);
        String userPrompt = buildPrompt(responseDto.getQuestion(),contextText);
        return chatClient.prompt().user(userPrompt).stream().content();
    }

    //this streams data
    public ChatResponseDto askQuestions(ChatRequestDto request){
        long startTime = System.currentTimeMillis();
        log.info("Processing query: '{}', scoped documentId: {}",request.getQuestion(),request.getDocumentId());
        List<Document> similarDocuments = this.retrieveRelevantDocuments(
                request.getQuestion(),
                request.getDocumentId(),
                request.getTopK(),
                request.getMinSimilarity()
        );

        List<CitationDto> citationDtos = similarDocuments.stream()
                .map(this::mapToCitation)
                .toList();

        String contextText = buildContextString(similarDocuments);
        String prompt = buildPrompt(request.getQuestion(),contextText);
        String answer = this.chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        long responseTime =  startTime-System.currentTimeMillis();
        return ChatResponseDto.builder()
                .answer(answer)
                .conversationId(request.getConversationId()!=null? request.getConversationId() : UUID.randomUUID().toString())
                .citations(citationDtos)
                .responseTimeMs(responseTime)
                .build();
    }

    private String buildPrompt(@NotBlank(message="Question cannot be empty") String question, String contextText) {
        if (contextText != null && !contextText.isBlank()) {
            return String.format("""
                           Document Context:
                           ---------------------
                           %s
                           ---------------------                                        
                           User Message / Question: %s             
                           Instructions:
                           - If the user's question relates to the document context above, prioritize answering using that context and reference key sections.
                           - If the user is asking a general question, greeting, or discussing topics beyond the document context, respond helpfully and conversationally using your general knowledge while weaving in relevant document context if applicable
                    
                    """, contextText, question);
        } else {
            return String.format("""
                       User Message / Question:
                       %s                         
                       Instructions:
                          - Respond helpfully, accurately, and conversationally to the user's message using your broad knowledge base.
                    
                    """, question);
        }
    }

    private String buildContextString(List<Document> similarDocuments) {
        if(similarDocuments==null||similarDocuments.isEmpty()) return "";
        return similarDocuments
                .stream()
                .map(doc->{
                    String filename = (String) doc.getMetadata().getOrDefault("filename","unknown file");
                    Object page = doc.getMetadata().getOrDefault("pageNumber","N/A");
                    return String.format("[Source: %s | Page: %s]\n%s",filename,page,doc.getText());
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    public SearchResultDto searchSimilarChunks(SearchRequestDto request){
        List<Document> matchedDocuments = retrieveRelevantDocuments(request.getQuery(),request.getId(),request.getTopK(),request.getSimilaritySearch());
        List<CitationDto> citations = matchedDocuments.stream().map(this::mapToCitation).toList();
        return SearchResultDto.builder()
                .query(request.getQuery())
                .totalMatches(matchedDocuments.size())
                .matches(citations)
                .build();
    }

    private CitationDto mapToCitation(Document document){
        Map<String, Object> meta = document.getMetadata();
        UUID docId = null;
        if (meta.get("documentId") != null) {
            try {
                docId = UUID.fromString(meta.get("documentId").toString());
            } catch (Exception ignore) {

            }
        }
        Integer chunkIndex = null;
        if (meta.get("chunkIndex") instanceof Number n) {
            chunkIndex = n.intValue();
        }

        Integer pageNumber = null;
        if (meta.get("pageNumber") instanceof Number n) {
            pageNumber = n.intValue();
        } else if (meta.get("page_number") instanceof Number n) {
            pageNumber = n.intValue();
        }

        Double score = null;
        if (meta.get("distance") instanceof Number n) {
            score = 1.0 - n.doubleValue();
        }
        return CitationDto.builder()
                .documentId(docId)
                .fileName((String) meta.getOrDefault("fileName", "Unknown"))
                .chunkIndex(chunkIndex)
                .pageNumber(pageNumber)
                .snippet(document.getText())
                .similarityScore(score)
                .metadata(meta)
                .build();
    }

    private List<Document> retrieveRelevantDocuments(@NotBlank(message="Query cannot be empty") String query, UUID id, Integer topK, Double similaritySearch) {
        int effectiveTopK = (topK!=null&&topK>0) ? topK : appProperties.getRag().getTopK();
        double effectiveSimilarity = similaritySearch!=null ? similaritySearch:appProperties.getRag().getSimilarityThreshold();

        SearchRequest.Builder searchRequestBuilder = SearchRequest
                .builder()
                .query(query)
                .topK(effectiveTopK);
        if(effectiveSimilarity>0.0){
            searchRequestBuilder.similarityThreshold(effectiveSimilarity);
        }
        if(id!=null){
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            searchRequestBuilder.filterExpression(b
                    .eq("documentId",id.toString())
                    .build());
        }
        try{
            List<Document> documents = vectorStore.similaritySearch(searchRequestBuilder.build());
            log.info("Retrieved {} chunks for query: '{}' (scoped docId: {})", documents.size(), query, id);
            return documents;
        }
        catch (Exception e){
            log.error("Similarity search failed for query: '{}'",query,e);
            return Collections.emptyList();
        }

    }
}
