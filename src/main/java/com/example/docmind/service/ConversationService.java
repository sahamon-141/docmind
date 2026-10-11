package com.example.docmind.service;

import com.example.docmind.dto.ConversationDetailDto;
import com.example.docmind.dto.ConversationSummaryDto;
import com.example.docmind.dto.MessageDto;
import com.example.docmind.dto.PageDto;
import com.example.docmind.entity.Conversation;
import com.example.docmind.exception.ResourceNotFoundException;
import com.example.docmind.repository.ConversationRepository;
import com.example.docmind.repository.MessageRepository;
import com.example.docmind.repository.UserRepository;
import com.example.docmind.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

// Owner-scoped conversation CRUD. Another user's conversation id behaves exactly like an unknown one (404).
@Service
@RequiredArgsConstructor
public class ConversationService {
    static final String DEFAULT_TITLE = "New conversation";
    private static final int MAX_PAGE_SIZE = 100;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ConversationSummaryDto create(AuthUser user, String title) {
        Conversation conversation = Conversation.builder()
                .owner(userRepository.getReferenceById(user.id()))
                .title(title == null || title.isBlank() ? DEFAULT_TITLE : title.strip())
                .build();
        return ConversationSummaryDto.from(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public PageDto<ConversationSummaryDto> list(AuthUser user, String query, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clamp(size));
        if (query == null || query.isBlank()) {
            return PageDto.from(conversationRepository.findByOwnerIdOrderByUpdatedAtDesc(user.id(), pageable), ConversationSummaryDto::from);
        }
        String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
        return PageDto.from(conversationRepository.search(user.id(), pattern, pageable), ConversationSummaryDto::from);
    }

    @Transactional(readOnly = true)
    public ConversationDetailDto get(AuthUser user, UUID id) {
        Conversation c = getOwned(user.id(), id);
        var messages = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(c.getId()).stream()
                .map(MessageDto::from)
                .toList();
        return new ConversationDetailDto(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt(), messages);
    }

    @Transactional(readOnly = true)
    public PageDto<MessageDto> messages(AuthUser user, UUID id, int page, int size) {
        Conversation c = getOwned(user.id(), id);
        return PageDto.from(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(c.getId(),
                PageRequest.of(Math.max(page, 0), clamp(size))), MessageDto::from);
    }

    @Transactional
    public ConversationSummaryDto rename(AuthUser user, UUID id, String title) {
        Conversation c = getOwned(user.id(), id);
        c.setTitle(title.strip());
        return ConversationSummaryDto.from(c);
    }

    @Transactional
    public void delete(AuthUser user, UUID id) {
        // messages and citations are removed by ON DELETE CASCADE
        conversationRepository.delete(getOwned(user.id(), id));
    }

    Conversation getOwned(Long ownerId, UUID id) {
        return conversationRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    // Initial title from the first question, avoiding an extra model call.
    static String deriveTitle(String question) {
        String text = question == null ? "" : question.replaceAll("\\s+", " ").strip();
        if (text.isEmpty()) return DEFAULT_TITLE;
        if (text.length() <= 60) return text;
        int cut = text.lastIndexOf(' ', 60);
        return text.substring(0, cut > 30 ? cut : 60) + "…";
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
