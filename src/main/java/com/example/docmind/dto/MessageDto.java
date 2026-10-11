package com.example.docmind.dto;

import com.example.docmind.entity.Message;
import com.example.docmind.entity.MessageRole;
import com.example.docmind.entity.MessageStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public record MessageDto(
        UUID id,
        UUID conversationId,
        MessageRole role,
        MessageStatus status,
        String content,
        UUID documentId,
        UUID replyToId,
        Long responseTimeMs,
        Instant createdAt,
        List<CitationDto> citations
) {
    public static MessageDto from(Message m) {
        List<CitationDto> citations = IntStream.range(0, m.getCitations().size())
                .mapToObj(i -> CitationDto.from(m.getCitations().get(i), i + 1))
                .toList();
        return new MessageDto(m.getId(), m.getConversation().getId(), m.getRole(), m.getStatus(), m.getContent(),
                m.getDocumentId(), m.getReplyToId(), m.getResponseTimeMs(), m.getCreatedAt(), citations);
    }
}
