package com.example.docmind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.UUID;

// Snapshot of a retrieved chunk that was given to the model as a numbered source.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Citation {
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String snippet;

    @Column(name = "similarity_score")
    private Double similarityScore;
}
