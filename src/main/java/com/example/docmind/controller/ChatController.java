package com.example.docmind.controller;

import com.example.docmind.dto.*;
import com.example.docmind.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(
        name = "Chat Management",
        description = "All chat related apis goes here."
)
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;

    @PostMapping("/query")
    @Operation(summary = "Ask a question against all the documents or a specific document with citations")
    public ResponseEntity<ApiResponse<ChatResponseDto>> askQuestion(@Valid @RequestBody ChatRequestDto requestDto){
            ChatResponseDto responseDto = ragService.askQuestions(requestDto);
            return ResponseEntity.status(HttpStatus.OK).body(
                    ApiResponse
                            .<ChatResponseDto>builder()
                            .success(true)
                            .message(null)
                            .data(responseDto)
                            .timestamps(LocalDateTime.now())
                            .build()
            );
    }

    @PostMapping("/stream")
    @Operation(summary="Stream real time answer tokens via Server-Sent Events (SSE)")
    public Flux<String> streamQuery(@Valid @RequestBody ChatRequestDto requestDto){
        return ragService.streamQuestionAndAnswer(requestDto);
    }

    @PostMapping("/search/similarity")
    @Operation(summary = "Perform semantic search on stored document vectors")
    public ResponseEntity<ApiResponse<SearchResultDto>> searchSimilar(@Valid @RequestBody SearchRequestDto requests){
        SearchResultDto results = ragService.searchSimilarChunks(requests);
        return ResponseEntity.status(HttpStatus.OK).body(
                ApiResponse
                        .<SearchResultDto>builder()
                        .success(true)
                        .message("performed similarity search")
                        .data(results)
                        .timestamps(LocalDateTime.now())
                        .build()
        );
    }

}
