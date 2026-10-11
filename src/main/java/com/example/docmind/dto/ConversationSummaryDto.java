package com.example.docmind.dto;

import com.example.docmind.entity.Conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryDto(UUID id, String title, Instant createdAt, Instant updatedAt) {
    public static ConversationSummaryDto from(Conversation c) {
        return new ConversationSummaryDto(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
