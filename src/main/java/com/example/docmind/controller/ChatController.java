package com.example.docmind.controller;

import com.example.docmind.dto.*;
import com.example.docmind.security.AuthUser;
import com.example.docmind.service.ChatService;
import com.example.docmind.service.RetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Ask questions against your own documents. Every exchange is persisted to a conversation.")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final RetrievalService retrievalService;

    @PostMapping("/query")
    @Operation(summary = "Ask a question and wait for the full answer",
            description = "Omit conversationId to start a new conversation. Returns the persisted user and assistant messages with citations. "
                    + "404 for another user's conversation or document, 409 DUPLICATE_MESSAGE for a repeated clientMessageId, "
                    + "502 AI_GENERATION_FAILED (the question is kept; data contains the FAILED assistant message, retry with retryOfMessageId).")
    public ResponseEntity<ApiResponse<ChatResponseDto>> askQuestion(@AuthenticationPrincipal AuthUser user,
                                                                    @Valid @RequestBody ChatRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.ask(user, requestDto), null));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Ask a question and stream the answer via Server-Sent Events",
            description = "Events: meta {conversation, userMessage}, sources [citations], token {text} (repeated), then done {assistantMessage} "
                    + "or error {message, assistantMessage}. Validation/ownership errors are returned as normal JSON errors before the stream starts.")
    public SseEmitter streamQuery(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChatRequestDto requestDto) {
        return chatService.stream(user, requestDto);
    }

    @PostMapping("/search/similarity")
    @Operation(summary = "Semantic search over your own indexed chunks",
            description = "Results are filtered by owner inside the vector query. Optional documentId restricts to one of your documents (404 otherwise).")
    public ResponseEntity<ApiResponse<SearchResultDto>> searchSimilar(@AuthenticationPrincipal AuthUser user,
                                                                      @Valid @RequestBody SearchRequestDto request) {
        SearchResultDto results = retrievalService.search(user, request);
        return ResponseEntity.ok(ApiResponse.ok(results, results.getTotalMatches() == 0 ? "No matching passages found" : null));
    }
}
