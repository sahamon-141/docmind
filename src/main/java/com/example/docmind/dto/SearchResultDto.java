package com.example.docmind.dto;

import java.util.List;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchResultDto {
    private String query;
    private int totalMatches;
    private List<CitationDto> matches;
}
