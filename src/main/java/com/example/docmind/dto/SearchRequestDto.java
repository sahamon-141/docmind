package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchRequestDto {
    @NotBlank(message = "Query cannot be empty")
    @Size(max = 2000, message = "Query must be at most 2000 characters")
    private String query;

    // optional: restrict the search to one of the caller's documents
    @JsonAlias("id")
    private UUID documentId;

    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 20, message = "topK must be at most 20")
    private Integer topK;

    @DecimalMin(value = "0.0", message = "minSimilarity must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "minSimilarity must be between 0 and 1")
    @JsonAlias("similaritySearch")
    private Double minSimilarity;
}
