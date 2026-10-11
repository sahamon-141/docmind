package com.example.docmind.repository;

import com.example.docmind.entity.Message;
import com.example.docmind.entity.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Messages are always looked up through their conversation id, which the services verify against the owner first.
public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);

    Page<Message> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId, Pageable pageable);

    Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

    Optional<Message> findByConversationIdAndClientMessageId(UUID conversationId, UUID clientMessageId);

    List<Message> findByReplyToId(UUID replyToId);

    boolean existsByReplyToIdAndStatus(UUID replyToId, MessageStatus status);

    @Query("select m from Message m where m.conversation.id = :conversationId and m.status = com.example.docmind.entity.MessageStatus.COMPLETE order by m.createdAt desc, m.id desc")
    List<Message> findRecentComplete(@Param("conversationId") UUID conversationId, Pageable pageable);

    long countByConversationId(UUID conversationId);
}
