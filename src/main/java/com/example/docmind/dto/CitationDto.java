package com.example.docmind.dto;

import com.example.docmind.entity.Citation;
import lombok.*;

import java.util.UUID;

// A retrieved chunk. "position" is the [n] id the model was given and may cite in its answer.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitationDto {
    private Integer position;
    private UUID documentId;
    private String fileName;
    private Integer chunkIndex;
    private Integer pageNumber;
    private String snippet;
    private Double similarityScore;

    public static CitationDto from(Citation c, int position) {
        return CitationDto.builder()
                .position(position)
                .documentId(c.getDocumentId())
                .fileName(c.getFileName())
                .chunkIndex(c.getChunkIndex())
                .pageNumber(c.getPageNumber())
                .snippet(c.getSnippet())
                .similarityScore(c.getSimilarityScore())
                .build();
    }

    public Citation toEntity() {
        return Citation.builder()
                .documentId(documentId)
                .fileName(fileName)
                .chunkIndex(chunkIndex)
                .pageNumber(pageNumber)
                .snippet(snippet)
                .similarityScore(similarityScore)
                .build();
    }
}
