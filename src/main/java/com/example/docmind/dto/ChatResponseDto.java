package com.example.docmind.dto;

import lombok.*;

// One question/answer exchange as persisted in the conversation.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatResponseDto {
    private ConversationSummaryDto conversation;
    private MessageDto userMessage;
    private MessageDto assistantMessage;
}
