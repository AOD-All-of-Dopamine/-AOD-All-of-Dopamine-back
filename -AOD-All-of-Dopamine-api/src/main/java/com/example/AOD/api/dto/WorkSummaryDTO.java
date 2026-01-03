package com.example.AOD.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkSummaryDTO {
    private Long id;
    private String domain;
    private String title;
    private String thumbnail;
    private Double score;
    private Integer rank; // for ranking pages
    private String rankChange; // "up", "down", "new", or number
    private String releaseDate; // for new releases
}


