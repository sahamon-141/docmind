package com.example.docmind.dto;

import com.example.docmind.entity.DocumentMetadata;
import com.example.docmind.entity.DocumentStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// Library view of a document. Ownership is implicit (only the owner can see it), so no owner field is exposed.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentMetadataDto {
    private UUID id;
    private String filename;
    private String contentType;
    private Long fileSize;
    private Integer totalPages;
    private Integer totalChunks;
    private DocumentStatus status;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public static DocumentMetadataDto from(DocumentMetadata d) {
        return DocumentMetadataDto.builder()
                .id(d.getId())
                .filename(d.getFilename())
                .contentType(d.getContentType())
                .fileSize(d.getFileSize())
                .totalPages(d.getTotalPages())
                .totalChunks(d.getTotalChunks())
                .status(d.getStatus())
                .errorMessage(d.getErrorMessage())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
