package com.example.docmind.controller;

import com.example.docmind.dto.*;
import com.example.docmind.security.AuthUser;
import com.example.docmind.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Persistent chat history of the authenticated user. Send messages through /api/v1/chat/query or /stream.")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "List conversations, most recently active first",
            description = "Optional q searches titles and message content (case-insensitive).")
    public ResponseEntity<ApiResponse<PageDto<ConversationSummaryDto>>> list(@AuthenticationPrincipal AuthUser user,
                                                                             @RequestParam(required = false) String q,
                                                                             @RequestParam(defaultValue = "0") int page,
                                                                             @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.list(user, q, page, size), null));
    }

    @PostMapping
    @Operation(summary = "Create an empty conversation")
    public ResponseEntity<ApiResponse<ConversationSummaryDto>> create(@AuthenticationPrincipal AuthUser user,
                                                                      @Valid @RequestBody(required = false) ConversationRequests.Create request) {
        String title = request == null ? null : request.title();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(conversationService.create(user, title), "Conversation created"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a conversation with all of its messages and citations (chronological)")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.get(user, id), null));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Page through a conversation's messages (chronological)")
    public ResponseEntity<ApiResponse<PageDto<MessageDto>>> messages(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.messages(user, id, page, size), null));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a conversation")
    public ResponseEntity<ApiResponse<ConversationSummaryDto>> rename(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                                                      @Valid @RequestBody ConversationRequests.Rename request) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.rename(user, id, request.title()), "Conversation renamed"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a conversation and all of its messages")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        conversationService.delete(user, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Conversation deleted"));
    }
}
