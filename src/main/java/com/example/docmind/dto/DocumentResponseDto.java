package com.example.docmind.dto;

import com.example.docmind.entity.DocumentStatus;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentResponseDto {
    private UUID id;
    private String filename;
    private long fileSize;
    private DocumentStatus documentStatus;
    private Integer chunksCreated;
    private String message;
}
