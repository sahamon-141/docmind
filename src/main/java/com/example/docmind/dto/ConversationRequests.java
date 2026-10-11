package com.example.docmind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ConversationRequests {
    private ConversationRequests() {
    }

    public record Create(@Size(max = 200, message = "Title must be at most 200 characters") String title) {
    }

    public record Rename(@NotBlank(message = "Title is required")
                         @Size(max = 200, message = "Title must be at most 200 characters") String title) {
    }
}
