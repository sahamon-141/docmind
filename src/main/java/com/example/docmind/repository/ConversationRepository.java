package com.example.docmind.repository;

import com.example.docmind.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndOwnerId(UUID id, Long ownerId);

    Page<Conversation> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId, Pageable pageable);

    // matches the title or any message content; pattern must already be lower-cased and wrapped in %
    @Query(value = """
            select c from Conversation c
            where c.owner.id = :ownerId
              and (lower(c.title) like :pattern escape '\\'
                   or exists (select 1 from Message m where m.conversation = c and lower(m.content) like :pattern escape '\\'))
            order by c.updatedAt desc""",
            countQuery = """
            select count(c) from Conversation c
            where c.owner.id = :ownerId
              and (lower(c.title) like :pattern escape '\\'
                   or exists (select 1 from Message m where m.conversation = c and lower(m.content) like :pattern escape '\\'))""")
    Page<Conversation> search(@Param("ownerId") Long ownerId, @Param("pattern") String pattern, Pageable pageable);
}
