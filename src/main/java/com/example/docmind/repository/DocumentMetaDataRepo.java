package com.example.docmind.repository;

import com.example.docmind.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface DocumentMetaDataRepo extends JpaRepository<DocumentMetadata, UUID>{

}
