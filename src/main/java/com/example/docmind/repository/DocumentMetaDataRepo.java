package com.example.docmind.repository;

import com.example.docmind.entity.DocumentMetadata;
import com.example.docmind.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface DocumentMetaDataRepo extends JpaRepository<DocumentMetadata, UUID>{

    List<DocumentMetadata> findAllByOrderByCreatedAtDesc();
    List<DocumentMetadata> findByStatus(DocumentStatus status);
}
