package com.example.docmind.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequestDto {

    // ignored when retryOfMessageId is set (the stored question is reused)
    @Size(max = 4000, message = "Question must be at most 4000 characters")
    private String question;

    // omit to start a new conversation; must belong to the caller otherwise (404)
    private UUID conversationId;

    // optional: restrict retrieval to one of the caller's documents
    private UUID documentId;

    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 20, message = "topK must be at most 20")
    private Integer topK;

    @DecimalMin(value = "0.0", message = "minSimilarity must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "minSimilarity must be between 0 and 1")
    private Double minSimilarity;

    // client-generated idempotency key; resubmitting the same key returns 409 DUPLICATE_MESSAGE
    private UUID clientMessageId;

    // regenerate the answer for this previously failed user message instead of posting a new one
    private UUID retryOfMessageId;

    @AssertTrue(message = "Question cannot be empty")
    private boolean isQuestionPresent() {
        return retryOfMessageId != null || (question != null && !question.isBlank());
    }
}
