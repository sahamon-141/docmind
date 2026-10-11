package com.example.docmind.dto;

import com.example.docmind.entity.DocumentStatus;
import lombok.*;

import java.util.UUID;

// Result of one upload. For multi-upload, failed files are reported per item with status FAILED.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentResponseDto {
    private UUID id;
    private String filename;
    private long fileSize;
    private DocumentStatus status;
    private Integer chunksCreated;
    private String message;
}
