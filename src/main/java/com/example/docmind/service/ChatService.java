package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.dto.ChatRequestDto;
import com.example.docmind.dto.ChatResponseDto;
import com.example.docmind.dto.CitationDto;
import com.example.docmind.dto.ConversationSummaryDto;
import com.example.docmind.dto.MessageDto;
import com.example.docmind.entity.Conversation;
import com.example.docmind.entity.Message;
import com.example.docmind.entity.MessageRole;
import com.example.docmind.entity.MessageStatus;
import com.example.docmind.exception.AiGenerationException;
import com.example.docmind.exception.ApiException;
import com.example.docmind.exception.ConflictException;
import com.example.docmind.exception.ResourceNotFoundException;
import com.example.docmind.repository.ConversationRepository;
import com.example.docmind.repository.DocumentMetaDataRepo;
import com.example.docmind.repository.MessageRepository;
import com.example.docmind.repository.UserRepository;
import com.example.docmind.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat request lifecycle:
 * <ol>
 *   <li>transaction 1: verify ownership (conversation, optional document), create the conversation if needed,
 *       persist the user message. It is committed before any AI call, so it is never lost;</li>
 *   <li>owner-filtered retrieval and generation, outside any transaction;</li>
 *   <li>transaction 2: persist the assistant message with its citation snapshot, or a FAILED assistant
 *       message that the client can retry via retryOfMessageId.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String FAILURE_TEXT = "The answer could not be generated because the AI service is unavailable. Please retry.";
    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentMetaDataRepo documentRepo;
    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final RetrievalService retrievalService;
    private final RagService ragService;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties appProperties;

    private record Prepared(Conversation conversation, MessageDto userMessage, String question, UUID documentId,
                            List<RagService.HistoryTurn> history) {
    }

    public ChatResponseDto ask(AuthUser user, ChatRequestDto request) {
        Prepared prepared = prepare(user, request);
        long start = System.nanoTime();
        try {
            List<Document> sources = retrieve(user, prepared, request);
            String answer = ragService.generate(prepared.question(), sources, prepared.history());
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("Model returned an empty answer");
            }
            MessageDto assistant = saveAssistant(prepared, MessageStatus.COMPLETE, answer,
                    retrievalService.toCitations(sources), elapsedMs(start));
            return response(prepared, assistant);
        } catch (RuntimeException e) {
            MessageDto failed = saveAssistant(prepared, MessageStatus.FAILED, FAILURE_TEXT, List.of(), elapsedMs(start));
            throw new AiGenerationException("The AI service failed to answer. Your question was saved and can be retried.",
                    response(prepared, failed), e);
        }
    }

    /**
     * Server-Sent Events: "meta" (conversation + saved user message), "sources" (citations), many "token"
     * events, then exactly one of "done" (persisted assistant message) or "error". If the client disconnects,
     * generation still runs to completion and is persisted, so reloading the conversation shows the answer.
     */
    public SseEmitter stream(AuthUser user, ChatRequestDto request) {
        Prepared prepared = prepare(user, request);
        long start = System.nanoTime();
        List<Document> sources;
        try {
            sources = retrieve(user, prepared, request);
        } catch (RuntimeException e) {
            MessageDto failed = saveAssistant(prepared, MessageStatus.FAILED, FAILURE_TEXT, List.of(), elapsedMs(start));
            throw new AiGenerationException("The AI service failed to answer. Your question was saved and can be retried.",
                    response(prepared, failed), e);
        }
        List<CitationDto> citations = retrievalService.toCitations(sources);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean clientGone = new AtomicBoolean(false);
        emitter.onTimeout(() -> clientGone.set(true));
        emitter.onError(e -> clientGone.set(true));

        send(emitter, clientGone, "meta", Map.of(
                "conversation", ConversationSummaryDto.from(prepared.conversation()),
                "userMessage", prepared.userMessage()));
        send(emitter, clientGone, "sources", citations);

        StringBuilder answer = new StringBuilder();
        ragService.stream(prepared.question(), sources, prepared.history()).subscribe(
                token -> {
                    answer.append(token);
                    send(emitter, clientGone, "token", Map.of("text", token));
                },
                error -> {
                    log.error("Streaming generation failed for conversation {}", prepared.conversation().getId(), error);
                    String partial = answer.isEmpty() ? FAILURE_TEXT : answer.toString();
                    MessageDto failed = saveAssistant(prepared, MessageStatus.FAILED, partial, citations, elapsedMs(start));
                    send(emitter, clientGone, "error", Map.of(
                            "message", "The AI service failed while answering. You can retry.",
                            "assistantMessage", failed));
                    emitter.complete();
                },
                () -> {
                    if (answer.toString().isBlank()) {
                        MessageDto failed = saveAssistant(prepared, MessageStatus.FAILED, FAILURE_TEXT, List.of(), elapsedMs(start));
                        send(emitter, clientGone, "error", Map.of("message", "The model returned an empty answer.", "assistantMessage", failed));
                    } else {
                        MessageDto done = saveAssistant(prepared, MessageStatus.COMPLETE, answer.toString(), citations, elapsedMs(start));
                        send(emitter, clientGone, "done", Map.of("assistantMessage", done));
                    }
                    emitter.complete();
                });
        return emitter;
    }

    private Prepared prepare(AuthUser user, ChatRequestDto request) {
        return transactionTemplate.execute(status -> {
            Conversation conversation;
            if (request.getConversationId() != null) {
                conversation = conversationService.getOwned(user.id(), request.getConversationId());
            } else {
                if (request.getRetryOfMessageId() != null) {
                    throw new ResourceNotFoundException("Message not found");
                }
                conversation = conversationRepository.save(Conversation.builder()
                        .owner(userRepository.getReferenceById(user.id()))
                        .title(ConversationService.deriveTitle(request.getQuestion()))
                        .build());
            }

            Message userMessage;
            if (request.getRetryOfMessageId() != null) {
                userMessage = messageRepository.findByIdAndConversationId(request.getRetryOfMessageId(), conversation.getId())
                        .filter(m -> m.getRole() == MessageRole.USER)
                        .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
                if (messageRepository.existsByReplyToIdAndStatus(userMessage.getId(), MessageStatus.COMPLETE)) {
                    throw new ConflictException("ALREADY_ANSWERED", "This message already has an answer");
                }
                messageRepository.deleteAll(messageRepository.findByReplyToId(userMessage.getId()));
            } else {
                if (request.getDocumentId() != null && !documentRepo.existsByIdAndOwnerId(request.getDocumentId(), user.id())) {
                    throw new ResourceNotFoundException("Document not found");
                }
                if (request.getClientMessageId() != null && messageRepository
                        .findByConversationIdAndClientMessageId(conversation.getId(), request.getClientMessageId()).isPresent()) {
                    throw new ConflictException("DUPLICATE_MESSAGE", "This message was already submitted");
                }
                userMessage = messageRepository.save(Message.builder()
                        .conversation(conversation)
                        .role(MessageRole.USER)
                        .status(MessageStatus.COMPLETE)
                        .content(request.getQuestion().strip())
                        .documentId(request.getDocumentId())
                        .clientMessageId(request.getClientMessageId())
                        .build());
            }
            conversation.touch();
            return new Prepared(conversation, MessageDto.from(userMessage), userMessage.getContent(),
                    userMessage.getDocumentId(), history(conversation.getId(), userMessage.getId()));
        });
    }

    private List<Document> retrieve(AuthUser user, Prepared prepared, ChatRequestDto request) {
        return retrievalService.retrieve(user.id(), prepared.question(), prepared.documentId(),
                request.getTopK(), request.getMinSimilarity());
    }

    // Most recent completed turns before the current question, oldest first.
    private List<RagService.HistoryTurn> history(UUID conversationId, UUID currentMessageId) {
        int limit = appProperties.getRag().getHistoryMessages();
        if (limit <= 0) return List.of();
        List<RagService.HistoryTurn> turns = new ArrayList<>();
        for (Message m : messageRepository.findRecentComplete(conversationId, PageRequest.of(0, limit + 1))) {
            if (!m.getId().equals(currentMessageId) && turns.size() < limit) {
                turns.add(new RagService.HistoryTurn(m.getRole(), m.getContent()));
            }
        }
        Collections.reverse(turns);
        return turns;
    }

    private MessageDto saveAssistant(Prepared prepared, MessageStatus status, String content, List<CitationDto> citations, long elapsedMs) {
        return transactionTemplate.execute(tx -> {
            Conversation conversation = conversationRepository.findById(prepared.conversation().getId())
                    .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.GONE, "CONVERSATION_DELETED",
                            "The conversation was deleted while the answer was generated"));
            Message message = Message.builder()
                    .conversation(conversation)
                    .role(MessageRole.ASSISTANT)
                    .status(status)
                    .content(content)
                    .documentId(prepared.documentId())
                    .replyToId(prepared.userMessage().id())
                    .responseTimeMs(elapsedMs)
                    .citations(new ArrayList<>(citations.stream().map(CitationDto::toEntity).toList()))
                    .build();
            conversation.touch();
            return MessageDto.from(messageRepository.save(message));
        });
    }

    private ChatResponseDto response(Prepared prepared, MessageDto assistant) {
        return ChatResponseDto.builder()
                .conversation(ConversationSummaryDto.from(prepared.conversation()))
                .userMessage(prepared.userMessage())
                .assistantMessage(assistant)
                .build();
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static void send(SseEmitter emitter, AtomicBoolean clientGone, String event, Object data) {
        if (clientGone.get()) return;
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // client disconnected: keep generating so the final answer is still persisted
            clientGone.set(true);
        }
    }
}
