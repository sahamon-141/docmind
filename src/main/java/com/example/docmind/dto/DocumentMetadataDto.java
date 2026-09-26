package com.example.docmind.dto;

import com.example.docmind.entity.DocumentStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

// this dto is created for sole purpose for getalldocuments api
// This dto is same as the DocumentMetadata but only  message attribute has been removed
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentMetadataDto {


    private UUID id;
    private  String filename;
    private  String contentType;
    private  Long fileSize;
    private Integer totalPages;
    private  Integer totalChunks;
    private DocumentStatus status;
    private  String errorMessage;
    private LocalDateTime createdAt;
    private  LocalDateTime updatedAt;
}
