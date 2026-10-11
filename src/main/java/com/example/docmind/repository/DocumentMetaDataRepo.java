package com.example.docmind.repository;

import com.example.docmind.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Every finder is scoped by owner. There is intentionally no unscoped lookup used by the services.
@Repository
public interface DocumentMetaDataRepo extends JpaRepository<DocumentMetadata, UUID> {

    List<DocumentMetadata> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<DocumentMetadata> findByIdAndOwnerId(UUID id, Long ownerId);

    boolean existsByIdAndOwnerId(UUID id, Long ownerId);
}
